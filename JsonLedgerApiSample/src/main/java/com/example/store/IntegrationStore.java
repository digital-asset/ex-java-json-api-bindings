package com.example.store;

import com.example.GsonTypeAdapters.ExtendedJson;
import com.example.client.ledger.model.*;
import com.example.store.models.TxHistoryEntry;
import org.jetbrains.annotations.NotNull;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.transferinstructionv1.Transfer;
import splice.api.token.transferinstructionv1.TransferInstructionView;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Logger;

/**
 * In-memory store for tracking holdings and pending transfer instructions for the treasury party.
 * <p>
 * This serves as a sample implementation of the core of the UTXO management that an exchange integration
 * needs to implement in the Canton Integration DB, see
 * https://docs.digitalasset.com/integrate/devnet/exchange-integration/architecture.html#canton-integration-components
 * <p>
 * The store tracks:
 * - The set of active holdings owned by the treasury party
 * - The set of pending transfer instructions where the treasury party is either sender or receiver
 * - A transaction history log that explains the reason for each change to the above two sets
 * - The last ingested offset, synchronizer ID, record time, and update ID
 *
 */
public class IntegrationStore {

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    private long lastIngestedOffset;
    private String sourceSynchronizerId = null;
    private String lastIngestedRecordTime = null;

    // Might be lagging behind lastIngestedOffset if an offset checkpoint was ingested last
    private String lastIngestedUpdateId = null;

    private final String treasuryParty;

    private final HashMap<String, HoldingView> activeHoldings = new HashMap<>();
    private final HashMap<String, TransferInstructionView> pendingTransferInstructions = new HashMap<>();
    private final ArrayList<TxHistoryEntry> txHistoryLog = new ArrayList<>();

    public IntegrationStore(String treasuryParty, Long startingOffset) {
        this.treasuryParty = treasuryParty;
        this.lastIngestedOffset = startingOffset;
    }

    static public IntegrationStore copyWithoutTransactionEvents(IntegrationStore other) {
        IntegrationStore copy = new IntegrationStore(other.treasuryParty, other.lastIngestedOffset);
        copy.sourceSynchronizerId = other.sourceSynchronizerId;
        copy.lastIngestedRecordTime = other.lastIngestedRecordTime;
        copy.lastIngestedUpdateId = other.lastIngestedUpdateId;
        copy.activeHoldings.putAll(other.activeHoldings);
        copy.pendingTransferInstructions.putAll(other.pendingTransferInstructions);
        for (TxHistoryEntry entry : other.txHistoryLog) {
            TxHistoryEntry entryCopy = new TxHistoryEntry(
                    entry.updateMetadata(),
                    entry.exerciseNodeId(),
                    entry.transfer(),
                    entry.unrecognized(),
                    entry.treasuryHoldingChanges(),
                    entry.pendingTransferInstructionChanges(),
                    List.of()
            );
            copy.txHistoryLog.add(entryCopy);
        }
        return copy;
    }

    @Override
    public String toString() {
        return ExtendedJson.gsonPretty.toJson(this);
    }

    public List<TxHistoryEntry> getTxHistoryLog() {
        return txHistoryLog;
    }

    public HashMap<String, HoldingView> getActiveHoldings() {
        return activeHoldings;
    }

    public long getLastIngestedOffset() {
        return lastIngestedOffset;
    }

    public String getSourceSynchronizerId() {
        return sourceSynchronizerId;
    }

    public String getLastIngestedRecordTime() {
        return lastIngestedRecordTime;
    }

    public String getTreasuryParty() {
        return treasuryParty;
    }

    public Optional<HoldingView> lookupHoldingById(String contractId) {
        return Optional.ofNullable(activeHoldings.get(contractId));
    }

    /**
     * Select holdings to cover a withdrawal of the given amount of the given instrument.
     * Returns a list of holding contract IDs that together cover at least the requested amount.
     *
     * @param instrumentId the instrument ID of the token to withdraw
     * @param amount       the amount to withdraw
     * @return a list of holding contract IDs that together cover at least the requested amount, or empty if insufficient
     * holdings are available
     */
    public Optional<List<String>> selectHoldingsForWithdrawal(InstrumentId instrumentId, BigDecimal amount) {
        // Simple greedy algorithm: select arbitrary holdings until the amount is covered
        // TODO: switch to selecting as per https://docs.digitalasset.com/integrate/devnet/exchange-integration/workflows.html#utxo-selection-and-management
        List<String> selected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, HoldingView> entry : activeHoldings.entrySet()) {
            HoldingView holding = entry.getValue();
            // TODO: allow using locked holdings if the lock has expired
            if (holding.instrumentId.equals(instrumentId) && holding.lock.isEmpty()) {
                selected.add(entry.getKey());
                total = total.add(holding.amount);
                if (total.compareTo(amount) >= 0) {
                    log.info("Selected holdings " + selected + " worth " + total + " to cover withdrawal of " + amount + " of " + instrumentId);
                    return Optional.of(selected);
                }
            }
        }
        log.warning("Insufficient holdings to cover withdrawal of " + amount + " of " + instrumentId + " (total available: " + total + "), returning empty selection");
        return Optional.empty();
    }

    public void ingestUpdate(Update update0) {
        if (update0.getActualInstance() instanceof UpdateOneOf update) {
            ingestOffsetCheckpoint(update.getOffsetCheckpoint().getValue());
        } else if (update0.getActualInstance() instanceof UpdateOneOf1 update) {
            throw new UnsupportedOperationException("Multiple synchronizer are not yet supported, failed to handle reassignment: " + update.toJson());
        } else if (update0.getActualInstance() instanceof UpdateOneOf2 update) {
            log.finer(() -> "Ignoring topology transaction (not relevant): " + update.toJson());
        } else if (update0.getActualInstance() instanceof UpdateOneOf3 update) {
            ingestTransaction(update.getTransaction().getValue());
        } else {
            throw new UnsupportedOperationException("Failed to handle: " + update0.toJson());
        }
    }

    private void ingestTransaction(JsTransaction tx) {
        updateLastIngested(tx.getOffset(), tx.getSynchronizerId(), tx.getRecordTime(), tx.getUpdateId());
        assert tx.getEvents() != null;
        TxHistoryEntry.UpdateMetadata updateMetadata = new TxHistoryEntry.UpdateMetadata(tx.getUpdateId(), tx.getRecordTime(), tx.getOffset());
        TransactionParser parser = new TransactionParser(updateMetadata, new UtxoStoreImpl());
        List<TxHistoryEntry> entries = parser.parse(null, tx.getEvents());
        txHistoryLog.addAll(entries);
    }

    private void ingestOffsetCheckpoint(OffsetCheckpoint1 checkpoint) {
        List<SynchronizerTime> times = checkpoint.getSynchronizerTimes();
        if (times != null && times.size() == 1) {
            SynchronizerTime time = times.get(0);
            updateLastIngested(checkpoint.getOffset(), time.getSynchronizerId(), time.getRecordTime(), null);
        } else {
            throw new UnsupportedOperationException("Multiple synchronizers are not yet supported, failed to handle checkpoint: " + checkpoint.toJson());
        }
    }

    private void updateLastIngested(Long offset, String synchronizerId, String recordTime, String updateId) {
        if (this.sourceSynchronizerId != null && !this.sourceSynchronizerId.equals(synchronizerId)) {
            throw new IllegalStateException("Multiple synchronizers are not yet supported. Existing: " + this.sourceSynchronizerId + ", new: " + synchronizerId);
        }
        this.lastIngestedOffset = offset;
        this.sourceSynchronizerId = synchronizerId;
        this.lastIngestedRecordTime = recordTime;
        this.lastIngestedUpdateId = updateId;
    }

    private class UtxoStoreImpl implements TransactionParser.IUtxoStore {

        @Override
        public String treasuryPartyId() {
            return treasuryParty;
        }

        @Override
        public void ingestTransferInstructionCreation(String contractId, TransferInstructionView instruction) {
            Transfer t = instruction.transfer;
            if (t.sender.equals(treasuryParty) || t.receiver.equals(treasuryParty)) {
                log.info("New pending transfer instruction for treasury party: " + contractId + " -> " + instruction.toJson());
                pendingTransferInstructions.put(contractId, instruction);
            } else {
                log.finer(() -> "Ignoring creation of transfer instruction not affecting treasury party: " + contractId + " -> " + instruction.toJson());
            }
        }

        @NotNull
        @Override
        public Optional<TransferInstructionView> ingestTransferInstructionArchival(String contractId) {
            TransferInstructionView instruction = pendingTransferInstructions.remove(contractId);
            if (instruction != null) {
                log.info("Archiving pending transfer instruction for treasury party: " + contractId + " -> " + instruction.toJson());
            } else {
                log.finer(() -> "Ignoring archival of transfer instruction not affecting treasury party: " + contractId);
            }
            return Optional.ofNullable(instruction);
        }

        @Override
        public void ingestHoldingCreation(String contractId, HoldingView holding) {
            if (holding.owner.equals(treasuryParty)) {
                log.info("New active holding for treasury party: " + contractId + " -> " + holding.toJson());
                activeHoldings.put(contractId, holding);
            } else {
                log.finer(() -> "Ignoring creation of holding not owned by treasury party: " + contractId + " -> " + holding.toJson());
            }
        }

        @NotNull
        @Override
        public Optional<HoldingView> ingestHoldingArchival(String contractId) {
            HoldingView holding = activeHoldings.remove(contractId);
            if (holding != null) {
                log.info("Archiving active holding for treasury party: " + contractId + " -> " + holding.toJson());
            } else {
                log.finer(() -> "Ignoring archival of holding not owned by treasury party: " + contractId);
            }
            return Optional.ofNullable(holding);
        }
    }
}
