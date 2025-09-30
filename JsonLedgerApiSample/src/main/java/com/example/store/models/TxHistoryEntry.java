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
import java.util.SortedMap;

/**
 * An entry in the transaction history log that explains the reason for a transaction affecting the treasury party's holdings
 * or the set of pending transfer instructions involving the treasury party.
 *
 * @param updateMetadata                    metadata of the update containing the transaction
 * @param exerciseNodeId                    the root node of the transaction
 * @param transfer                          the transfer that this transaction represents, if recognized as such
 * @param unrecognized                      if the transaction was not recognized as a transfer, the reason why
 * @param treasuryHoldingChanges            the changes to the set of holdings of the treasury party that happened as part of this transaction
 * @param pendingTransferInstructionChanges the changes to the set of pending transfer instructions that happened as part of this transaction
 * @param transactionEvents                 the events of the (sub)transaction, starting with the exercise node event. These are included for debugging only.
 *                                          In particular, to understand unrecognized choices; and to get a feel for how the actual transactions look like.
 */
public record TxHistoryEntry(
        @Nonnull UpdateMetadata updateMetadata,
        @Nonnull long exerciseNodeId,
        Transfer transfer,
        Unrecognized unrecognized,
        @Nonnull List<HoldingChange> treasuryHoldingChanges,
        @Nonnull List<TransferInstructionChange> pendingTransferInstructionChanges,
        // FIXME: capture full list events, switch parsing to not use iterators
        @Nonnull List<Event> transactionEvents
) {


    public TxHistoryEntry {
        if (treasuryHoldingChanges.isEmpty() && pendingTransferInstructionChanges.isEmpty()) {
            throw new IllegalArgumentException("Not both of treasuryHoldingChanges and pendingTransferInstructionChanges can be empty");
        }
    }

    public record UpdateMetadata(
            @Nonnull
            String updateId,
            @Nonnull
            String recordTime,
            long offset) {
    }


    /**
     * The status of a recognized transfer. All states other than 'PENDING' are final.
     */
    public enum TransferStatus {PENDING, COMPLETED, REJECTED, WITHDRAWN, FAILED_OTHER}

    /**
     * Details of a recognized transfer
     *
     * @param memoTag                the memo tag associated with the transfer, empty if none was provided
     * @param instrumentId           the instrument ID of the token being transferred
     * @param amount                 the amount of tokens being transferred
     * @param transferStatus         the status of the transfer
     * @param multiStepCorrelationId an on-ledger correlation ID for multi-step transfers
     * @param pendingInstructionCid  the contract ID of the pending transfer instruction created for this transfer, if any
     */
    public record TransferDetails(
            @Nonnull
            String memoTag,
            @Nonnull
            InstrumentId instrumentId,
            @Nonnull
            BigDecimal amount,
            TransferStatus transferStatus,
            String multiStepCorrelationId,
            TransferInstruction.ContractId pendingInstructionCid) {
    }

    /**
     * The kind of transfer with respect to the treasury party
     */
    public enum TransferKind {TRANSFER_IN, TRANSFER_OUT, TRANSFER_SELF}

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

    public enum UnrecognizedKind {BARE_CREATE, UNRECOGNIZED_CHOICE}

    /**
     * If a transaction was not recognized as a transfer, the reason why
     *
     * @param kind    the kind of unrecognized transaction
     * @param details additional details about the unrecognized transaction, e.g., the choice name for UNRECOGNIZED_CHOICE
     */
    public record Unrecognized(
            @Nonnull UnrecognizedKind kind,
            @Nonnull SortedMap<String, String> details
    ) {
    }

    /**
     * A change to the set of holdings of the treasury party
     */
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

    public static Optional<Transfer> tryMkTransfer(
            @Nonnull String treasuryPartyId,
            @Nonnull String senderPartyId,
            @Nonnull String receiverPartyId,
            @Nonnull TransferDetails details) {
        TransferKind kind = null;
        if (senderPartyId.equals(treasuryPartyId)) {
            if (receiverPartyId.equals(treasuryPartyId)) {
                kind = TransferKind.TRANSFER_SELF;
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
}