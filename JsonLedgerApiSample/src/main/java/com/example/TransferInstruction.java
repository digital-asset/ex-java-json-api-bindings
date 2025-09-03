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

import com.daml.ledger.javaapi.data.codegen.DamlRecord;
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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;

public class TransferInstruction {

    private final DefaultApi transferInstructionApi;

    public TransferInstruction(String transferInstructionBaseUrl) {

        ApiClient client = new ApiClient();
        client.setBasePath(transferInstructionBaseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds

        this.transferInstructionApi = new DefaultApi(client);
    }

    private Object jsonPayload(/*TransferFactory_Transfer payload*/ String rawJson) {
        Gson generalGson = new Gson();
        return generalGson.fromJson(rawJson, LinkedHashMap.class);
    }

    public TransferFactoryWithChoiceContext getTransferFactory(
            String admin,
            String sender,
            String receiver,
            BigDecimal amount,
            InstrumentId instrumentId,
            Instant requestedAt,
            Instant executeBefore,
            List<Holding.ContractId> inputHoldingCids
    ) throws ApiException {

        Metadata emptyMetadata = new Metadata(new HashMap<>());
        ChoiceContext noContext = new ChoiceContext(new HashMap<>());
        ExtraArgs blankExtraArgs = new ExtraArgs(noContext, emptyMetadata);

        Transfer transfer = new Transfer(sender, receiver, amount, instrumentId, requestedAt, executeBefore, inputHoldingCids, emptyMetadata);
        TransferFactory_Transfer choiceToSend = new TransferFactory_Transfer(admin, transfer, blankExtraArgs);

        GetFactoryRequest request = new GetFactoryRequest();
        /*
         TODO: openAPI's JSON encoder doesn't know how to encode Instant instances, instead it crashes with:

         Failed making field 'java.time.Instant#seconds' accessible; either increase its visibility or write a custom TypeAdapter for its declaring type.
         See https://github.com/google/gson/blob/main/Troubleshooting.md#reflection-inaccessible

         For now, we work around this issue by using teh provided toJson methods to make a raw Json string, and then
         parse the JSON string to a semi-structured LinkedHashMap (so that the string output of toJson is not then re-encoded as a single JSON string).

         request.setChoiceArguments(choiceToSend);
         */
        request.setChoiceArguments(jsonPayload(choiceToSend.toJson()));

        System.out.println("\nget transfer factory request: " + request.toJson() + "\n");
        TransferFactoryWithChoiceContext response = this.transferInstructionApi.getTransferFactory(request);
        System.out.println("\nget transfer factory response: " + response.toJson() + "\n");

        return response;
    }
}
