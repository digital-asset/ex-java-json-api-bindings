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
        LedgerUser adminUser,
        Optional<ExternalParty> existingTreasuryParty,
        Optional<ExternalParty> existingTestParty,
        String ledgerApiUrl,
        String validatorApiUrl,
        String scanProxyApiUrl,
        String scanApiUrl,
        String tokenStandardUrl,
        Optional<String> synchronizerId,
        BigDecimal preferredTransferAmount,
        String memoTag,
        Optional<String> exchangePartyId,
        String treasuryPartyHint,
        String testPartyHint,
        String identitiesCacheFile
) {
    public final static String ADMIN_USER_TOKEN_KEY = "VALIDATOR_TOKEN";

    public static Env validate() throws Exception {
        LedgerUser adminUser = readAdminUser();
        Optional<ExternalParty> existingTreasuryParty = readExternalParty("TREASURY");
        Optional<ExternalParty> existingTestParty = readExternalParty("TEST");

        // localnet defaults
        String ledgerApiUrl = readApiUrl("LEDGER_API_URL", "http://canton.localhost:2975");
        String validatorApiUrl = readApiUrl("VALIDATOR_API_URL", "http://wallet.localhost:2903/api/validator");
        String scanProxyApiUrl = readApiUrl("SCAN_PROXY_API_URL", validatorApiUrl);
        String scanApiUrl = readApiUrl("SCAN_API_URL", "http://scan.localhost:4000/api/scan");

        if (scanApiUrl == null /* add other URL validations here as required*/) {
            // validation messages already printed by readApiUrl
            System.exit(1);
        }

        String tokenStandardUrl = readApiUrl("TOKEN_STANDARD_URL", "http://scan.localhost:4000");
        Optional<String> synchronizerId = readSynchronizerId();
        BigDecimal preferredTransferAmount = readTransferAmount();

        String memoTag = readMemoTag();
        Optional<String> exchangePartyId = readExchangeParty();
        String treasuryPartyHint = getenv("TREASURY_PARTY_HINT", "treasury");
        String testPartyHint = getenv("TEST_PARTY_HINT", "alice");
        String identitiesCacheFile = getenv("IDENTITIES_CACHE", "identities-cache.json");

        return new Env(
                adminUser,
                existingTreasuryParty,
                existingTestParty,
                ledgerApiUrl,
                validatorApiUrl,
                scanProxyApiUrl,
                scanApiUrl,
                tokenStandardUrl,
                synchronizerId,
                preferredTransferAmount,
                memoTag,
                exchangePartyId,
                treasuryPartyHint,
                testPartyHint,
                identitiesCacheFile
        );
    }

    private static String getenv(String name, String defaultValue) {
        String envValue = System.getenv(name);
        return envValue != null ? envValue : defaultValue;
    }

    private static String readMemoTag() {
        String memoTag = System.getenv("MEMO_TAG");
        if (memoTag == null || memoTag.isBlank()) {
            memoTag = java.util.UUID.randomUUID().toString();
            System.out.println("MEMO_TAG was not set, assigning: " + memoTag);
        }
        return memoTag;
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

    private static LedgerUser readAdminUser() throws IllegalArgumentException {
        String rawAdminUserToken = System.getenv(ADMIN_USER_TOKEN_KEY);
        if (rawAdminUserToken == null || rawAdminUserToken.isBlank()) {
            System.out.printf("""
                    This application needs a current JWT for the participant admin (a user with participant_admin rights).
                    
                    For most use cases, this means a current JWT for the validator's ledger-api-user user ID.
                    
                    Once you have configured validator authorization, populate the environment variable '%s' with the JWT to proceed.
                    
                    Please refer to the exchange integration guide for more information: https://docs.digitalasset.com/integrate/devnet/exchange-integration/node-operations.html#setup-ledger-api-users
                    
                    In the meantime, this application will try to 'guess' a valid token based on LocalNet default auth configuration.
                    %n""", rawAdminUserToken);

            // this is a dummy auth token for LocalNet
            rawAdminUserToken = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2NhbnRvbi5uZXR3b3JrLmdsb2JhbCIsInN1YiI6ImxlZGdlci1hcGktdXNlciJ9.A0VZW69lWWNVsjZmDDpVvr1iQ_dJLga3f-K2bicdtsc";
        }

        String identityProviderId = getenv("IDENTITY_PROVIDER_ID", "");

        return LedgerUser.validateUserToken(rawAdminUserToken, identityProviderId);
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