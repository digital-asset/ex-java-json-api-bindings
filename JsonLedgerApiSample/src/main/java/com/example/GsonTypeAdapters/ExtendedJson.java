package com.example.GsonTypeAdapters;

import com.example.client.ledger.invoker.JSON;
import com.google.gson.Gson;
import splice.api.token.metadatav1.anyvalue.AV_ContractId;

import java.security.KeyPair;
import java.time.Instant;

/**
 * Extended version of JSON to support custom types.
 */
public class ExtendedJson {
    public static final Gson gson =
            JSON.getGson().newBuilder()
                    .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                    .registerTypeAdapterFactory(new OptionalTypeAdapterFactory())
                    .registerTypeAdapter(AV_ContractId.class, new AvContractIdTypeAdapter())
                    .registerTypeAdapterFactory(new ContractIdTypeAdapterFactory())
                    .registerTypeAdapter(KeyPair.class, new KeyPairTypeAdapter())
                    .create();

    public static final Gson gsonPretty = gson.newBuilder().setPrettyPrinting().create();
}
