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

import com.daml.ledger.api.v2.EventOuterClass;
import com.daml.ledger.javaapi.data.GetActiveContractsResponse;
import com.daml.ledger.javaapi.data.Value;
import com.daml.ledger.javaapi.data.codegen.DamlRecord;
import com.daml.ledger.javaapi.data.codegen.json.JsonLfDecoder;
import com.daml.ledger.javaapi.data.codegen.json.JsonLfReader;
import com.example.client.ledger.model.*;
import com.example.client.ledger.model.AbstractOpenApiSchema;
import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.*;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.TreeNode;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.util.JSONWrappedObject;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.*;
import java.util.function.Function;

public class Main {
    private final static String HOLDING_INTERFACE_ID = "#splice-api-token-holding-v1:Splice.Api.Token.HoldingV1:Holding";

    public static void main(String[] args) {

        setupEnvironment(args);

        Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
        Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);
        Scan scanApi = new Scan(Env.SCAN_API_URL, Env.VALIDATOR_TOKEN);

        try {
            // confirm environment and inputs
            confirmConnectivity(ledgerApi, validatorApi);
            confirmAuthentication(ledgerApi, validatorApi);

            String validatorParty = validatorApi.getValidatorParty();
            String dsoParty = scanApi.getDsoPartyId();
            System.out.println("Validator Party: " + validatorParty);
            System.out.println("DSO Party: " + dsoParty);

            // onboard the treasury, if necessary
            if (Env.SENDER_PARTY.isEmpty()){
                KeyPair senderKeyPair = Keys.generate();
                Keys.printKeyPair(Env.SENDER_PARTY_HINT, senderKeyPair);
                Env.SENDER_PARTY = onboardNewUser(Env.SENDER_PARTY_HINT, validatorApi, senderKeyPair);

                // preapprove Canton Coin transfers
                preapproveTransfers(validatorApi, Env.SENDER_PARTY, senderKeyPair);
            }

            // TODO: convert the result to a HoldingView (in this case)
            List<JsGetActiveContractsResponse> result = ledgerApi.getActiveContractsForInterface(validatorParty, TemplateId.HOLDING_INTERFACE_ID.getRaw());

            BigDecimal transferAmount = new BigDecimal(500);

            List<ContractAndId<HoldingView>> holdingViews = fromInterfaces(result, HoldingView::fromJson);
            List<ContractAndId<HoldingView>> holdingsToTransfer = holdingsToTransfer(transferAmount, holdingViews);

            if (holdingsToTransfer == null) {
                System.out.println("Insufficient holdings to transfer " + transferAmount + " units");
                System.exit(0);
            }

            for (ContractAndId<HoldingView> holding : holdingsToTransfer) {
                System.out.println("Holding views: " + holding.record().toJson());
            }

            System.exit(0);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(1);
        }
    }

    private static <T extends DamlRecord<T>> List<ContractAndId<T>> fromInterfaces(
            List<JsGetActiveContractsResponse> searchResults,
            DamlDecoder<T> interfaceValueParser
    ) throws JsonProcessingException, JsonLfDecoder.Error {

        List<ContractAndId<T>> relevantHoldings = new ArrayList<>();
        for (JsGetActiveContractsResponse responseItem: searchResults) {
            System.out.println("Ledger API lookup result: " + responseItem.toJson());
            JsContractEntry contractEntry = responseItem.getContractEntry();

            // TODO: nice inheritance check
            JsActiveContract activeContract = contractEntry.getJsContractEntryOneOf().getJsActiveContract();

            if (activeContract != null) {
                List<JsInterfaceView> interfaceViews = activeContract.getCreatedEvent().getInterfaceViews();
                if (interfaceViews != null) {
                    for (JsInterfaceView interfaceView : interfaceViews) {
                        if (TemplateId.HOLDING_INTERFACE_ID.matchesModuleAndTypeName(interfaceView.getInterfaceId())) {

                            String holdingJson = new ObjectMapper().writeValueAsString(interfaceView.getViewValue());

                            String holdingContractId = activeContract.getCreatedEvent().getContractId();
                            T holdingView = interfaceValueParser.decode(holdingJson);

                            relevantHoldings.add(new ContractAndId<>(holdingContractId, holdingView));
                        }
                    }

                }
                System.out.println("contractEntry: " + activeContract);
            }
        }
        return relevantHoldings;
    }

    private static List<ContractAndId<HoldingView>> holdingsToTransfer(BigDecimal transferAmount, List<ContractAndId<HoldingView>> holdingViews) throws JsonProcessingException, JsonLfDecoder.Error {
        List<ContractAndId<HoldingView>> toTransfer = new ArrayList<>();
        Iterator<ContractAndId<HoldingView>> iterator = holdingViews.iterator();
        while (transferAmount.compareTo(BigDecimal.ZERO) > 0 && iterator.hasNext()) {
            ContractAndId<HoldingView> next = iterator.next();
            toTransfer.add(next);
            transferAmount = transferAmount.subtract(next.record().amount);
        }

        if (transferAmount.compareTo(BigDecimal.ZERO) > 0) return null; // there weren't enough holdings to satisfy the transfer amount

        return toTransfer;
    }

    private static void printStep(String step) {
        System.out.println("\n=== " + step + " ===");
    }

    private static void printToken(String name, String token) {
        int length = token.length();
        System.out.println(name + ": " +
            (token.isEmpty() ? "<empty>" : token.substring(0, 10) + "..." + token.substring(length - 11, length - 1)));
    }

    private static void setupEnvironment(String[] args) {
        printStep("Print environment variables");
        System.out.println("LEDGER_API_URL: " + Env.LEDGER_API_URL);
        System.out.println("VALIDATOR_API_URL: " + Env.VALIDATOR_API_URL);
        printToken("VALIDATOR_TOKEN", Env.VALIDATOR_TOKEN);
        printToken("SENDER_TOKEN", Env.SENDER_TOKEN);
        printToken("RECEIVER_TOKEN", Env.RECEIVER_TOKEN);
        System.out.println("SENDER_PARTY_HINT: " + Env.SENDER_PARTY_HINT);
        System.out.println("RECEIVER_PARTY_HINT: " + Env.RECEIVER_PARTY_HINT);
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
