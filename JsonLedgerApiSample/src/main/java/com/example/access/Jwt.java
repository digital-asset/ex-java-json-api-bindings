package com.example.access;

import com.example.GsonTypeAdapters.GsonSingleton;
import com.example.signing.Encode;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonSyntaxException;

public class Jwt {
    JsonObject header;
    JsonObject payload;

    private Jwt(JsonObject header, JsonObject payload) {
        this.header = header;
        this.payload = payload;
    }

    public static Jwt fromString(String raw) {

        String[] parts = raw.split("\\.");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Was not able to decode JWT " + raw);
        }

        JsonObject header;
        try {
            String decodedJsonHeader = new String(Encode.fromBase64String(parts[0]));
            header = GsonSingleton.getInstance().fromJson(decodedJsonHeader, JsonObject.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException("Failed to parse JWT header as JSON", ex);
        }

        JsonObject payload;
        try {
            String decodedJsonPayload = new String(Encode.fromBase64String(parts[1]));
            payload = GsonSingleton.getInstance().fromJson(decodedJsonPayload, JsonObject.class);
        } catch (JsonSyntaxException ex) {
            throw new IllegalArgumentException("Failed to parse JWT payload as JSON", ex);
        }

        return new Jwt(header, payload);
    }

    public String readSubject() {
        JsonElement subjectElement = payload.get("sub");
        if (subjectElement == null) {
            return null;
        }

        return subjectElement.getAsString();
    }

    public String readIssuer() {
        JsonElement issuerElement = payload.get("iss");
        if (issuerElement == null) {
            return null;
        }

        return issuerElement.getAsString();
    }
}
