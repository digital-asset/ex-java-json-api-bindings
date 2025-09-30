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
import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.CompletionStreamResponse;
import com.example.client.ledger.model.JsGetUpdatesResponse;
import com.example.client.ledger.model.Status;
import com.example.models.ContractAndId;
import com.example.services.CommandCompletionTracker;
import com.example.services.Ledger;
import com.example.services.Wallet;
import com.example.signing.Keys;
import com.example.signing.SignatureProvider;
import com.example.store.IntegrationStore;
import com.example.testdata.TestFiles;
import com.example.testdata.TestIdentities;
import com.example.testdata.WorkflowInfo;
import com.google.gson.Gson;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.nio.file.Path;
import java.security.KeyPair;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Main {

    private static final BigDecimal estimatedFeesMultiplier = new BigDecimal("0.1");

    public static void main(String[] args) {
        try {
            /*
            For the exchange:
               read user token from environment
               user should have ExecuteAsAnyParty permissions
               read user ID from token

            For the treasury:
               read the treasury party, public key, and private key from the environment

            For the test party:
               read the test party, public key, and private key from the environment
             */

            Env env = Env.validate();

            // using this SignatureProvider visualises transactions during transaction signing
            // SignatureProvider signatureProvider = Ledger::sign;
            SignatureProvider signatureProvider = Ledger::verifyAndSign;
            // SignatureProvider signatureProvider = Ledger::printAndSign;

            Wallet wallet = new Wallet(
                    env.adminUser(),
                    env.scanApiUrl(),
                    env.tokenStandardUrl(),
                    env.ledgerApiUrl(),
                    env.validatorApiUrl(),
                    env.scanProxyApiUrl(),
                    signatureProvider
            );

            printStep("Confirm API connectivity");
            wallet.confirmConnectivity();

            printStep("Confirm authentication");
            wallet.confirmAuthentication();

            printStep("Setup exchange parties (or read from cache)");
            TestIdentities ids = getOrCreateTestIdentities(env, wallet);

            printStep("Initialize integration store");
            // We intentionally pretend that the ACS is empty at the start of the demo run.
            // Otherwise, we see the transfers from previous runs of the demo.
            // In production, we would just start with offset 0 and ingest the full tx history.
            Long testStartOffset = wallet.getLedgerEnd();
            IntegrationStore store = new IntegrationStore(ids.treasury().partyId(), testStartOffset);
            ingestAndParseTransactions(wallet, store);
            System.out.println("State of local store after initial ingestion");
            System.out.println(store);

            // calculate the first transfer amount
            // sender needs at least enough coins
            // to cover the fees of transferring them out again
            BigDecimal transferAmount1 = env.preferredTransferAmount();
            BigDecimal estimatedFees = transferAmount1.multiply(estimatedFeesMultiplier);
            transferAmount1 = transferAmount1.add(estimatedFees);

            printTotalHoldings(wallet, ids.all(), ids.cantonCoinId());


            // perform a transfer from the local party operator
            transferAsset(
                    wallet,
                    ids.synchronizerId(),
                    ids.exchangePartyId(),
                    Optional.empty(),
                    ids.alice().partyId(),
                    transferAmount1,
                    ids.cantonCoinId(),
                    Optional.empty());

            printTotalHoldings(wallet, ids.all(), ids.cantonCoinId());

            // calculate transfer amount
            BigDecimal transferAmount2 = env.preferredTransferAmount();

            String memoTag = env.memoTag();

            // Perform a transfer modelling a deposit from alice to the treasury
            transferAsset(
                    wallet,
                    ids.synchronizerId(),
                    ids.alice().partyId(),
                    Optional.of(ids.alice().keyPair()),
                    ids.treasury().partyId(),
                    transferAmount2,
                    ids.cantonCoinId(),
                    Optional.of(memoTag));

            // Perform a transfer modelling a withdrawal of funds from the treasury to alice
            String withdrawalId = java.util.UUID.randomUUID().toString();
            transferAssetFromTreasury(
                    wallet,
                    ids,
                    store,
                    ids.alice().partyId(),
                    new BigDecimal(20),
                    ids.cantonCoinId(),
                    withdrawalId);

            printStep("Success!");
            printTotalHoldings(wallet, ids.all(), ids.cantonCoinId());

            // Update IntegrationStore -- this would usually happen in a background process
            ingestAndParseTransactions(wallet, store);
            printStep("State of local store after final transfer");
            System.out.println(store);

            // Write out test data for use in automated integration tests if requested via --write-test-data
            printStep("Write integration test data");
            if (Arrays.asList(args).contains("--write-test-data")) {
                WorkflowInfo info = new WorkflowInfo(
                        memoTag,
                        withdrawalId
                );
                writeIntegrationTestData(wallet, testStartOffset, ids, info);
            } else {
                System.out.println("Skipping writing integration test data, pass --write-test-data to enable");
            }

            System.exit(0);
        } catch (Exception ex) {
            handleException(ex);
        }
    }

    private static void ingestAndParseTransactions(Wallet wallet, IntegrationStore store) {
        long lastIngestedOffset = store.getLastIngestedOffset();
        assert lastIngestedOffset >= 0;
        try {
            List<JsGetUpdatesResponse> updates = wallet.queryForHoldingTransactions(store.getTreasuryParty(), lastIngestedOffset);
            for (JsGetUpdatesResponse updateResponse : updates) {
                store.ingestUpdate(updateResponse.getUpdate());
            }
        } catch (Exception ex) {
            handleException(ex);
        }
    }

    private static void waitFor(long sleepForMillis, int retries, WaitLoopCheck checkState) throws Exception {
        while (!checkState.getAsBoolean() && retries > 0) {
            System.out.println("Waiting...");
            Thread.sleep(sleepForMillis);
            retries--;
        }
        if (retries == 0) {
            System.out.println("Check failed, aborting...");
            System.exit(3);
        }
    }

    private static void printStep(String step) {
        System.out.println("\n=== " + step + " ===");
    }

    private static void printTotalHoldings(Wallet wallet, List<ExternalParty> parties, InstrumentId instrumentId) throws Exception {
        printStep("Print total holdings");
        for (ExternalParty party : parties) {
            String partyId = party.partyId();
            BigDecimal totalHoldings = wallet.getTotalHoldings(partyId, instrumentId);
            System.out.println(partyId + " has " + totalHoldings + " " + instrumentId.id);
        }
        System.out.println();
    }

    private static String defaultToValidatorParty(Wallet wallet) {
        try {
            String validatorPartyId = wallet.validatorApi.getValidatorParty();
            System.out.printf("Exchange party not specified, defaulting to validator node party: %s%n", validatorPartyId);
            return validatorPartyId;
        } catch (com.example.client.validator.invoker.ApiException ex) {
            System.err.println("Could not get the validator party.");
            handleException(ex);
            return null;
        }
    }

    private static String findTransferSynchronizerId(Wallet wallet) {
        try {
            String synchronizerId = wallet.scanApi.getSynchronizerId();
            System.out.println("Selecting synchronizer id: " + synchronizerId);
            return synchronizerId;
        } catch (com.example.client.scan.invoker.ApiException ex) {
            System.err.println("Could not get the synchronizer id.");
            handleException(ex);
            return null;
        }
    }

    private static ExternalParty allocateNewExternalParty(Wallet wallet, String synchronizerId, String dso, String exchangePartyId, String partyHint) {
        try {
            printStep("Generating keypair and wallet for %s".formatted(partyHint));
            KeyPair externalPartyKeyPair = Keys.generate();

            System.out.printf("Allocating new external party with hint: %s%n", partyHint);
            ExternalParty externalParty = wallet.allocateExternalPartyNew(synchronizerId, partyHint, externalPartyKeyPair);

            System.out.println("Allocated party: " + externalParty.partyId());
            Keys.printKeyPairSummary(partyHint, externalPartyKeyPair);

            // the validator node will automatically accept any transfer preapproval proposal submitted to it.
            printStep("Pre-approving " + externalParty.partyId() + " for CC transfers");
            Long offsetBeforeProposal = wallet.getLedgerEnd();
            System.out.println("Marking offset: " + offsetBeforeProposal);

            String commandId = java.util.UUID.randomUUID().toString();
            wallet.issueTransferPreapprovalProposal(synchronizerId, commandId, dso, exchangePartyId, externalParty.partyId(), externalParty.keyPair());

            System.out.printf("Awaiting completion of transfer preapproval proposal (Command ID %s)%n", commandId);
            expectSuccessfulCompletion(wallet, externalParty.partyId(), commandId, offsetBeforeProposal);

            System.out.println("Awaiting auto-acceptance of transfer preapproval proposal");
            waitFor(5 * 1000, 12, () -> {
                return wallet.hasEstablishedTransferPreapproval(externalParty.partyId());
            });

            assert wallet.getPartyDetails(externalParty.partyId()).isPresent();
            assert wallet.hasCantonCoinTransferPreapproval(externalParty.partyId());

            return externalParty;
        } catch (Exception ex) {
            handleException(ex);
            return null;
        }
    }

    private static TestIdentities getOrCreateTestIdentities(Env env, Wallet wallet) throws Exception {

        String dsoParty = wallet.getDsoPartyId();

        String cacheFile = env.identitiesCacheFile();
        final boolean useCache = !cacheFile.equals("-");
        if (!useCache) {
            System.out.println("Not using identities cache as IDENTITIES_CACHE was set to '-'");
        } else {
            System.out.println("Attempting to read identities from cache file: " + cacheFile);
            System.out.println("(use `export IDENTITIES_CACHE=-` to disable cache usage)");
            try {
                String json = java.nio.file.Files.readString(java.nio.file.Paths.get(cacheFile));
                TestIdentities cachedIds = JSON.getGson().fromJson(json, TestIdentities.class);
                // We use the DSO party as a cache key, which should work as it changes on restarts of LocalNet
                if (cachedIds.dsoPartyId().equals(dsoParty)) {
                    System.out.println("Using cached identities: ");
                    System.out.println("  Synchronizer ID: " + cachedIds.synchronizerId());
                    System.out.println("  DSO: " + cachedIds.dsoPartyId());
                    System.out.println("  Exchange: " + cachedIds.exchangePartyId());
                    System.out.println("  Treasury: " + cachedIds.treasury().partyId());
                    System.out.println("  Alice: " + cachedIds.alice().partyId());

                    return cachedIds;
                } else {
                    System.out.println("DSO party in cache (" + cachedIds.dsoPartyId() + ") does not match current DSO party (" + dsoParty + "), ignoring cache");
                }
            } catch (Exception e) {
                System.out.println("Could not read identities from cache file due to: " + e);
            }
        }

        // Cache miss, need to create the parties
        String resolvedSynchronizerId = env.synchronizerId()
                .orElseGet(() -> findTransferSynchronizerId(wallet));

        String resolvedExchangePartyId = env.exchangePartyId()
                .orElseGet(() -> defaultToValidatorParty(wallet));

        ExternalParty treasuryParty = env.existingTreasuryParty()
                .orElseGet(() -> allocateNewExternalParty(
                        wallet,
                        resolvedSynchronizerId,
                        dsoParty,
                        resolvedExchangePartyId,
                        env.treasuryPartyHint()
                ));

        ExternalParty testParty = env.existingTestParty()
                .orElseGet(() -> allocateNewExternalParty(
                        wallet,
                        resolvedSynchronizerId,
                        dsoParty,
                        resolvedExchangePartyId,
                        env.testPartyHint()
                ));

        TestIdentities ids = new TestIdentities(resolvedSynchronizerId, dsoParty, resolvedExchangePartyId, treasuryParty, testParty);

        if (useCache) {
            System.out.println("Writing identities to cache file: " + cacheFile);
            try {
                java.nio.file.Files.writeString(java.nio.file.Paths.get(cacheFile), JSON.getGson().toJson(ids));
            } catch (Exception e) {
                System.out.println("Could not write identities to cache file due to: " + e);
            }
        }
        return ids;
    }

    private static void validateHoldings(BigDecimal amount, List<ContractAndId<HoldingView>> holdings) {
        if (holdings == null) {
            System.err.println("Insufficient holdings to transfer " + amount + " units");
            System.exit(1);
        } else {
            System.out.println("Found sufficient holdings for transfer: ");
            for (ContractAndId<HoldingView> holding : holdings) {
                System.out.println("- " + holding.record().amount + " " + holding.record().instrumentId.id);
            }
        }
    }

    private static void transferAsset(
            Wallet wallet,
            String synchronizerId,
            String senderPartyId,
            Optional<KeyPair> senderKeyPair,
            String receiverPartyId,
            BigDecimal amount,
            InstrumentId instrumentId,
            Optional<String> memoTag) throws Exception {
        printStep("Transfer " + amount + " from " + senderPartyId + " to " + receiverPartyId);

        List<ContractAndId<HoldingView>> holdings = wallet.selectHoldingsForTransfer(senderPartyId, instrumentId, amount);
        validateHoldings(amount, holdings);
        List<String> holdingContractIds = holdings.stream().map(ContractAndId::contractId).toList();

        String commandId = java.util.UUID.randomUUID().toString();

        Long offsetBeforeTransfer = wallet.getLedgerEnd();
        boolean transferWasSubmitted = wallet.transferHoldings(synchronizerId, commandId, senderPartyId, senderKeyPair, receiverPartyId, instrumentId, memoTag, new HashMap<>(), amount, holdingContractIds, true);
        if (!transferWasSubmitted) {
            throw new IllegalStateException("Transfer preapproval was established for party %s, but no preapproval was found when setting up transfer");
        }

        System.out.printf("Awaiting completion of transfer from %s to %s (Command ID %s)%n%n", senderPartyId, receiverPartyId, commandId);
        expectSuccessfulCompletion(wallet, senderPartyId, commandId, offsetBeforeTransfer);

        System.out.println("Transfer complete");
    }

    /**
     * Transfer asset from the treasury, using the IntegrationStore to select holdings.
     * <p>
     * Using the IntegrationStore is important to avoid getting confusing by earlier runs with the same cached
     * treasury party.
     */
    private static void transferAssetFromTreasury(
            Wallet wallet,
            TestIdentities ids,
            IntegrationStore store,
            String receiverPartyId,
            BigDecimal amount,
            InstrumentId instrumentId,
            String transferId) throws Exception {
        printStep("Transfer " + amount + " from treasury (" + ids.treasury().partyId() + ") to " + receiverPartyId);

        // Update integration store to ensure we have the latest holdings
        ingestAndParseTransactions(wallet, store);
        List<String> holdings = store.selectHoldingsForWithdrawal(instrumentId, amount).orElseGet(
                () -> {
                    throw new RuntimeException("Insufficient holdings in treasury to transfer " + amount + " units");
                }
        );

        String commandId = java.util.UUID.randomUUID().toString();

        Long offsetBeforeTransfer = wallet.getLedgerEnd();
        boolean transferWasSubmitted =
                wallet.transferHoldings(ids.synchronizerId(), commandId, ids.treasury().partyId(), Optional.of(ids.treasury().keyPair()), receiverPartyId, instrumentId, Optional.of(transferId), new HashMap<>(), amount, holdings, true);
        if (!transferWasSubmitted) {
            throw new IllegalStateException("Transfer preapproval was established for party %s, but no preapproval was found when setting up transfer");
        }

        System.out.printf("Awaiting completion of transfer from %s to %s (Command ID %s)%n%n", ids.treasury().partyId(), receiverPartyId, commandId);
        expectSuccessfulCompletion(wallet, ids.treasury().partyId(), commandId, offsetBeforeTransfer);

        System.out.println("Transfer complete");
    }

    private static void expectSuccessfulCompletion(Wallet wallet, String partyId, String commandId, Long startOffset) throws Exception {
        CommandCompletionTracker completionTracker = new CommandCompletionTracker(startOffset);
        waitFor(2000, 10, () -> {
            List<CompletionStreamResponse> completions = wallet.checkForCommandCompletion(List.of(partyId), completionTracker.nextOffset);
            completionTracker.observeCompletions(completions);
            return completionTracker.resultCodeFor(commandId).isPresent();
        });

        Optional<Status> completionStatus = completionTracker.resultCodeFor(commandId);
        if (completionStatus.isEmpty()) {
            throw new IllegalStateException("No completion of command with ID " + commandId + " was observed");
        }

        Status status = completionStatus.get();

        if (!status.getCode().equals(0)) {
            throw new IllegalStateException("Command with ID %s failed with status %d, reason: %s%n"
                    .formatted(commandId, status.getCode(), status.getMessage()));
        }
    }

    private static void handleException(Exception ex) {
        System.err.println(ex.getMessage());
        ex.printStackTrace();
        System.exit(1);
    }

    private static void writeIntegrationTestData(Wallet wallet, Long startOffset, TestIdentities ids, WorkflowInfo info) {
        Gson gson = JSON.getGson().newBuilder().setPrettyPrinting().create();
        try {
            java.nio.file.Files.writeString(TestFiles.IDENTITIES_FILE, gson.toJson(ids));
            java.nio.file.Files.writeString(TestFiles.WORKFLOW_INFO_FILE, gson.toJson(info));
            List<JsGetUpdatesResponse> updates = wallet.queryForHoldingTransactions(ids.treasury().partyId(), startOffset);
            java.nio.file.Files.writeString(TestFiles.TREASURY_UPDATES_FILE, gson.toJson(updates));
        } catch (Exception e) {
            System.out.println("Could not write integration test data to file due to: " + e);
        }
        System.out.println("Wrote integration test data to '" + TestFiles.DATA_DIR + "'");
    }

    private interface WaitLoopCheck {
        boolean getAsBoolean() throws Exception;
    }

}
