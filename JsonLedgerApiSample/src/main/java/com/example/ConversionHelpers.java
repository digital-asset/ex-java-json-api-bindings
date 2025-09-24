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
import com.example.client.ledger.model.JsActiveContract;
import com.example.client.ledger.model.JsContractEntry;
import com.example.client.ledger.model.JsContractEntryOneOf;
import com.example.client.ledger.model.JsInterfaceView;
import com.example.models.ContractAndId;
import com.example.models.TemplateId;

import java.io.IOException;
import java.util.List;

public class ConversionHelpers {

    public static <T> T convertFromJson(
            String json,
            JsonDecoder<T> fromJson) {
        try {
            return fromJson.decode(json);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot decode " + json, ex);
        }
    }

    public static <U, V> V convertViaJson(
            U payload,
            JsonEncoder<U> encoder,
            JsonDecoder<V> decoder
    ) {
        String json = "";
        try {
            json = encoder.encode(payload);
        } catch (IOException ex) {
            throw new IllegalArgumentException("Cannot encode " + payload.toString(), ex);
        }
        return convertFromJson(json, decoder);
    }

    public static <T extends DamlRecord<T>> ContractAndId<T> fromInterface(
            JsContractEntry contractEntry,
            TemplateId interfaceId,
            JsonDecoder<T> decoder
    ) {
        Object entryInstance = contractEntry.getActualInstance();
        if (!(entryInstance instanceof JsContractEntryOneOf)) {
            return null;
        }
        JsActiveContract activeContract = ((JsContractEntryOneOf) entryInstance).getJsActiveContract();
        String instanceContractId = activeContract.getCreatedEvent().getContractId();

        List<JsInterfaceView> interfaceViews = activeContract.getCreatedEvent().getInterfaceViews();
        if (interfaceViews == null) return null;

        T record = interfaceViews
                .stream()
                .filter(v -> interfaceId.matchesModuleAndTypeName(v.getInterfaceId()))
                .map(v -> convertViaJson(v.getViewValue(), com.example.client.ledger.invoker.JSON.getGson()::toJson, decoder))
                .findFirst()
                .orElseThrow();
        return new ContractAndId<>(instanceContractId, record);
    }

    public interface JsonDecoder<T> {
        T decode(String input) throws IOException;
    }

    public interface JsonEncoder<T> {
        String encode(T input) throws IOException;
    }
}
