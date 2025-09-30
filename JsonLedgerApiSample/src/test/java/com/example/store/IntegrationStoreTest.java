package com.example.store;

import com.example.GsonTypeAdapters.ExtendedJson;
import com.example.client.ledger.model.JsGetUpdatesResponse;
import com.example.store.models.TxHistoryEntry;
import com.example.testdata.TestFiles;
import com.example.testdata.TestIdentities;
import com.example.testdata.WorkflowInfo;
import com.google.gson.reflect.TypeToken;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import splice.api.token.holdingv1.HoldingView;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.fail;

class IntegrationStoreTest {

    final static Level minLogLevel = Level.WARNING;

    @BeforeAll
    static void setMinLogLevel() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(minLogLevel);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(minLogLevel);
        }
    }

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    private <T> T readTestJson(Path path, TypeToken<T> typeToken) {
        try {
            String json = Files.readString(path);
            return ExtendedJson.gson.fromJson(json, typeToken.getType());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Test that the deposit and withdrawal transactions are correctly recognized.
     * Generate test data by running the `Main` program with `--write-test-data`.
     */
    @Test
    void testOneDepositAndWithdrawAndCompareLog() {
        TestIdentities ids = readTestJson(TestFiles.IDENTITIES_FILE, new TypeToken<>() {
        });
        WorkflowInfo info = readTestJson(TestFiles.WORKFLOW_INFO_FILE, new TypeToken<>() {
        });
        List<JsGetUpdatesResponse> updates = readTestJson(TestFiles.TREASURY_UPDATES_FILE, new TypeToken<>() {
        });

        // Create store for treasury
        IntegrationStore store = new IntegrationStore(ids.treasury().partyId(), 0L);
        log.info("State of store before ingestion :\n" + store);

        // Read updates from sample-updates.json resources
        for (JsGetUpdatesResponse updateResponse : updates) {
            store.ingestUpdate(updateResponse.getUpdate());
        }

        log.info("State of store after ingestion:\n" + store);

        // Assert that exactly one active holding exists
        assertEquals(1, store.getActiveHoldings().size());
        HoldingView holding = store.getActiveHoldings().values().iterator().next();
        assertEquals(ids.treasury().partyId(), holding.owner);
        assertEquals(damlDecimal(80), holding.amount); // 100 deposited - 20 withdrawn

        // There are exactly the deposit and withdrawal in the tx history log
        List<TxHistoryEntry> history = store.getTxHistoryLog();
        assertEquals(2, history.size());

        TxHistoryEntry entry1 = history.get(0);
        TxHistoryEntry.TransferDetails transferDetails1 = new TxHistoryEntry.TransferDetails(
                info.aliceDepositId(),
                ids.cantonCoinId(), damlDecimal(100),
                TxHistoryEntry.TransferStatus.COMPLETED,
                null,
                null
        );
        TxHistoryEntry.Transfer label1 = new TxHistoryEntry.Transfer(
                ids.alice().partyId(),
                ids.treasury().partyId(),
                TxHistoryEntry.TransferKind.TRANSFER_IN,
                transferDetails1);
        assertEquals(label1, entry1.transfer());

        TxHistoryEntry entry2 = history.get(1);
        TxHistoryEntry.TransferDetails transferDetails2 = new TxHistoryEntry.TransferDetails(
                info.aliceWithdrawalId(),
                ids.cantonCoinId(), damlDecimal(20),
                TxHistoryEntry.TransferStatus.COMPLETED,
                null,
                null
        );

        TxHistoryEntry.Transfer label2 = new TxHistoryEntry.Transfer(
                ids.treasury().partyId(),
                ids.alice().partyId(),
                TxHistoryEntry.TransferKind.TRANSFER_OUT,
                transferDetails2);
        assertEquals(label2, entry2.transfer());
    }

    @Test
    void testSpliceReferenceTestCases() {
        // This test runs the transaction parser on the tx parser test cases
        // used in the https://github.com/hyperledger-labs/splice repo as part of
        // the token standard parsing reference implementation copied from
        // https://github.com/hyperledger-labs/splice/blob/fa489964c2b37e1b5d0adad66eabe7315eef473b/token-standard/cli/__tests__/mocks/data/txs.json
        // and then modified using search-and-replace to replace "alice::normalized" with "treasury::normalized".
        testGolden("splice-test-cases", "treasury::normalized");
    }

    @Test
    void testSpliceReferenceTestCasesWithDummyParty() {
        // Parse the same data as in the test above, but with a party that would not be the treasury.
        testGolden("splice-test-cases", "dummy::normalized");
    }

    @Test
    void testGoldenOneStepDepositAndWithdraw() {
        // Parse the same data as in the test above, but with a party that would not be the treasury.
        testGolden("one-step-deposit-and-withdraw",
                "treasury::1220bada55b12697a660ade92a1c920b2cd9d9bed0e854c17fb3697119c46b29c5e8");
    }

    // TODO: test parsing of a merge, and make it store a memoTag as well

    private void testGolden(String baseName, String treasuryPartyId) {
        String treasuryHint = treasuryPartyId.substring(0, treasuryPartyId.indexOf(':'));
        Path updatesFile = TestFiles.GOLDEN_TEST_DIR.resolve(baseName + ".json");
        Path expectedStoreFile = updatesFile.getParent().resolve(baseName + "_" + treasuryHint + "_expected.json");
        Path actualStoreFile = updatesFile.getParent().resolve(baseName + "_" + treasuryHint + "_actual.json");

        System.out.println("running golden test with Ledger API updates list from: " + updatesFile);
        System.out.println("  treasury party: " + treasuryPartyId);
        System.out.println("  expected: " + expectedStoreFile);
        System.out.println("  actual:   " + actualStoreFile);

        // ingest all updates
        List<JsGetUpdatesResponse> updates = readTestJson(updatesFile, new TypeToken<>() {
        });
        IntegrationStore store = new IntegrationStore(treasuryPartyId, 0L);
        for (JsGetUpdatesResponse updateResponse : updates) {
            store.ingestUpdate(updateResponse.getUpdate());
        }

        // write and compare test files
        String storeJson = store.toString();
        try {
            Files.writeString(actualStoreFile, storeJson);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (!Files.exists(expectedStoreFile)) {
            log.info("No expected store file found at " + expectedStoreFile + ", seeding it with actual store content.");
            try {
                Files.writeString(expectedStoreFile, storeJson);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
            fail("Expected store file '" + expectedStoreFile + "' did not exist, created it from actual store. Please verify and re-run the test.");
        } else {
            try {
                String expectedJson = Files.readString(expectedStoreFile);
                assertEquals(storeJson, expectedJson);
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }
    }

    static private BigDecimal damlDecimal(long val) {
        return BigDecimal.valueOf(val).setScale(10, RoundingMode.CEILING);
    }
}

