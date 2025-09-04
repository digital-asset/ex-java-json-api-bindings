/*
 * Copyright (c) 2025, by Digital Asset
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

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

