/*
 * Copyright (c) 2025, by Digital Asset
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

package com.example.services;

import com.example.ConversionHelpers;
import com.example.access.ExternalParty;
import com.example.access.LedgerUser;
import com.example.client.ledger.invoker.ApiException;
import com.example.client.ledger.model.*;
import com.example.client.scanProxy.model.ContractWithState;
import com.example.client.tokenMetadata.model.GetRegistryInfoResponse;
import com.example.client.transferInstruction.model.TransferFactoryWithChoiceContext;
import com.example.models.ContractAndId;
import com.example.models.Splice;
import com.example.models.TemplateId;
import com.example.models.TokenStandard;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.transferinstructionv1.TransferFactory_Transfer;

import java.math.BigDecimal;
import java.net.URISyntaxException;
import java.security.KeyPair;
import java.time.Instant;
import java.util.*;

public class Wallet {
    public Scan scanApi;
    public TransferInstruction transferInstructionApi;
    public TokenMetadata tokenMetadataApi;
    public Validator validatorApi;

    // authorized APIs
    public LedgerUser managingUser;
    public Ledger ledgerApi;
    public ScanProxy scanProxyApi;

    public Wallet(
            LedgerUser managingUser,
            String scanApiUrl,
            String tokenStandardUrl,
            String ledgerApiUrl,
            String validatorApiUrl,
            String scanProxyApiUrl
    ) throws URISyntaxException {

        // public APIs
        this.scanApi = new Scan(scanApiUrl);
        this.transferInstructionApi = new TransferInstruction(tokenStandardUrl);
        this.tokenMetadataApi = new TokenMetadata(tokenStandardUrl);

        // authorized APIs
        this.managingUser = managingUser;
        this.ledgerApi = new Ledger(ledgerApiUrl, managingUser);
        this.validatorApi = new Validator(validatorApiUrl, managingUser);
        this.scanProxyApi = new ScanProxy(scanProxyApiUrl, managingUser);
    }

    public void confirmConnectivity() throws Exception {
        System.out.println("Version: " + this.ledgerApi.getVersion());
        System.out.println("Synchronizer id: " + this.scanApi.getSynchronizerId());
        GetRegistryInfoResponse registryInfo = this.tokenMetadataApi.getRegistryInfo();
        System.out.println("Registry Party: " + registryInfo.getAdminId());
    }

    public void confirmAuthentication() throws Exception {
        // these require a valid Validator token
        try {
            System.out.println("DSO Party: " + this.scanProxyApi.getDsoPartyId());
            System.out.println("Ledger end: " + this.ledgerApi.getLedgerEnd());
        } catch (com.example.client.scanProxy.invoker.ApiException ex) {
            System.err.println(ex.getCode() + " response when accessing the Scan Proxy API. Check the validator token.");
            throw ex;
        } catch (com.example.client.ledger.invoker.ApiException ex) {
            System.err.println(ex.getCode() + " response when accessing the Ledger API. Check the validator token.");
            throw ex;
        }
    }

    public String getDsoPartyId() throws Exception {
        return this.scanProxyApi.getDsoPartyId();
    }

    public Optional<PartyDetails> getPartyDetails(String partyId) throws Exception {
        return this.ledgerApi.getPartyDetails(partyId);
    }

    // This API endpoint is specific to Canton Coin; the method's output is irrelevant when considering other token standard implementations.
    public boolean hasCantonCoinTransferPreapproval(String partyId) throws Exception {
        return this.scanProxyApi.getTransferPreapproval(partyId).isPresent();
    }

    public ExternalParty allocateExternalPartyNew(String synchronizerId, String partyHint, KeyPair externalPartyKeyPair) throws Exception {

        GenerateExternalPartyTopologyResponse generateStepResponse = this.ledgerApi.generateExternalPartyTopology(synchronizerId, partyHint, externalPartyKeyPair.getPublic());

        String partyId = generateStepResponse.getPartyId();
        List<String> transactionsToSign = generateStepResponse.getTopologyTransactions();
        if (transactionsToSign == null) {
            transactionsToSign = new ArrayList<>();
        }

        this.ledgerApi.allocateExternalParty(externalPartyKeyPair, synchronizerId, transactionsToSign, generateStepResponse.getMultiHash());

        this.ledgerApi.grantUserRights(this.managingUser.userId(), List.of(
                Ledger.makeCanReadAsRight(partyId),
                Ledger.makeCanExecuteAsRight(partyId)
        ));

        return new ExternalParty(partyId, externalPartyKeyPair);
    }

    public void issueTransferPreapprovalProposal(String synchronizerId, String commandId, String dso, String exchangePartyId, ExternalParty externalParty) throws Exception {
        List<DisclosedContract> noDisclosures = new ArrayList<>();
        var transferPreapprovalProposal = Splice.makeTransferPreapprovalProposal(externalParty.partyId(), exchangePartyId, dso);
        List<Command> createTransferPreapprovalCommands = Ledger.makeCreateCommand(TemplateId.TRANSFER_PREAPPROVAL_PROPOSAL_ID, transferPreapprovalProposal);
        this.prepareAndSign(externalParty, synchronizerId, commandId, createTransferPreapprovalCommands, noDisclosures);
    }

    public boolean hasEstablishedTransferPreapproval(String partyId) throws Exception {
        CumulativeFilter transferPreapprovalFilter = Ledger.createFilterByTemplate(TemplateId.TRANSFER_PREAPPROVAL_ID);
        List<JsGetActiveContractsResponse> activeContracts = this.ledgerApi.getActiveContractsByFilter(partyId, List.of(transferPreapprovalFilter));
        // TODO: filter by provider party and expiry time
        return !activeContracts.isEmpty();
    }

    public BigDecimal getTotalHoldings(String partyId, InstrumentId instrumentId) throws Exception {
        return queryForHoldings(partyId, instrumentId).stream()
                .map(h -> h.record().amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    public List<ContractAndId<HoldingView>> queryForHoldings(String partyId, InstrumentId instrumentId) throws Exception {
        CumulativeFilter holdingInterfaceFilter = Ledger.createFilterByInterface(TemplateId.HOLDING_INTERFACE_ID);
        return this.ledgerApi.getActiveContractsByFilter(partyId, List.of(holdingInterfaceFilter)).stream()
                .map(r -> ConversionHelpers.fromInterface(r.getContractEntry(), TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson))
                .filter(v -> v != null && v.record().instrumentId.equals(instrumentId))
                .toList();
    }

    public List<ContractAndId<HoldingView>> selectHoldingsForTransfer(String partyId, InstrumentId instrumentId, BigDecimal transferAmount) throws Exception {

        final BigDecimal[] remainingReference = {transferAmount};

        return queryForHoldings(partyId, instrumentId).stream()
                .filter(h -> h.record().lock.isEmpty())
                .sorted(Comparator.comparing(c -> c.record().amount))
                .takeWhile(c -> {
                    BigDecimal previousTotal = remainingReference[0];
                    remainingReference[0] = previousTotal.subtract(c.record().amount);
                    return previousTotal.compareTo(BigDecimal.ZERO) > 0;
                })
                .toList();
    }

    /**
     * Perform a token standards-compliant transfer.
     *
     * @param synchronizerId
     * @param commandId
     * @param senderPartyId
     * @param senderKeyPair
     * @param receiverPartyId
     * @param instrumentId
     * @param memoTag
     * @param otherTransferMetadata
     * @param amount
     * @param holdings
     * @param preventMultiStep
     * @return whether or not the transfer was submitted.
     *
     *         A transfer will not be submitted if preventMultiStep is set to 'true' and the token standards factory
     *         responds with a transfer kind of "offer" (i.e. multi-step transfer).
     * @throws Exception
     */
    public boolean transferHoldings(
            String synchronizerId,
            String commandId,
            String senderPartyId,
            Optional<KeyPair> senderKeyPair,
            String receiverPartyId,
            InstrumentId instrumentId,
            Optional<String> memoTag,
            Map<String, String> otherTransferMetadata,
            BigDecimal amount,
            List<ContractAndId<HoldingView>> holdings,
            boolean preventMultiStep
    ) throws Exception {
        Instant requestDate = Instant.now();
        Instant requestExpiresDate = requestDate.plusSeconds(24 * 60 * 60);

        TransferFactory_Transfer proposedTransfer = TokenStandard.makeProposedTransfer(senderPartyId, receiverPartyId, amount, instrumentId, memoTag, otherTransferMetadata, requestDate, requestExpiresDate, holdings);
        TransferFactoryWithChoiceContext transferFactoryWithChoiceContext = this.transferInstructionApi.getTransferFactory(proposedTransfer);

        TransferFactoryWithChoiceContext.TransferKindEnum kind = transferFactoryWithChoiceContext.getTransferKind();

        if (preventMultiStep
                && kind.equals(TransferFactoryWithChoiceContext.TransferKindEnum.OFFER) ) {
            return false;
        }

        TransferFactory_Transfer sentTransfer = TokenStandard.resolveProposedTransfer(proposedTransfer, transferFactoryWithChoiceContext);

        List<DisclosedContract> disclosures = transferFactoryWithChoiceContext
                .getChoiceContext()
                .getDisclosedContracts()
                .stream()
                .map((d) -> ConversionHelpers.convertRecordViaJson(d, DisclosedContract::fromJson))
                .toList();

        List<Command> transferCommands = Ledger.makeExerciseCommand(
                TemplateId.TRANSFER_FACTORY_INTERFACE_ID,
                "TransferFactory_Transfer",
                transferFactoryWithChoiceContext.getFactoryId(),
                sentTransfer
        );

        if (senderKeyPair.isEmpty()) {
            this.ledgerApi.submitAndWaitForCommands(
                    senderPartyId,
                    commandId,
                    transferCommands,
                    disclosures);

        } else {
            prepareAndSign(new ExternalParty(senderPartyId, senderKeyPair.get()), synchronizerId, commandId, transferCommands, disclosures);
        }

        return true;
    }

    public void prepareAndSign(ExternalParty externalParty, String synchronizerId, String commandId, List<Command> commands, List<DisclosedContract> disclosures) throws Exception {
        JsPrepareSubmissionResponse preparedTransaction = this.ledgerApi.prepareSubmissionForSigning(
                synchronizerId,
                externalParty.partyId(),
                commandId,
                commands,
                disclosures);

        SinglePartySignatures signature = Ledger.makeSingleSignature(preparedTransaction, externalParty.partyId(), externalParty.keyPair());
        this.ledgerApi.executeSignedSubmission(preparedTransaction, List.of(signature));
    }

    public List<CompletionStreamResponse> checkForCommandCompletion(List<String> parties, Long beginExclusive) throws Exception {
        return this.ledgerApi.getCompletions(parties, beginExclusive);
    }

    public Long getLedgerEnd() throws Exception {
        return this.ledgerApi.getLedgerEnd();
    }
}
