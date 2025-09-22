package com.example.access;

import com.example.GsonTypeAdapters.GsonSingleton;
import com.example.signing.Keys;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

import java.security.KeyPair;
import java.util.Optional;

public record ExternalParty(
    String partyId,
    KeyPair keyPair
) {
}
