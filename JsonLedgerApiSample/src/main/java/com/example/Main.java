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

import com.example.client.validator.model.SignedTopologyTx;
import com.example.client.validator.model.SubmitAcceptExternalPartySetupProposalResponse;
import com.example.client.validator.model.TopologyTx;

import java.security.KeyPair;
import java.util.List;

public class Main {
    public static void main(String[] args) {

        setupEnvironment(args);
        Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
        Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);
        Wallet walletApi = new Wallet(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);

        try {
            // confirm environment and inputs
            confirmConnectivity(ledgerApi, validatorApi);
            confirmAuthentication(ledgerApi, validatorApi);

            // onboard the sender
            KeyPair senderKeyPair = Keys.generate();
            Keys.printKeyPair(Env.SENDER_PARTY_HINT, senderKeyPair);
            String senderParty = onboardNewUser(Env.SENDER_PARTY_HINT, validatorApi, senderKeyPair);

            // grant the sender some coin
            double tapAmount = 500.0;
            long nonce = 42; // arbitrary; a real-world application should generate and retain distinct nonces for each business transaction
            walletApi.tap(tapAmount);

            // onboard receiver
            KeyPair receiverKeyPair = Keys.generate();
            Keys.printKeyPair(Env.RECEIVER_PARTY_HINT, receiverKeyPair);
            String receiverParty = onboardNewUser(Env.RECEIVER_PARTY_HINT, validatorApi, senderKeyPair);

            double transferAmount = 30.0;
            SubmitAcceptExternalPartySetupProposalResponse transactionPreapproval = validatorApi.preapproveTransactions(receiverKeyPair, senderParty, receiverParty);
            validatorApi.sendWithPreApproval(senderKeyPair, senderParty, receiverParty, transferAmount, nonce);

            /*
            String transferPreapprovalProposalContractId = createTransferPreapproval(senderParty, receiverParty);
            String transferPreapprovalContractId = acceptTransferPreapproval(receiverParty, transferPreapprovalProposalContractId);

            String holdingContractId = tap(senderParty, tapAmount);
            transfer(senderParty, receiverParty, holdingContractId, transferAmount);
            */

            System.exit(0);
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
            System.exit(1);
        }
    }

    private static void printStep(String step) {
        System.out.println("\n=== " + step + " ===");
    }

    private static void setupEnvironment(String[] args) {
        if (args.length > 0)
            Env.SENDER_PARTY_HINT = args[0];

        printStep("Print environment variables");
        System.out.println("LEDGER_API_URL: " + Env.LEDGER_API_URL);
        System.out.println("VALIDATOR_API_URL: " + Env.VALIDATOR_API_URL);
        System.out.println("VALIDATOR_TOKEN: "
                + (Env.VALIDATOR_TOKEN.isEmpty() ? "<empty>" : Env.VALIDATOR_TOKEN.substring(0, 5) + "..."));
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

    private static String createTransferPreapproval(String externalParty, String hostParty) throws Exception {
        throw new Exception("Need to create a transfer pre-approval contract");
    }

    private static String acceptTransferPreapproval(String hostparty, String transferPreapprovalProposalContractId) throws Exception {
        throw new Exception("Need to accept the transfer pre-approval contract");
    }

    private static String tap(String delivererParty, double tapAmount) throws Exception {
        throw new Exception("Need to accept the transfer pre-approval contract");
    }

    private static String transfer(
            String delivererParty,
            String receiverParty,
            String holdingContractId,
            double transferAmount) throws Exception {

        throw new Exception("Need to accept the transfer pre-approval contract");
    }
}
