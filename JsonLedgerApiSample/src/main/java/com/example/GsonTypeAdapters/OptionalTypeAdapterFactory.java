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
import com.google.common.reflect.TypeParameter;
import com.google.gson.Gson;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public class OptionalTypeAdapterFactory implements TypeAdapterFactory {

    private static class OptionalTypeAdapter<X> extends TypeAdapter<Optional<X>> {
        private TypeAdapter<X> adapter;
        public OptionalTypeAdapter(TypeAdapter<X> adapter) {
            this.adapter = adapter;
        }

        public void write(JsonWriter out, Optional<X> value) throws IOException {
            if (value.isEmpty()) {
                out.nullValue();
            } else {
                adapter.write(out, value.get());
            }
        }

        @Override
        public Optional<X> read(JsonReader in) throws IOException {
            JsonToken next = in.peek();
            if (next == JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            } else {
                X value = adapter.read(in);
                return Optional.of(value);
            }
        }
    }

    @Override
    public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> typeToken) {
        if (!Optional.class.isAssignableFrom(typeToken.getRawType())) {
            return null; // Let Gson handle other types
        }

        ParameterizedType typeOfOptionalT = (ParameterizedType) typeToken.getType();
        Type t = typeOfOptionalT.getActualTypeArguments()[0];
        TypeAdapter<?> adapter = gson.getAdapter(TypeToken.get(t));

        return new OptionalTypeAdapter(adapter);
    }
}

