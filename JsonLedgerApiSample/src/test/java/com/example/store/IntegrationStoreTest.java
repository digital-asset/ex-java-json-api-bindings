package com.example.store;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Paths;

class IntegrationStoreTest {

    @Test
    void testIngestEmptyACSAndSingleUpdate() throws Exception {
        IntegrationStore store = new IntegrationStore();

        // Ingest empty ACS
        store.ingestACS(new ACS());

        // Read update from JSON file
        String json = new String(Files.readAllBytes(Paths.get("src/test/resources/update.json")));
        Update update = Update.fromJson(json);

        // Ingest the update
        store.ingestUpdate(update);

        // Assert exactly one active holding exists
        assertEquals(1, store.getActiveHoldings().size());
    }
}