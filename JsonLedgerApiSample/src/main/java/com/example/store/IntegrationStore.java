package com.example.store;

import com.example.ConversionHelpers;
import com.example.client.ledger.model.*;
import com.example.models.ContractAndId;
import com.example.models.TemplateId;
import splice.api.token.holdingv1.HoldingView;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * In-memory store mocking the Canton Integration DB from the exchange integration docs
 */
public class IntegrationStore {

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    private final HashMap<String, HoldingView> activeHoldings = new HashMap<>();
    private long lastIngestedOffset = -1;
    private String sourceSynchronizerId = null;
    private String lastIngestedRecordTime = null;
    private final String treasuryParty;

    public IntegrationStore(String treasuryParty) {
        this.treasuryParty = treasuryParty;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("IntegrationStore{")
                .append("\ntreasuryParty='").append(treasuryParty).append('\'')
                .append("\nlastIngestedOffset=").append(lastIngestedOffset)
                .append("\nsourceSynchronizerId='").append(sourceSynchronizerId).append('\'')
                .append("\nlastIngestedRecordTime='").append(lastIngestedRecordTime).append('\'')
                .append("\nactiveHoldings={\n");

        activeHoldings.forEach((key, value) ->
                sb.append("  ").append(key).append(": ").append(value).append("\n")
        );

        sb.append("}}");
        return sb.toString();
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


    public void ingestAcs(List<JsContractEntry> contracts, long offset) {
        for (JsContractEntry contract : contracts) {
            ingestActiveContract(contract);
        }
        this.lastIngestedOffset = offset;
    }

    public Optional<HoldingView> lookupHoldingById(String contractId) {
        requireAcsIngested();
        return Optional.ofNullable(activeHoldings.get(contractId));
    }

    private void requireAcsIngested() {
        if (lastIngestedOffset <= 0) {
            throw new IllegalStateException("Must ingest ACS before calling this method.");
        }
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
        updateLastIngested(tx.getOffset(), tx.getSynchronizerId(), tx.getRecordTime());
        assert tx.getEvents() != null;
        for (Event event : tx.getEvents()) {
            ingestEvent(event);
        }
    }

    private void ingestEvent(Event event0) {
        if (event0.getActualInstance() instanceof EventOneOf event) {
            throw new UnsupportedOperationException("Did not expect archive event as we are subscribing using LEDGER_EFFECTS: " + event.toJson());
        } else if (event0.getActualInstance() instanceof EventOneOf1 event) {
            ingestCreateEvent(event.getCreatedEvent());
        } else if (event0.getActualInstance() instanceof EventOneOf2 event) {
            ingestExerciseEvent(event.getExercisedEvent());
        } else {
            throw new UnsupportedOperationException("Failed to handle event: " + event0.toJson());
        }
    }

    private void ingestCreateEvent(CreatedEvent createdEvent) {
        List<JsInterfaceView> interfaceViews = createdEvent.getInterfaceViews();
        if (interfaceViews != null) {
            for (JsInterfaceView view : interfaceViews) {
                if (TemplateId.HOLDING_INTERFACE_ID.matchesModuleAndTypeName(view.getInterfaceId())) {
                    String viewJson = com.example.client.ledger.invoker.JSON.getGson().toJson(view.getViewValue());
                    HoldingView holding = ConversionHelpers.convertFromJson(viewJson, HoldingView::fromJson);
                    ingestCreateHoldingEvent(createdEvent.getContractId(), holding);
                    return;
                }
            }
        }
        log.finer(() -> "Ignoring create event as it does not implement Holding interface: " + createdEvent.toJson());
    }

    private void ingestExerciseEvent(ExercisedEvent exercisedEvent) {
        // Note: we currently only parse consumptions
        if (!exercisedEvent.getConsuming()) {
            log.finer(() -> "Ignoring non-consuming exercise event: " + exercisedEvent.toJson());
        } else {
            String cid = exercisedEvent.getContractId();
            HoldingView oldView = activeHoldings.remove(cid);
            if (oldView == null) {
                log.finer(() -> "Ignoring consuming exercise event for untracked contract: " + exercisedEvent.toJson());
            } else {
                log.info("Processed consuming choice " + exercisedEvent.getChoice() + " on holding " + cid);
            }
        }
    }

    private void ingestOffsetCheckpoint(OffsetCheckpoint1 checkpoint) {
        List<SynchronizerTime> times = checkpoint.getSynchronizerTimes();
        if (times != null && times.size() == 1) {
            SynchronizerTime time = times.get(0);
            updateLastIngested(checkpoint.getOffset(), time.getSynchronizerId(), time.getRecordTime());
        } else {
            throw new UnsupportedOperationException("Multiple synchronizers are not yet supported, failed to handle checkpoint: " + checkpoint.toJson());
        }
    }

    private void updateLastIngested(Long offset, String synchronizerId, String recordTime) {
        if (this.sourceSynchronizerId != null && !this.sourceSynchronizerId.equals(synchronizerId)) {
            throw new IllegalStateException("Multiple synchronizers are not yet supported. Existing: " + this.sourceSynchronizerId + ", new: " + synchronizerId);
        }
        this.lastIngestedOffset = offset;
        this.sourceSynchronizerId = synchronizerId;
        this.lastIngestedRecordTime = recordTime;
    }

    private void ingestCreateHoldingEvent(String cid, HoldingView holding) {
        if (treasuryParty.equals(holding.owner)) {
            assert !activeHoldings.containsKey(cid);
            activeHoldings.put(cid, holding);
        } else {
            log.finer(() -> "Ignoring creation of holding not owned by treasury party: " + cid + " -> " + holding.toJson());
        }
    }

    private void ingestActiveContract(JsContractEntry contract) {
        try {
            ContractAndId<HoldingView> holding =
                    ConversionHelpers.fromInterface(contract, TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson);
            if (holding == null) {
                log.info(() -> "Skipping contract (null): " + contract.toJson());
            } else {
                ingestCreateHoldingEvent(holding.contractId(), holding.record());
            }
        } catch (Exception e) {
            log.log(Level.INFO, e, () -> "Skipping contract due to parsing exception: " + contract.toJson());
        }
    }

}
