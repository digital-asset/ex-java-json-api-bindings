package com.example.access;

import java.security.KeyPair;

public record ExternalParty(
        String partyId,
        KeyPair keyPair
) {
}
