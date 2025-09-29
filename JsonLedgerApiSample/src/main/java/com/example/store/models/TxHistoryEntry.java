package com.example.store.models;

import com.example.client.ledger.model.Event;
import jakarta.annotation.Nonnull;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.transferinstructionv1.TransferInstructionStatus;
import splice.api.token.transferinstructionv1.TransferInstructionView;

import java.math.BigDecimal;
import java.util.List;

/**
 * An entry in the transaction history log that explains the reason for a transaction affecting the treasury party's holdings.
 *
 * @param updateMetadata         metadata of the update containing the transaction
 * @param exerciseNodeId         the root node of the transaction
 * @param kind                   the kind of transaction, e.g. "TransferIn", "TransferOut", "SplitMerge", "UnrecognizedChoice", "BareCreate"
 * @param details                the details parsed from the transaction
 * @param treasuryHoldingChanges the list of holdings created or archived for the treasury party as part of this transaction
 * @param transactionEvents      the events of the (sub)transaction, starting with the exercise node event. These are included for debugging only.
 *                               In particular, to understand unrecognized choices; and to get a feel for how the actual transactions look like.
 */
public record TxHistoryEntry(
        @Nonnull UpdateMetadata updateMetadata,
        @Nonnull long exerciseNodeId,
        // The below is a hack to make the JSON output contains the kind of label. It does though not work for JSON decoding.
        // TODO: use a better JSON encoding to tag the kind of label, and enable decoding
        @Nonnull String kind,
        @Nonnull Label details,
        @Nonnull List<HoldingChange> treasuryHoldingChanges,
        @Nonnull List<Event> transactionEvents
) {

    public enum TransferStatus { COMPLETED, PENDING, FAILED };

    public TxHistoryEntry {
        if (treasuryHoldingChanges.isEmpty()) {
            throw new IllegalArgumentException("treasuryHoldingChanges cannot be empty");
        }
    }

    public TxHistoryEntry(
            @Nonnull UpdateMetadata updateMetadata,
            @Nonnull long exerciseNodeId,
            @Nonnull Label label,
            @Nonnull List<HoldingChange> treasuryHoldingChanges,
            @Nonnull List<Event> subtransaction) {
        this(
                updateMetadata,
                exerciseNodeId,
                label.getClass().getSimpleName(),
                label,
                treasuryHoldingChanges,
                subtransaction);
    }

    public record UpdateMetadata(
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
            BigDecimal amount,
            // FIXME
            TransferInstructionStatus pending) implements Label {
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
            BigDecimal amount,
            TransferStatus transferStatus,
            TransferInstructionView pendingInstruction) implements Label {
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