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

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import splice.api.token.metadatav1.AnyContract;
import splice.api.token.metadatav1.anyvalue.AV_ContractId;

import java.io.IOException;

public class AvContractIdTypeAdapter extends TypeAdapter<AV_ContractId> {

    @Override
    public void write(JsonWriter out, AV_ContractId value) throws IOException {
        out.beginObject();
        out.name("tag");
        out.value("AV_ContractId");
        out.name("value");
        out.value(value.contractIdValue.contractId);
        out.endObject();
    }

    @Override
    public AV_ContractId read(JsonReader in) throws IOException {
        in.beginObject();
        String raw = null;
        while (in.hasNext()) {
            String name = in.nextName();
            if ("value".equals(name)) {
                raw = in.nextString();
            } else {
                in.skipValue();
            }
        }

        in.endObject();
        return new AV_ContractId(new AnyContract.ContractId(raw));
    }
}
