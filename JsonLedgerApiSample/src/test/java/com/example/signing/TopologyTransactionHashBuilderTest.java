package com.example.signing;

import com.daml.ledger.api.v2.interactive.InteractiveSubmissionServiceOuterClass;
import com.example.services.Ledger;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

class TopologyTransactionHashBuilderTest {

    void testFromBase64Payload(String expectedOutputBase64, List<String> inputs) throws Exception {
        byte[] expectedOutputRaw = Encode.fromBase64String(expectedOutputBase64);
        byte[] actualOutputRaw = new TopologyHashBuilder(inputs).hash();

        if (!Arrays.equals(expectedOutputRaw, actualOutputRaw)) {
            String base64ComputedHash = Encode.toBase64String(actualOutputRaw);
            throw new IllegalStateException("Topology hash mismatch: %s (provided) vs %s (computed) for topology tranasctions [%s]"
                    .formatted(expectedOutputBase64, base64ComputedHash, String.join(", ", inputs)));
        }
    }

    @Test
    void createTreasuryExample() throws Exception {
        List<String> inputs = new ArrayList<>();
        inputs.add("CosBCAEQARqEAQqBAQpEMTIyMDllYzljMTQxY2M3MGZiNjRiOTg3M2EyZWY3ZGQ3NmY5YjUxYzFhOWE5MjA3ZmJmMmI3NjhlMDQwODdkZDQyNWISNxAEGiwwKjAFBgMrZXADIQBhJWW3y52wD+i3fB7JVAb10zZK+ViNw4SbjT8G8MdYWyoDAQUEMAEiABAe");
        inputs.add("CpYBCAEQARqPAYIBiwEKTnRyZWFzdXJ5OjoxMjIwOWVjOWMxNDFjYzcwZmI2NGI5ODczYTJlZjdkZDc2ZjliNTFjMWE5YTkyMDdmYmYyYjc2OGUwNDA4N2RkNDI1YhgBIjcQBBosMCowBQYDK2VwAyEAYSVlt8udsA/ot3weyVQG9dM2SvlYjcOEm40/BvDHWFsqAwEFBDABEB4=");
        inputs.add("CrMBCAEQARqsAUqpAQpOdHJlYXN1cnk6OjEyMjA5ZWM5YzE0MWNjNzBmYjY0Yjk4NzNhMmVmN2RkNzZmOWI1MWMxYTlhOTIwN2ZiZjJiNzY4ZTA0MDg3ZGQ0MjViEAEaVQpRcGFydGljaXBhbnQ6OjEyMjAwMTI2ODFiMTVlYTdiMjY5MGNhY2JjMTYzNjY0YTZkZmIwOTZhN2UwNmJhNmVkZjdjZDA0ODBmOWU2ODVjMmQzEAIQHg==");

        testFromBase64Payload("EiCcllZgT/EMojkOyBRdWOOvNYVQ0WDv+Ri+V44wShs86A==", inputs);
    }

    @Test
    void createSenderExample() throws Exception {
        List<String> inputs = new ArrayList<>();
        inputs.add("CosBCAEQARqEAQqBAQpEMTIyMGNiOGY4ZTUzMjE0MTNhMWU4N2E4ZWYyMzc5ZWY2MDE1ODgxMDJiZGZhZWU3MDRlZjkzNTY3MGNhNzg0OTA2MGISNxAEGiwwKjAFBgMrZXADIQByPvwpZaxNOr75Ny9n+SlpU+qCMIwIDaE5+OX0giLG8yoDAQUEMAEiABAe");
        inputs.add("CpMBCAEQARqMAYIBiAEKS2FsaWNlOjoxMjIwY2I4ZjhlNTMyMTQxM2ExZTg3YThlZjIzNzllZjYwMTU4ODEwMmJkZmFlZTcwNGVmOTM1NjcwY2E3ODQ5MDYwYhgBIjcQBBosMCowBQYDK2VwAyEAcj78KWWsTTq++TcvZ/kpaVPqgjCMCA2hOfjl9IIixvMqAwEFBDABEB4=");
        inputs.add("CrABCAEQARqpAUqmAQpLYWxpY2U6OjEyMjBjYjhmOGU1MzIxNDEzYTFlODdhOGVmMjM3OWVmNjAxNTg4MTAyYmRmYWVlNzA0ZWY5MzU2NzBjYTc4NDkwNjBiEAEaVQpRcGFydGljaXBhbnQ6OjEyMjAwMTI2ODFiMTVlYTdiMjY5MGNhY2JjMTYzNjY0YTZkZmIwOTZhN2UwNmJhNmVkZjdjZDA0ODBmOWU2ODVjMmQzEAIQHg==");

        testFromBase64Payload("EiCXrO2MQ9u8uB/po8TmrI820qJtQFVLx8tZXsjJ/Shwag==", inputs);
    }
}