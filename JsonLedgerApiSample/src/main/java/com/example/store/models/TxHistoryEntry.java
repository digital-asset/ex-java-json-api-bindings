package com.example.store.models;

import com.example.client.ledger.model.Event;
import jakarta.annotation.Nonnull;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.transferinstructionv1.TransferInstruction;
import splice.api.token.transferinstructionv1.TransferInstructionView;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

// FIXME: docs

/**
 * An entry in the transaction history log that explains the reason for a transaction affecting the treasury party's holdings.
 *
 * @param updateMetadata             metadata of the update containing the transaction
 * @param exerciseNodeId             the root node of the transaction
 * @param treasuryHoldingChanges     the list of holdings created or archived for the treasury party as part of this transaction
 * @param transferInstructionChanges the changes to the set of pending transfer instructions as part of this transaction
 * @param transactionEvents          the events of the (sub)transaction, starting with the exercise node event. These are included for debugging only.
 *                                   In particular, to understand unrecognized choices; and to get a feel for how the actual transactions look like.
 */
// TODO: consider merging the treasuryHoldingChanges and transferInstructionChanges into a single list of Utxo changes
public record TxHistoryEntry(
        @Nonnull UpdateMetadata updateMetadata,
        @Nonnull long exerciseNodeId,
        Transfer transfer,
        List<String> validationErrors,
        @Nonnull List<HoldingChange> treasuryHoldingChanges,
        // FIXME: pendingTransferInstructionChanges would be a better name
        @Nonnull List<TransferInstructionChange> transferInstructionChanges,
        // FIXME: capture full list events, switch parsing to not use iterators
        @Nonnull List<Event> transactionEvents
) {

    public static Optional<Transfer> tryMkTransfer(
            @Nonnull String treasuryPartyId,
            @Nonnull String senderPartyId,
            @Nonnull String receiverPartyId,
            @Nonnull TransferDetails details) {
        TransferKind kind = null;
        if (senderPartyId.equals(treasuryPartyId)) {
            if (receiverPartyId.equals(treasuryPartyId)) {
                kind = TransferKind.SPLIT_MERGE;
            } else {
                kind = TransferKind.TRANSFER_OUT;
            }
        } else if (receiverPartyId.equals(treasuryPartyId)) {
            kind = TransferKind.TRANSFER_IN;
        }
        if (kind == null) {
            return Optional.empty();
        } else {
            return Optional.of(new Transfer(senderPartyId, receiverPartyId, kind, details));
        }
    }

    public enum TransferStatus {COMPLETED, PENDING, REJECTED, WITHDRAWN, FAILED_OTHER}

    public enum TransferKind {TRANSFER_IN, TRANSFER_OUT, SPLIT_MERGE}

    public TxHistoryEntry(
            @Nonnull UpdateMetadata updateMetadata,
            @Nonnull long exerciseNodeId,
            Transfer transfer,
            List<String> validationErrors,
            @Nonnull List<HoldingChange> treasuryHoldingChanges,
            @Nonnull List<TransferInstructionChange> transferInstructionChanges,
            @Nonnull List<Event> transactionEvents)  {
        if (treasuryHoldingChanges.isEmpty() && transferInstructionChanges.isEmpty()) {
            throw new IllegalArgumentException("Not both of treasuryHoldingChanges and transferInstructionChanges can be empty");
        }
        this.updateMetadata = updateMetadata;
        this.exerciseNodeId = exerciseNodeId;
        this.transfer = transfer;
        this.validationErrors = validationErrors.isEmpty() ? null : validationErrors;
        this.treasuryHoldingChanges = treasuryHoldingChanges;
        this.transferInstructionChanges = transferInstructionChanges;
        this.transactionEvents = transactionEvents;
    }


    public record UpdateMetadata(
            @Nonnull
            String updateId,
            @Nonnull
            String recordTime,
            long offset) {
    }

    public record TransferDetails(
            @Nonnull
            String memoTag,
            @Nonnull
            InstrumentId instrumentId,
            @Nonnull
            BigDecimal amount,
            // FIXME: track on-ledger correlation id
            TransferStatus transferStatus,
            TransferInstruction.ContractId pendingInstructionCid) {
    }

    public record Transfer(
            @Nonnull
            String senderPartyId,
            @Nonnull
            String receiverPartyId,
            @Nonnull
            TransferKind kind,
            @Nonnull TransferDetails details
    ) {
    }

    public record HoldingChange(
            @Nonnull String contractId,
            @Nonnull HoldingView holding,
            boolean archived
    ) {
    }

    /**
     * A change to the set of pending transfer instructions
     */
    public record TransferInstructionChange(
            @Nonnull String contractId,
            @Nonnull TransferInstructionView transferInstruction,
            boolean archived
    ) {
    }

}