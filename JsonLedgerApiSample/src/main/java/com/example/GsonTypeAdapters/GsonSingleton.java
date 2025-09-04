package com.example.GsonTypeAdapters;

import com.example.client.ledger.model.Command;
import com.example.client.ledger.model.IdentifierFilter;
import com.example.client.ledger.model.JsContractEntry;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import splice.api.token.metadatav1.anyvalue.AV_ContractId;

import java.time.Instant;
import java.time.OffsetDateTime;

public class GsonSingleton {

    private static Gson sharedInstance;

    // JSON serialization compatibility with Open API payloads
    public static Gson getInstance() {
        if (sharedInstance == null) {
            sharedInstance = new GsonBuilder()
                    .registerTypeAdapter(AV_ContractId.class, new AvContractIdTypeAdapter())
                    .registerTypeAdapterFactory(new ContractIdTypeAdapterFactory())
                    .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                    .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeTypeAdapter())
                    .registerTypeAdapterFactory(new IdentifierFilter.CustomTypeAdapterFactory())
                    .registerTypeAdapterFactory(new JsContractEntry.CustomTypeAdapterFactory())
                    .registerTypeAdapterFactory(new Command.CustomTypeAdapterFactory())
                    .create();
        }

        return sharedInstance;
    }
}
