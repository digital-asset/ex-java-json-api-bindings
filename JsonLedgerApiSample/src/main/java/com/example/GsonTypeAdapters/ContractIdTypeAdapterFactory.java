package com.example.GsonTypeAdapters;

import com.daml.ledger.javaapi.data.codegen.ContractId;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;

public class ContractIdTypeAdapterFactory implements TypeAdapterFactory {

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (!ContractId.class.isAssignableFrom(typeToken.getRawType())) {
            return null; // Let Gson handle other types
        }

        TypeAdapter<ContractId> contractIdAdapter = new TypeAdapter<ContractId>() {
            @Override
            public void write(JsonWriter out, ContractId value) throws IOException {
                out.value(value.contractId);
            }

            @Override
            public ContractId read(JsonReader in) throws IOException {
                return new ContractId<>(in.nextString());
            }
        };

        // We cast the adapter to the generic TypeAdapter<T>
        return (TypeAdapter<T>) contractIdAdapter;
    }
}

