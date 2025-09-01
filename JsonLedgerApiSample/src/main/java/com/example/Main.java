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

import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.*;

import java.security.KeyPair;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        setupEnvironment(args);

        Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
        Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);

        try {
            // confirm environment and inputs
            confirmConnectivity(ledgerApi, validatorApi);
            confirmAuthentication(ledgerApi, validatorApi);

            // onboard the sender
            KeyPair senderKeyPair = Keys.generate();
            Keys.printKeyPair(Env.SENDER_PARTY_HINT, senderKeyPair);
            String senderParty = onboardNewUser(Env.SENDER_PARTY_HINT, validatorApi, senderKeyPair);

            // preapprove Canton Coin transfers
            preapproveTransfers(validatorApi, senderParty, senderKeyPair);



            System.exit(0);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(1);
        }
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
}
