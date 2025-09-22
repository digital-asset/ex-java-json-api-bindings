package com.example.models;

import com.example.ConversionHelpers;
import com.example.GsonTypeAdapters.GsonSingleton;
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

public class TokenStandard {

    public static TransferFactory_Transfer makeProposedTransfer(
            String senderPartyId,
            String receiverPartyId,
            BigDecimal amount,
            InstrumentId instrumentId,
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

        Transfer transfer = new Transfer(senderPartyId, receiverPartyId, amount, instrumentId, requestedAt, executeBefore, holdingCids, emptyMetadata);
        return new TransferFactory_Transfer(instrumentId.admin, transfer, blankExtraArgs);
    }

    public static TransferFactory_Transfer resolveProposedTransfer(
            TransferFactory_Transfer proposed,
            TransferFactoryWithChoiceContext fromApi
    ) {
        Metadata emptyMetadata = new Metadata(new HashMap<>());

        // ChoiceContext from the transfer OpenAPI != ChoiceContext generated from the transfer DAR
        String choiceJson = GsonSingleton.getInstance().toJson(fromApi.getChoiceContext().getChoiceContextData());
        ChoiceContext choiceContextFromApi = ConversionHelpers.useValueParser(choiceJson, ChoiceContext::fromJson);

        ExtraArgs populatedExtraArgs = new ExtraArgs(choiceContextFromApi, emptyMetadata);
        return new TransferFactory_Transfer(proposed.expectedAdmin, proposed.transfer, populatedExtraArgs);
    }
}
