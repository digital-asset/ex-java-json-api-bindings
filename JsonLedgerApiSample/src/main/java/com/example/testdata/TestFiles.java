package com.example.testdata;

import java.nio.file.Path;

public class TestFiles {
    public final static Path DATA_DIR = Path.of("src", "test", "resources", "integration-test-data");
    public final static Path IDENTITIES_FILE = DATA_DIR.resolve("identities.json");
    public final static Path TREASURY_UPDATES_FILE = DATA_DIR.resolve("treasury-updates.json");
    public static final Path WORKFLOW_INFO_FILE = DATA_DIR.resolve("workflow-info.json");
}
