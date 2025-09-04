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

package com.example;

import com.daml.ledger.javaapi.data.codegen.DamlRecord;
import com.daml.ledger.javaapi.data.codegen.json.JsonLfDecoder;
import com.example.GsonTypeAdapters.GsonSingleton;
import com.example.client.ledger.model.DisclosedContract;
import com.example.client.ledger.model.JsActiveContract;
import com.example.client.ledger.model.JsContractEntry;
import com.example.client.ledger.model.JsInterfaceView;
import com.example.client.tokenMetadata.model.GetRegistryInfoResponse;
import com.example.client.transferInstruction.model.TransferFactoryWithChoiceContext;
import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.*;
import splice.api.token.holdingv1.Holding;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.metadatav1.ChoiceContext;
import splice.api.token.metadatav1.ExtraArgs;
import splice.api.token.metadatav1.Metadata;
import splice.api.token.transferinstructionv1.Transfer;
import splice.api.token.transferinstructionv1.TransferFactory_Transfer;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        setupEnvironment();

        Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
        Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);
        ScanProxy scanProxyApi = new ScanProxy(Env.SCAN_PROXY_API_URL, Env.VALIDATOR_TOKEN);
        TransferInstruction transferInstructionApi = new TransferInstruction(Env.SCAN_API_URL);
        TokenMetadata tokenMetadataApi = new TokenMetadata(Env.SCAN_API_URL);

        try {
            // confirm environment and inputs
            confirmConnectivity(ledgerApi, validatorApi, scanProxyApi, tokenMetadataApi);
            confirmAuthentication(ledgerApi, validatorApi);

            // onboard the treasury, if necessary
            if (Env.TREASURY_PARTY.isEmpty()) {
                KeyPair treasuryKeyPair = Keys.generate();
                Keys.printKeyPair(Env.TREASURY_PARTY_HINT, treasuryKeyPair);
                Env.TREASURY_PARTY = onboardNewUser(Env.TREASURY_PARTY_HINT, validatorApi, treasuryKeyPair);

                // preapprove Canton Coin transfers
                preapproveTransfers(validatorApi, Env.TREASURY_PARTY, treasuryKeyPair);
            }

            // onboard the sender, if necessary
            if (Env.SENDER_PARTY.isEmpty()) {
                KeyPair senderKeyPair = Keys.generate();
                Keys.printKeyPair(Env.SENDER_PARTY_HINT, senderKeyPair);
                Env.SENDER_PARTY = onboardNewUser(Env.SENDER_PARTY_HINT, validatorApi, senderKeyPair);

                // preapprove Canton Coin transfers
                preapproveTransfers(validatorApi, Env.SENDER_PARTY, senderKeyPair);
            }

            InstrumentId cantonCoinInstrumentId = new InstrumentId(Env.DSO_PARTY, "Amulet");

            // select the holdings to use for a transfer from the Validator
            BigDecimal transferAmount = new BigDecimal(Env.TRANSFER_AMOUNT);

            transferAsset(
                    transferInstructionApi,
                    ledgerApi,
                    Env.DSO_PARTY,
                    Env.VALIDATOR_PARTY,
                    Env.SENDER_PARTY,
                    transferAmount,
                    cantonCoinInstrumentId);

            System.exit(0);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static <T> T convertRecordViaJson(
            Object recordPayload,
            JsonDecoder<T> valueParser
    ) {
        String raw = GsonSingleton.getInstance().toJson(recordPayload);
        return useValueParser(raw, valueParser);
    }



    private static <T> T useValueParser(
            String raw,
            JsonDecoder<T> valueParser
    ) {
        try {
            return valueParser.decode(raw);
        } catch (IOException ex) {
            System.out.println("Cannot decode interface view.");
            System.exit(1);
            return null;
        }
    }

    private static <T extends DamlRecord<T>> ContractAndId<T> fromInterface(
            JsContractEntry contractEntry,
            TemplateId interfaceId,
            JsonDecoder<T> interfaceValueParser
    ) {
        JsActiveContract activeContract = contractEntry.getJsContractEntryOneOf().getJsActiveContract();
        String instanceContractId = activeContract.getCreatedEvent().getContractId();

        List<JsInterfaceView> interfaceViews = activeContract.getCreatedEvent().getInterfaceViews();
        if (interfaceViews == null) return null;

        T record = interfaceViews
                .stream()
                .filter(v -> interfaceId.matchesModuleAndTypeName(v.getInterfaceId()))
                .map(v -> convertRecordViaJson(v.getViewValue(), interfaceValueParser))
                .findFirst()
                .orElseThrow();
        return new ContractAndId<>(instanceContractId, record);
    }

    private static List<ContractAndId<HoldingView>> selectHoldingsForTransfer(Ledger ledgerApi, String party, BigDecimal transferAmount, InstrumentId instrumentId) throws Exception {

        printStep("Selecting holdings");
        System.out.println("Selecting holdings for a " + transferAmount + " unit transfer from " + party);

        final BigDecimal[] remaining = {transferAmount};
        List<ContractAndId<HoldingView>> holdingsForTransfer =
                ledgerApi.getActiveContractsForInterface(party, TemplateId.HOLDING_INTERFACE_ID.getRaw()).stream()
                        .map(r -> fromInterface(r.getContractEntry(), TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson))
                        .filter(v -> v != null && v.record().instrumentId.equals(instrumentId))
                        .sorted(Comparator.comparing(c -> c.record().amount))
                        .takeWhile(c -> {
                            remaining[0] = remaining[0].subtract(c.record().amount);
                            return remaining[0].compareTo(BigDecimal.ZERO) < 0;
                        })
                        .toList();
        if (remaining[0].compareTo(BigDecimal.ZERO) > 0) {
            System.out.println("Insufficient holdings to transfer " + transferAmount + " units");
            System.exit(1);
        }
        else
        {
            for (ContractAndId<HoldingView> holding : holdingsForTransfer) {
                System.out.println("Holding views: " + holding.record().toJson());
            }
        }
        return holdingsForTransfer;
    }

    private static void printStep(String step) {
        System.out.println("\n=== " + step + " ===");
    }

    private static void printToken(String name, String token) {
        int length = token.length();
        System.out.println(name + ": " +
                (token.trim().isEmpty() ? "<empty>" : token.substring(0, 10) + "..." + token.substring(length - 11, length - 1)));
    }

    private static void setupEnvironment() {
        printStep("Print environment variables");
        System.out.println("LEDGER_API_URL: " + Env.LEDGER_API_URL);
        System.out.println("VALIDATOR_API_URL: " + Env.VALIDATOR_API_URL);
        System.out.println("SCAN_PROXY_API_URL: " + Env.SCAN_PROXY_API_URL);
        System.out.println();
        printToken("VALIDATOR_TOKEN", Env.VALIDATOR_TOKEN);
        System.out.println();
        System.out.println("TREASURY_PARTY_HINT: " + Env.TREASURY_PARTY_HINT);
        System.out.println("TREASURY_PARTY: " + Env.TREASURY_PARTY);
        printToken("TREASURY_TOKEN", Env.TREASURY_TOKEN);
        System.out.println();
        System.out.println("SENDER_PARTY_HINT: " + Env.SENDER_PARTY_HINT);
        System.out.println("SENDER_PARTY: " + Env.SENDER_PARTY);
        printToken("SENDER_TOKEN", Env.SENDER_TOKEN);

        if (Env.SCAN_API_URL == null || Env.SCAN_API_URL.isEmpty()) {
            System.err.println("Error: SCAN_API_URL environment variable must be set");
            System.exit(1);
        }
    }

    private static void confirmConnectivity(Ledger ledgerApi, Validator validatorApi, ScanProxy scanProxyApi, TokenMetadata tokenMetadataApi) throws Exception {
        printStep("Confirm API connectivity");

        System.out.println("Version: " + ledgerApi.getVersion());

        Env.VALIDATOR_PARTY = validatorApi.getValidatorParty();
        System.out.println("Validator Party: " + validatorApi.getValidatorParty());

        Env.DSO_PARTY = scanProxyApi.getDsoPartyId();
        System.out.println("DSO Party: " + scanProxyApi.getDsoPartyId());

        GetRegistryInfoResponse registryInfo = tokenMetadataApi.getRegistryInfo();
        System.out.println("Registry Party: " + registryInfo.getAdminId());
    }

    private static void confirmAuthentication(Ledger ledgerApi, Validator validatorApi) throws Exception {
        printStep("Confirm authentication");
        // these require a valid Validator token
        System.out.println("Ledger end: " + ledgerApi.getLedgerEnd());
        System.out.println("Validator users: " + validatorApi.listUsers());
    }

    private static String onboardNewUser(String partyHint, Validator validatorApi, KeyPair keyPair) throws Exception {

        printStep("Onboard " + partyHint);

        List<TopologyTx> txs = validatorApi.prepareOnboarding(partyHint, keyPair.getPublic());
        List<SignedTopologyTx> signedTxs = ExternalSigning.signOnboarding(txs, keyPair.getPrivate());
        String newParty = validatorApi.submitOnboarding(signedTxs, keyPair.getPublic());
        System.out.println("New party: " + newParty);

        return newParty;
    }

    // Note: the endpoints used here consume limited resources
    // (i.e., the 200 parties-per-node limitation).
    // TODO: Switch to "bare creating a TransferPreapprovalRequest"
    private static void preapproveTransfers(Validator validatorApi, String externalPartyId, KeyPair externalPartyKeyPair) throws ApiException {
        CreateExternalPartySetupProposalResponse proposalContract = validatorApi.createExternalPartySetupProposal(externalPartyId);
        PrepareAcceptExternalPartySetupProposalResponse preparedAccept = validatorApi.prepareAcceptExternalPartySetupProposal(externalPartyId, proposalContract.getContractId());
        ExternalPartySubmission acceptSubmission = ExternalSigning.signSubmission(externalPartyId, preparedAccept.getTransaction(), preparedAccept.getTxHash(), externalPartyKeyPair);
        validatorApi.submitAcceptExternalPartySetupProposal(acceptSubmission);
    }

    private static void transferAsset(
            TransferInstruction transferInstructionApi,
            Ledger ledgerApi,
            String adminParty,
            String sender,
            String receiver,
            BigDecimal amount,
            InstrumentId instrumentId) throws Exception{

        List<ContractAndId<HoldingView>> holdings = selectHoldingsForTransfer(ledgerApi, Env.VALIDATOR_PARTY, amount, instrumentId);

        Instant requestDate = Instant.now();
        Instant requestExpiresDate = requestDate.plusSeconds(24 * 60 * 60);

        TransferFactory_Transfer proposedTransfer = makeProposedTransfer(adminParty, sender, receiver, amount, instrumentId, requestDate, requestExpiresDate, holdings);
        TransferFactoryWithChoiceContext transferFactoryWithChoiceContext = transferInstructionApi.getTransferFactory(proposedTransfer);
        TransferFactory_Transfer sentTransfer = adoptChoiceContext(proposedTransfer, transferFactoryWithChoiceContext);
        List<DisclosedContract> disclosures = transferFactoryWithChoiceContext
                .getChoiceContext()
                .getDisclosedContracts()
                .stream()
                .map((d) -> convertRecordViaJson(d, DisclosedContract::fromJson))
                .toList();

        ledgerApi.exercise(
                sender,
                TemplateId.TRANSFER_FACTORY_INTERFACE_ID,
                transferFactoryWithChoiceContext.getFactoryId(),
                "TransferFactory_Transfer",
                sentTransfer,
                disclosures);
    }

    private static TransferFactory_Transfer makeProposedTransfer(
            String admin,
            String sender,
            String receiver,
            BigDecimal amount,
            InstrumentId instrumentId,
            Instant requestedAt,
            Instant executeBefore,
            List<ContractAndId<HoldingView>> holdings) {

        List<Holding.ContractId> holdingCids = holdings
                .stream()
                .map((h) -> new Holding.ContractId(h.contractId()))
                .toList();

        Metadata emptyMetadata = new Metadata(new HashMap<>());
        ChoiceContext noContext = new ChoiceContext(new HashMap<>());
        ExtraArgs blankExtraArgs = new ExtraArgs(noContext, emptyMetadata);

        Transfer transfer = new Transfer(sender, receiver, amount, instrumentId, requestedAt, executeBefore, holdingCids, emptyMetadata);
        return new TransferFactory_Transfer(admin, transfer, blankExtraArgs);
    }

    private static TransferFactory_Transfer adoptChoiceContext(
            TransferFactory_Transfer proposed,
            TransferFactoryWithChoiceContext fromApi) throws JsonLfDecoder.Error {

        Metadata emptyMetadata = new Metadata(new HashMap<>());

        // ChoiceContext from the transfer OpenAPI != ChoiceContext generated from the transfer DAR
        String choiceJson = GsonSingleton.getInstance().toJson(fromApi.getChoiceContext().getChoiceContextData());
        System.out.println("Intermediate choice context JSON: " + choiceJson);
        ChoiceContext choiceContextFromApi = useValueParser(choiceJson, ChoiceContext::fromJson);

        ExtraArgs populatedExtraArgs = new ExtraArgs(choiceContextFromApi, emptyMetadata);
        return new TransferFactory_Transfer(proposed.expectedAdmin, proposed.transfer, populatedExtraArgs);
    }
}
