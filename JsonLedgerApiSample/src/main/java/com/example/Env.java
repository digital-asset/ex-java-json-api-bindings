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

import com.example.access.ExternalParty;
import com.example.access.LedgerUser;
import com.example.signing.Keys;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.util.Optional;

public record Env(
        LedgerUser managingUser,
        Optional<ExternalParty> existingTreasuryParty,
        Optional<ExternalParty> existingTestParty,
        String ledgerApiUrl,
        String validatorApiUrl,
        String scanProxyApiUrl,
        String scanApiUrl,
        String tokenStandardUrl,
        Optional<String> synchronizerId,
        BigDecimal preferredTransferAmount,
        Optional<String> exchangePartyId,
        String treasuryPartyHint,
        String testPartyHint
) {
    public final static String MANAGING_USER_TOKEN_KEY = "VALIDATOR_TOKEN";

    /*
    public final static String LEDGER_API_URL = getenv("LEDGER_API_URL", "http://wallet.localhost/api/participant");
    public final static String VALIDATOR_API_URL = getenv("VALIDATOR_API_URL", "http://wallet.localhost/api/validator");
    public final static String SCAN_PROXY_API_URL = getenv("SCAN_PROXY_API_URL", Env.VALIDATOR_API_URL);
    public final static String SCAN_API_URL = getenv("SCAN_API_URL", "");

    public final static String VALIDATOR_TOKEN = getenv(MANAGING_USER_TOKEN_KEY, "");

    public final static String TREASURY_PARTY_HINT = getenv("TREASURY_PARTY_HINT", "treasury");
    public final static String TREASURY_TOKEN = getenv("TREASURY_TOKEN", "");
    public final static String SENDER_PARTY_HINT = getenv("SENDER_PARTY_HINT", "alice");
    public final static String SENDER_TOKEN = getenv("SENDER_TOKEN", "");
    public final static String LEDGER_USER_ID = getenv("LEDGER_USER_ID", "");
    public final static String TRANSFER_AMOUNT = getenv("TRANSFER_AMOUNT", "100");
    public static String TREASURY_PARTY = getenv("TREASURY_PARTY", "");
    public static String SENDER_PARTY = getenv("SENDER_PARTY", "");
    public static String SENDER_PUBLIC_KEY = getenv("SENDER_PUBLIC_KEY", "");
    public static String SENDER_PRIVATE_KEY = getenv("SENDER_PRIVATE_KEY", "");
    public static String SYNCHRONIZER_ID = "";
    public static String DSO_PARTY = "";
    public static String VALIDATOR_PARTY = getenv("VALIDATOR_PARTY", "");
     */

    public static Env validate() throws Exception {
        LedgerUser managingUser = readManagingUser();
        Optional<ExternalParty> existingTreasuryParty = readExternalParty("TREASURY");
        Optional<ExternalParty> existingTestParty = readExternalParty("SENDER");

        String ledgerApiUrl = readApiUrl("LEDGER_API_URL", "http://wallet.localhost/api/participant");
        String validatorApiUrl = readApiUrl("VALIDATOR_API_URL", "http://wallet.localhost/api/validator");
        String scanProxyApiUrl = readApiUrl("SCAN_PROXY_API_URL", validatorApiUrl);
        String scanApiUrl = readApiUrl("SCAN_API_URL", null);

        if (scanApiUrl == null /* add other URL validations here as required*/ ) {
            // validation messages already printed by readApiUrl
            System.exit(1);
        }

        String tokenStandardUrl = readApiUrl("TOKEN_STANDARD_URL", scanApiUrl);
        Optional<String> synchronizerId = readSynchronizerId();
        BigDecimal preferredTransferAmount = readTransferAmount();

        Optional<String> exchangePartyId = readExchangeParty();
        String treasuryPartyHint = getenv("TREASURY_PARTY_HINT", "treasury");
        String testPartyHint = getenv("TEST_PARTY_HINT", "alice");

        return new Env(
            managingUser,
            existingTreasuryParty,
            existingTestParty,
            ledgerApiUrl,
            validatorApiUrl,
            scanProxyApiUrl,
            scanApiUrl,
            tokenStandardUrl,
            synchronizerId,
            preferredTransferAmount,
            exchangePartyId,
            treasuryPartyHint,
            testPartyHint
        );
    }

    private static String getenv(String name, String defaultValue) {
        String envValue = System.getenv(name);
        return envValue != null ? envValue : defaultValue;
    }

    private static Optional<String> readExchangeParty() {
        String exchangeParty = System.getenv("EXCHANGE_PARTY");
        if (exchangeParty == null || exchangeParty.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(exchangeParty);
    }

    private static BigDecimal readTransferAmount() {
        String raw = System.getenv("TRANSFER_AMOUNT");
        if (raw == null || raw.isBlank()) {
            return new BigDecimal(100);
        }
        return new BigDecimal(raw);
    }

    private static Optional<String> readSynchronizerId() {
        String foundSynchronizerId = System.getenv("SYNCHRONIZER_ID");
        if (foundSynchronizerId != null) {
            System.out.println("Using configured synchronizerId: " + foundSynchronizerId);
            return Optional.of(foundSynchronizerId);
        } else {
            return Optional.empty();
        }
    }

    private static LedgerUser readManagingUser() throws IllegalArgumentException {
        String rawManagingUserToken = System.getenv(MANAGING_USER_TOKEN_KEY);
        if (rawManagingUserToken == null || rawManagingUserToken.isBlank()) {
            System.out.printf("""
                    This application needs a current JWT for the participant admin (a user with participant_admin rights).
                    
                    For most use cases, this means a current JWT for the validator's ledger-api-user user ID.
                    
                    Populate the environment variable '%s' with the JWT to proceed.
                    
                    Please refer to the exchange integration guide for more information: https://docs.digitalasset.com/integrate/devnet/exchange-integration/node-operations.html#setup-ledger-api-users
                    %n""", rawManagingUserToken);
            System.exit(1);
        }

        String identityProviderId = getenv("IDENTITY_PROVIDER_ID", "");

        return LedgerUser.validateUserToken(rawManagingUserToken, identityProviderId);
    }

    private static Optional<ExternalParty> readExternalParty(String environmentPrefix) throws Exception {

        String partyVariable = environmentPrefix + "_PARTY";
        String privateKeyVariable = environmentPrefix + "_PRIVATE_KEY";
        String publicKeyVariable = environmentPrefix + "_PUBLIC_KEY";

        String partyId = System.getenv(partyVariable);
        if (partyId == null || partyId.isBlank()) {
            return Optional.empty();
        }

        String privateKey = System.getenv(privateKeyVariable);
        String publicKey = System.getenv(publicKeyVariable);

        if (publicKey == null || publicKey.isBlank()) {
            throw new IllegalArgumentException(partyVariable + " was set in the environment, but " + publicKeyVariable + " was missing");
        }

        if (privateKey == null || privateKey.isBlank()) {
            throw new IllegalArgumentException(partyVariable + " was set in the environment, but " + privateKeyVariable + " was missing");
        }

        KeyPair keyPair = Keys.createAndValidateKeyPair(publicKey, privateKey);

        return Optional.of(new ExternalParty(partyId, keyPair));
    }

    private static String readApiUrl(String environmentKey, String defaultValue) {
        String environmentValue = System.getenv(environmentKey);

        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue;
        } else if (defaultValue != null) {
            System.out.printf("Environment variable %s was not set, defaulting to %s...%n", environmentKey, defaultValue);
            return defaultValue;
        } else {
            System.out.printf("""
                Environment variable %s was not set, and no default was available.
                
                Please set the %s environment variable and try again.
                %n""", environmentKey, environmentKey);
            return null;
        }

    }
}