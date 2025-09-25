package com.example.store;

import static org.junit.jupiter.api.Assertions.*;

import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.JsGetUpdatesResponse;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;
import java.lang.reflect.Type;
import java.util.logging.Logger;

import com.google.gson.reflect.TypeToken;

class IntegrationStoreTest {

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    final static String treasuryParty = "treasury::12206b095339d93f62c84ae52c8d60e057f6da8ad14903d5f4c43e5bb274fb5ea3d0";
    final static com.google.gson.Gson gson = JSON.getGson();

    private String getTestData(String resourcePath) {
        try (InputStream in = getClass().getResourceAsStream(resourcePath)) {
            return new String(in.readAllBytes());
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + resourcePath, e);
        }
    }

    private <T> T getTestJson(String resourcePath, TypeToken<T> typeToken) {
        String json = getTestData(resourcePath);
        return gson.fromJson(json, typeToken.getType());
    }

    @Test
    void testIngestEmptyACSAndSingleUpdate() throws Exception {
        IntegrationStore store = new IntegrationStore(treasuryParty);

        log.info("State of store before ingestion :\n" + store);

        // Assume we start with an empty ACS
        store.ingestAcs(List.of(), 0L);

        log.info("State of store after ingestion ACS:\n" + store);

        // Read updates from sample-updates.json resources
        List<JsGetUpdatesResponse> updates = getTestJson("/sample-updates.json", new TypeToken<List<JsGetUpdatesResponse>>() {
        });

        for (JsGetUpdatesResponse updateResponse : updates) {
            store.ingestUpdate(updateResponse.getUpdate());
        }

        log.info("State of store after ingestion:\n" + store);

        // Assert exactly one active holding exists
        assertEquals(1, store.getActiveHoldings().size());
    }
}