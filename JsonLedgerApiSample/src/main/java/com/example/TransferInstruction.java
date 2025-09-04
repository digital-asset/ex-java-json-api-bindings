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

package com.example;

import com.example.GsonTypeAdapters.ContractIdTypeAdapterFactory;
import com.example.GsonTypeAdapters.InstantTypeAdapter;
import com.example.GsonTypeAdapters.OffsetDateTimeTypeAdapter;
import com.example.client.transferInstruction.api.DefaultApi;
import com.example.client.transferInstruction.invoker.ApiClient;
import com.example.client.transferInstruction.invoker.ApiException;
import com.example.client.transferInstruction.model.GetFactoryRequest;
import com.example.client.transferInstruction.model.TransferFactoryWithChoiceContext;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import splice.api.token.holdingv1.Holding;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.metadatav1.ChoiceContext;
import splice.api.token.metadatav1.ExtraArgs;
import splice.api.token.metadatav1.Metadata;
import splice.api.token.transferinstructionv1.Transfer;
import splice.api.token.transferinstructionv1.TransferFactory_Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.HashMap;
import java.util.List;

public class TransferInstruction {

    private final DefaultApi transferInstructionApi;

    public TransferInstruction(String transferInstructionBaseUrl) {

        ApiClient client = new ApiClient();
        client.setBasePath(transferInstructionBaseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds

        // JSON serialization compatibility with Open API payloads
        Gson customGson = new GsonBuilder()
                .registerTypeAdapterFactory(new ContractIdTypeAdapterFactory())
                .registerTypeAdapter(Instant.class, new InstantTypeAdapter())
                .registerTypeAdapter(OffsetDateTime.class, new OffsetDateTimeTypeAdapter())
                .create();

        client.getJSON().setGson(customGson);

        this.transferInstructionApi = new DefaultApi(client);
    }

    public TransferFactoryWithChoiceContext getTransferFactory(
            TransferFactory_Transfer choiceToSend
    ) throws ApiException {

        GetFactoryRequest request = new GetFactoryRequest().choiceArguments(choiceToSend);

        System.out.println("\nget transfer factory request: " + request.toJson() + "\n");
        TransferFactoryWithChoiceContext response = this.transferInstructionApi.getTransferFactory(request);
        System.out.println("\nget transfer factory response: " + response.toJson() + "\n");

        return response;
    }
}
