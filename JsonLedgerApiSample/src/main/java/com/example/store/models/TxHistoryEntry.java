package com.example.store.models;

import com.example.client.ledger.model.Event;
import jakarta.annotation.Nonnull;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.util.List;

// FIXME: javadoc
public record TxHistoryEntry(
        @Nonnull TxMetadata txMetadata,
        @Nonnull long exerciseNodeId,
        // The below is a hack to make the JSON output contains the kind of label. It does though not work for JSON decoding.
        // TODO: use a better JSON encoding to tag the kind of label, and enable decoding
        @Nonnull String labelKind,
        @Nonnull Label labelData,
        @Nonnull List<HoldingChange> treasuryHoldingChanges,
        @Nonnull List<Event> subtransaction
) {

    public TxHistoryEntry {
        if (treasuryHoldingChanges.isEmpty()) {
            throw new IllegalArgumentException("treasuryHoldingChanges cannot be empty");
        }
    }

    public TxHistoryEntry(
            @Nonnull TxMetadata txMetadata,
            @Nonnull long exerciseNodeId,
            @Nonnull Label label,
            @Nonnull List<HoldingChange> treasuryHoldingChanges,
            @Nonnull List<Event> subtransaction) {
        this(
                txMetadata,
                exerciseNodeId,
                label.getClass().getSimpleName(),
                label,
                treasuryHoldingChanges,
                subtransaction);
    }

    public record TxMetadata(
            @Nonnull
            String updateId,
            @Nonnull
            String recordTime,
            long offset) {
    }

    public sealed interface Label permits TransferIn, TransferOut, SplitMerge, UnrecognizedChoice, BareCreate {
        /**
         * True if this is a recognized label, false if it is a fallback for an unrecognized choice or bare create
         */
        boolean isRecognized();
    }

    public record TransferIn(
            @Nonnull
            String senderPartyId,
            @Nonnull String memoTag,
            @Nonnull
            InstrumentId instrumentId,
            @Nonnull
            BigDecimal amount) implements Label {
        @Override
        public boolean isRecognized() {
            return true;
        }
    }

    public record TransferOut(
            @Nonnull String receiverPartyId,
            @Nonnull String memoTag,
            @Nonnull
            InstrumentId instrumentId,
            @Nonnull
            BigDecimal amount) implements Label {
        @Override
        public boolean isRecognized() {
            return true;
        }
    }

    public record SplitMerge(
            @Nonnull InstrumentId instrumentId
    ) implements Label {
        @Override
        public boolean isRecognized() {
            return true;
        }
    }

    public record UnrecognizedChoice(
            @Nonnull String packageName,
            @Nonnull String templateId,
            @Nonnull String choiceName) implements Label {
        @Override
        public boolean isRecognized() {
            return false;
        }
    }

    public record BareCreate(
            @Nonnull String templateName) implements Label {
        @Override
        public boolean isRecognized() {
            return false;
        }
    }

    public record HoldingChange(
            @Nonnull String contractId,
            @Nonnull HoldingView holding,
            boolean archived
    ) {
    }


}