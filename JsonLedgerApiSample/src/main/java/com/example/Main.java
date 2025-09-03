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
import com.example.client.ledger.model.JsActiveContract;
import com.example.client.ledger.model.JsContractEntry;
import com.example.client.transferInstruction.model.TransferFactoryWithChoiceContext;
import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.*;
import com.google.gson.Gson;
import splice.api.token.holdingv1.Holding;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;

public class Main {

    public static void main(String[] args) {

        setupEnvironment(args);

        Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
        Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);
        Scan scanApi = new Scan(Env.SCAN_PROXY_API_URL, Env.VALIDATOR_TOKEN);
        TransferInstruction transferInstructionApi = new TransferInstruction(Env.TOKEN_ADMIN_URL);

        try {
            // confirm environment and inputs
            confirmConnectivity(ledgerApi, validatorApi);
            confirmAuthentication(ledgerApi, validatorApi);

            // get network party ids
            Env.VALIDATOR_PARTY = validatorApi.getValidatorParty();
            Env.DSO_PARTY = scanApi.getDsoPartyId();
            System.out.println("Validator Party: " + Env.VALIDATOR_PARTY);
            System.out.println("DSO Party: " + Env.DSO_PARTY);

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
            BigDecimal transferAmount = new BigDecimal(500);
            Instant requestDate = Instant.now();
            Instant requestExpiresDate = requestDate.plusSeconds(24 * 60 * 60);

            List<ContractAndId<HoldingView>> holdingsForTransfer = selectHoldingsForTransfer(
                    ledgerApi, Env.VALIDATOR_PARTY,
                    transferAmount, cantonCoinInstrumentId);

            List<Holding.ContractId> contractIdsForTransfer = holdingsForTransfer
                    .stream()
                    .map((h) -> new Holding.ContractId(h.contractId()))
                    .toList();

            TransferFactoryWithChoiceContext transferFactoryPayload = transferInstructionApi.getTransferFactory(
                    Env.DSO_PARTY,
                    Env.VALIDATOR_PARTY,
                    Env.SENDER_PARTY,
                    transferAmount,
                    cantonCoinInstrumentId,
                    requestDate,
                    requestExpiresDate,
                    contractIdsForTransfer);

            System.exit(0);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(1);
        }
    }

    private static <T extends DamlRecord<T>> ContractAndId<T> fromInterface(
            JsContractEntry contractEntry,
            TemplateId interfaceId,
            DamlDecoder<T> interfaceValueParser
    ) {
        Gson gson = new Gson();
        JsActiveContract activeContract = contractEntry.getJsContractEntryOneOf().getJsActiveContract();
        String holdingContractId = activeContract.getCreatedEvent().getContractId();
        T record = activeContract.getCreatedEvent().getInterfaceViews().stream()
                .filter(v -> interfaceId.matchesModuleAndTypeName(v.getInterfaceId()))
                .map(v -> gson.toJson(v.getViewValue()))
                .map(json -> {
                    try {
                        return interfaceValueParser.decode(json);
                    } catch (JsonLfDecoder.Error ex) {
                        System.out.println("Cannot decode interface view.");
                        System.exit(1);
                        return null;
                    }
                })
                .findFirst()
                .orElseThrow();
        return new ContractAndId<>(holdingContractId, record);
    }

    private static List<ContractAndId<HoldingView>> selectHoldingsForTransfer(Ledger ledgerApi, String party, BigDecimal transferAmount, InstrumentId instrumentId) throws Exception {

        printStep("Selecting holdings");
        System.out.println("Selecting holdings for a " + transferAmount + " unit transfer from " + party);

        final BigDecimal[] remaining = {transferAmount};
        List<ContractAndId<HoldingView>> holdingsForTransfer =
                ledgerApi.getActiveContractsForInterface(party, TemplateId.HOLDING_INTERFACE_ID.getRaw()).stream()
                        .map(r -> fromInterface(r.getContractEntry(), TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson))
                        .filter(v -> v.record().instrumentId.equals(instrumentId))
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

    private static void setupEnvironment(String[] args) {
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
    }

    private static void confirmConnectivity(Ledger ledgerApi, Validator validatorApi) throws Exception {
        printStep("Confirm API connectivity");
        System.out.println("Version: " + ledgerApi.getVersion());
        System.out.println("Party: " + validatorApi.getValidatorParty());
    }

    private static void confirmAuthentication(Ledger ledgerApi, Validator validatorApi) throws Exception {
        printStep("Confirm authentication");
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
        SubmitAcceptExternalPartySetupProposalResponse acceptResponse = validatorApi.submitAcceptExternalPartySetupProposal(acceptSubmission);
    }

    private static void transferAsset(String sender, String receiver, InstrumentId instrumentId, double amount) {


    }

    private static void getHoldings(Ledger ledger) throws Exception {
        long offset = ledger.getLedgerEnd();

    }
}
