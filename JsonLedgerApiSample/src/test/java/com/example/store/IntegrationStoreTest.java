package com.example.store;

import static org.junit.jupiter.api.Assertions.*;

import com.example.GsonTypeAdapters.ExtendedJson;
import com.example.client.ledger.model.JsGetUpdatesResponse;
import com.example.testdata.TestFiles;
import com.example.testdata.TestIdentities;
import com.example.testdata.WorkflowInfo;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.google.gson.reflect.TypeToken;

class IntegrationStoreTest {

    final static Level logLevel = Level.INFO;

    @BeforeAll
    static void enableFinestLogging() {
        Logger rootLogger = Logger.getLogger("");
        rootLogger.setLevel(logLevel);
        for (Handler h : rootLogger.getHandlers()) {
            h.setLevel(logLevel);
        }
    }

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    private <T> T getTestJson(Path path, TypeToken<T> typeToken) {
        try {
            String json = Files.readString(path);
            return ExtendedJson.gson.fromJson(json, typeToken.getType());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void testDepositTracking() {
        // Get test data
        TestIdentities ids = getTestJson(TestFiles.IDENTITIES_FILE, new TypeToken<>() {
        });
        WorkflowInfo info = getTestJson(TestFiles.WORKFLOW_INFO_FILE, new TypeToken<>() {
        });
        List<JsGetUpdatesResponse> updates = getTestJson(TestFiles.TREASURY_UPDATES_FILE, new TypeToken<>() {
        });

        // Create store for treasury
        IntegrationStore store = new IntegrationStore(ids.treasury().partyId(), 0L);
        log.info("State of store before ingestion :\n" + store);

        // Read updates from sample-updates.json resources
        for (JsGetUpdatesResponse updateResponse : updates) {
            store.ingestUpdate(updateResponse.getUpdate());
        }

        log.info("State of store after ingestion:\n" + store);

        // Assert exactly one active holding exists
        assertEquals(1, store.getActiveHoldings().size());

        // Assert on the balances
        // FIXME
        // assertEquals(damlDecimal(100), store.getDepositBalance(ids.cantonCoinId(), info.aliceDepositId()));
    }

    static private BigDecimal damlDecimal(long val) {
        return BigDecimal.valueOf(val).setScale(10, RoundingMode.CEILING);
    }
}