package com.example.GsonTypeAdapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.google.gson.JsonIOException;

import java.io.*;
        import java.security.KeyPair;
import java.util.Base64;

/** Hacky version that uses Java serialization to serialize/deserialize KeyPair objects.
 *
 * Use for testing only, not for production code.
 */
public class KeyPairTypeAdapter extends TypeAdapter<KeyPair> {

    @Override
    public void write(JsonWriter out, KeyPair value) throws IOException {
        if (value == null) {
            out.nullValue();
            return;
        }
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream();
             ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(value);
            String base64 = Base64.getEncoder().encodeToString(baos.toByteArray());
            out.value(base64);
        } catch (IOException e) {
            throw new JsonIOException("Failed to serialize KeyPair", e);
        }
    }

    @Override
    public KeyPair read(JsonReader in) throws IOException {
        if (in.peek() == com.google.gson.stream.JsonToken.NULL) {
            in.nextNull();
            return null;
        }
        String base64 = in.nextString();
        try (ByteArrayInputStream bais = new ByteArrayInputStream(Base64.getDecoder().decode(base64));
             ObjectInputStream ois = new ObjectInputStream(bais)) {
            return (KeyPair) ois.readObject();
        } catch (ClassNotFoundException e) {
            throw new IOException("Failed to deserialize KeyPair", e);
        }
    }
}
