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

package com.example.models;

import com.example.ConversionHelpers;
import com.example.client.transferInstruction.invoker.JSON;
import com.example.client.transferInstruction.model.TransferFactoryWithChoiceContext;
import splice.api.token.holdingv1.Holding;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.metadatav1.ChoiceContext;
import splice.api.token.metadatav1.ExtraArgs;
import splice.api.token.metadatav1.Metadata;
import splice.api.token.transferinstructionv1.Transfer;
import splice.api.token.transferinstructionv1.TransferFactory_Transfer;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TokenStandard {

    public final static String MEMO_KEY = "splice.lfdecentralizedtrust.org/reason";

    public static TransferFactory_Transfer makeProposedTransfer(
            String senderPartyId,
            String receiverPartyId,
            BigDecimal amount,
            InstrumentId instrumentId,
            Optional<String> memoTag,
            Map<String, String> otherTransferMetadata,
            Instant requestedAt,
            Instant executeBefore,
            List<ContractAndId<HoldingView>> holdings) {

        List<Holding.ContractId> holdingCids = holdings
                .stream()
                .map((h) -> new Holding.ContractId(h.contractId()))
                .toList();

        Metadata emptyMetadata = new Metadata(new HashMap<>());
        ChoiceContext noContext = new ChoiceContext(new HashMap<>());
        ExtraArgs blankExtraArgs = new ExtraArgs(noContext, emptyMetadata);

        memoTag.ifPresent(s -> otherTransferMetadata.put(MEMO_KEY, s));
        Metadata transferMetadata = new Metadata(otherTransferMetadata);

        Transfer transfer = new Transfer(senderPartyId, receiverPartyId, amount, instrumentId, requestedAt, executeBefore, holdingCids, transferMetadata);
        return new TransferFactory_Transfer(instrumentId.admin, transfer, blankExtraArgs);
    }

    public static TransferFactory_Transfer resolveProposedTransfer(
            TransferFactory_Transfer proposed,
            TransferFactoryWithChoiceContext fromApi
    ) {
        Metadata emptyMetadata = new Metadata(new HashMap<>());

        // ChoiceContext from the transfer OpenAPI != ChoiceContext generated from the transfer DAR
        String choiceJson = JSON.getGson().toJson(fromApi.getChoiceContext().getChoiceContextData());
        ChoiceContext choiceContextFromApi = ConversionHelpers.useValueParser(choiceJson, ChoiceContext::fromJson);

        ExtraArgs populatedExtraArgs = new ExtraArgs(choiceContextFromApi, emptyMetadata);
        return new TransferFactory_Transfer(proposed.expectedAdmin, proposed.transfer, populatedExtraArgs);
    }
}
