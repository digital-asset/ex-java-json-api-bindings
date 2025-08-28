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
import com.example.client.validator.model.TopologyTx;

import java.security.KeyPair;
import java.util.List;

public class Main {
    public static void main(String[] args) {
        setupEnvironment(args);
        try {
            Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
            Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);
            confirmConnectivity(ledgerApi, validatorApi);
            confirmAuthentication(ledgerApi, validatorApi);
            onboardNewUser(Env.NEW_PARTY_HINT, validatorApi);
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
            Env.NEW_PARTY_HINT = args[0];

        printStep("Print environment variables");
        System.out.println("LEDGER_API_URL: " + Env.LEDGER_API_URL);
        System.out.println("VALIDATOR_API_URL: " + Env.VALIDATOR_API_URL);
        System.out.println("VALIDATOR_TOKEN: "
                + (Env.VALIDATOR_TOKEN.isEmpty() ? "<empty>" : Env.VALIDATOR_TOKEN.substring(0, 5) + "..."));
        System.out.println("NEW_PARTY_HINT: " + Env.NEW_PARTY_HINT);
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

    private static void onboardNewUser(String partyHint, Validator validatorApi) throws Exception {
        printStep("Onboard " + partyHint);

        // KeyPair keyPair = Keys.generate();

        // from https://daholdings.slack.com/archives/C08P8TN7KKM/p1756315578998549?thread_ts=1756299658.068089&cid=C08P8TN7KKM
        String publicKeyReference = "PntesmqjJYbaxkQgYgeJ7OOgaQMCtwekOfDqronPgMY=";
        String privateKeyReference = "BrXeL1/4s0Hh7KJ5cdngj2rBJVFDehzax7a6KQ3HV90+e16yaqMlhtrGRCBiB4ns46BpAwK3B6Q58Oquic+Axg==";
        KeyPair keyPair = Keys.createFromRawBase64(publicKeyReference, privateKeyReference);

        System.out.println("Public key algorithm: " + keyPair.getPublic().getAlgorithm());
        System.out.println("              format: " + keyPair.getPublic().getFormat());
        System.out.println("      (Java, base64): " + Encode.toBase64String(keyPair.getPublic().getEncoded()));
        System.out.println("       (raw, base64): " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())));
        System.out.println("          (raw, hex): " + Encode.toHexString(Keys.toRawBytes(keyPair.getPublic())));

        System.out.println(" Private key algorithm: " + keyPair.getPrivate().getAlgorithm());
        System.out.println("                format: " + keyPair.getPrivate().getFormat());
        System.out.println("        (Java, base64): " + Encode.toBase64String(keyPair.getPrivate().getEncoded()));
        System.out.println("(raw + public, base64): " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));
        System.out.println("   (raw + public, hex): " + Encode.toHexString(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));

        if(!publicKeyReference.equals(Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())))) {
            throw new Exception("Conversion error with public keys.");
        };

        if(!privateKeyReference.equals(Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())))) {
            throw new Exception("Conversion error with private keys.");
        }

        List<TopologyTx> txs = validatorApi.prepareOnboarding(partyHint, keyPair.getPublic());
        List<SignedTopologyTx> signedTxs = ExternalSigning.signOnboarding(txs, keyPair.getPrivate());
        String newParty = validatorApi.submitOnboarding(signedTxs, keyPair.getPublic());
        System.out.println("New party: " + newParty);
    }
}
