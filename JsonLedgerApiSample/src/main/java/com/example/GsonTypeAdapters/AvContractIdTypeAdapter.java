package com.example.GsonTypeAdapters;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import splice.api.token.metadatav1.AnyContract;
import splice.api.token.metadatav1.anyvalue.AV_ContractId;

import java.io.IOException;
import java.time.Instant;

public class AvContractIdTypeAdapter extends TypeAdapter<AV_ContractId> {

    @Override
    public void write(JsonWriter out, AV_ContractId value) throws IOException {
        out.beginObject();
        out.name("tag"); out.value("AV_ContractId");
        out.name("value"); out.value(value.contractIdValue.contractId);
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
