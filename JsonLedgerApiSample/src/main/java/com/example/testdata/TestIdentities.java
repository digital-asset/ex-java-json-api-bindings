package com.example.testdata;

import com.example.access.ExternalParty;
import splice.api.token.holdingv1.InstrumentId;

import java.util.List;

public record TestIdentities(
        String synchronizerId,
        String dsoPartyId,
        String exchangePartyId,
        ExternalParty treasury,
        ExternalParty alice
) {
    public List<ExternalParty> all() {
        return List.of(treasury, alice);
    }

    public InstrumentId cantonCoinId() {
        return new InstrumentId(dsoPartyId, "Amulet");
    }
}
