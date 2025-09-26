package com.example.store;

import com.example.ConversionHelpers;
import com.example.GsonTypeAdapters.ExtendedJson;
import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.*;
import com.example.models.ContractAndId;
import com.example.models.TemplateId;
import com.example.store.models.Balances;
import com.example.store.models.TransferLog;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import jakarta.annotation.Nonnull;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.transferinstructionv1.*;
import splice.api.token.transferinstructionv1.transferinstructionresult_output.TransferInstructionResult_Completed;
import splice.api.token.transferinstructionv1.transferinstructionresult_output.TransferInstructionResult_Failed;
import splice.api.token.transferinstructionv1.transferinstructionresult_output.TransferInstructionResult_Pending;

import java.math.BigDecimal;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;

import static com.example.models.TokenStandard.*;

/**
 * In-memory store mocking the Canton Integration DB from the exchange integration docs
 */
public class IntegrationStore {

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    private final HashMap<String, HoldingView> activeHoldings = new HashMap<>();
    private long lastIngestedOffset = -1;
    private String sourceSynchronizerId = null;
    private String lastIngestedRecordTime = null;

    // Might be lagging behind lastIngestedOffset if an offset checkpoint was ingested last
    private String lastIngestedUpdateId = null;

    private final String treasuryParty;
    private final TransferLog transferLog;

    public BigDecimal getBalance(InstrumentId instrumentId, String depositId) {
        return transferLog.getDepositBalances().getOrDefault(depositId, new Balances()).getBalance(instrumentId);
    }

    private static class TransferInfo {
        @Nonnull
        final public String sender;
        final public String receiver;
        @Nonnull
        final public String depositId;
        final public Transfer transferSpec;
        final public ArrayList<TransferLog.HoldingChange> treasuryHoldingChanges = new ArrayList<>();

        public TransferInfo(@Nonnull String sender, String receiver, @Nonnull String depositId, Transfer transferSpec) {
            this.sender = sender;
            this.receiver = receiver;
            this.depositId = depositId;
            this.transferSpec = transferSpec;
        }

        public void appendHoldingChange(String contractId, HoldingView holding, boolean archived) {
            treasuryHoldingChanges.add(new TransferLog.HoldingChange(contractId, holding, archived));
        }

        public Set<InstrumentId> affectedInstrumentIds() {
            HashSet<InstrumentId> instrumentIds = new HashSet<>();
            for (var hc : treasuryHoldingChanges) {
                instrumentIds.add(hc.holding().instrumentId);
            }
            return instrumentIds;
        }

        public BigDecimal treasuryHoldingChangeAmount(InstrumentId instrumentId) {
            return treasuryHoldingChanges.stream()
                    .filter(hc -> hc.holding().instrumentId.equals(instrumentId))
                    .map(hc -> hc.archived() ? hc.holding().amount.negate() : hc.holding().amount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
        }
    }

    private class TransactionParser {

        // Generic parsing of metadata is only required if the packages use custom transfer workflows.
        // It also requires the package to set the metadata in the expected format.
        // To avoid confusion, we whitelist the package names where generic metadata parsing is enabled.
        //
        // For all others we expect to use the token standard choices to parse the transfers.
        static final Set<String> packagesWithGenericMetadataParsing = Set.of("splice-amulet");

        private final Integer lastDescendantNodeId;
        private final Iterator<Event> events;
        private final int rootNodeId;
        private final TransferInfo transferInfo;

        /**
         * Parse the events in the iterator up to and including the event with the given node ID.
         * <p>
         * This parser properly follows the tree structure of exercise nodes and their children.
         * It will always parse at least one event.
         */
        TransactionParser(int rootNodeId, Iterator<Event> events, int lastDescendantNodeId, TransferInfo transferInfo) {
            assert events.hasNext();
            this.rootNodeId = rootNodeId;
            this.events = events;
            this.lastDescendantNodeId = lastDescendantNodeId;
            this.transferInfo = transferInfo;
        }

        /**
         * Parse the transaction and return the node ID of the last event parsed.
         */
        int parse() {
            assert events.hasNext();
            int nodeId;
            do {
                nodeId = ingestEvent(events.next());
            } while (events.hasNext() && nodeId < lastDescendantNodeId);
            return nodeId;
        }

        /**
         * @return the node ID of the last event parsed. There can be multiple in case of parsing an exercise node.
         */
        private int ingestEvent(Event event0) {
            if (event0.getActualInstance() instanceof EventOneOf event) {
                throw new UnsupportedOperationException("Did not expect archive event as we are subscribing using LEDGER_EFFECTS: " + event.toJson());
            } else if (event0.getActualInstance() instanceof EventOneOf1 event) {
                return ingestCreateEvent(event.getCreatedEvent());
            } else if (event0.getActualInstance() instanceof EventOneOf2 event) {
                return ingestExerciseEvent(event.getExercisedEvent());
            } else {
                throw new UnsupportedOperationException("Failed to handle event: " + event0.toJson());
            }
        }

        /**
         * Parse a create event, return its node ID.
         */
        private int ingestCreateEvent(CreatedEvent createdEvent) {
            List<JsInterfaceView> interfaceViews = createdEvent.getInterfaceViews();
            if (interfaceViews != null) {
                for (JsInterfaceView view : interfaceViews) {
                    if (TemplateId.HOLDING_INTERFACE_ID.matchesModuleAndTypeName(view.getInterfaceId())) {
                        String viewJson = JSON.getGson().toJson(view.getViewValue());
                        HoldingView holding = ConversionHelpers.convertFromJson(viewJson, HoldingView::fromJson);
                        ingestCreateHoldingEvent(createdEvent.getContractId(), holding, transferInfo);
                        return createdEvent.getNodeId();
                    }
                }
            }
            log.finer(() -> "Ignoring create event as it does not implement Holding interface: " + createdEvent.toJson());
            return createdEvent.getNodeId();
        }

        /**
         * Parse an exercise event, return the node ID of the last descendant node.
         */
        private int ingestExerciseEvent(ExercisedEvent exercisedEvent) {
            // Parse effect of the exercise node itself
            if (!exercisedEvent.getConsuming()) {
                log.finer(() -> "Ignoring non-consuming exercise event: " + exercisedEvent.toJson());
            } else {
                String cid = exercisedEvent.getContractId();
                HoldingView oldView = activeHoldings.remove(cid);
                if (oldView == null) {
                    log.info(() -> "Ignoring consuming exercise event for untracked contract: " + exercisedEvent.toJson());
                } else {
                    log.info("Processing consuming choice " + exercisedEvent.getChoice() + " on tracked holding " + cid);
                    if (transferInfo != null) {
                        transferInfo.appendHoldingChange(cid, oldView, true);
                    }
                }
            }

            // If there is no transfer info yet, try to extract it from the exercise event
            if (transferInfo == null) {
                TransferInfo newTransferInfo = parseTransferInfoFromExerciseEvent(exercisedEvent);

                if (newTransferInfo != null) {
                    // Parse child nodes, which are a transaction by themselves,
                    // see https://docs.digitalasset.com/overview/3.3/explanations/ledger-model/ledger-structure.html#transactions
                    log.finer("Starting parsing sub-transaction of choice " + exercisedEvent.getChoice() + " from node id " + exercisedEvent.getNodeId() + " to " + exercisedEvent.getLastDescendantNodeId());
                    TransactionParser subtransactionParser = new TransactionParser(exercisedEvent.getNodeId(), events, exercisedEvent.getLastDescendantNodeId(), newTransferInfo);
                    log.finer("Completed parsing sub-transaction of choice " + exercisedEvent.getChoice() + " from node id " + exercisedEvent.getNodeId() + " to " + exercisedEvent.getLastDescendantNodeId());
                    int lastNodeId = subtransactionParser.parse();

                    // Determine total amount transferred based on holding changes.
                    // We use the holding changes rather than the transfer spec, as they are the ground truth of what actually happened.
                    Set<InstrumentId> instrumentIds = newTransferInfo.affectedInstrumentIds();
                    if (instrumentIds.size() != 1) {
                        log.warning("Ignoring transfer affecting multiple instrument IDs " + instrumentIds + " in exercise event" + exercisedEvent.toJson());
                        // TODO: report this parse error on the transfer log (make it into a tx history log)
                        return lastNodeId;
                    } else {
                        InstrumentId instrumentId = instrumentIds.iterator().next();
                        BigDecimal rawAmount = newTransferInfo.treasuryHoldingChangeAmount(instrumentId);
                        BigDecimal amount = newTransferInfo.sender.equals(treasuryParty) ? rawAmount.negate() : rawAmount;
                        // record the transfer
                        TransferLog.Transfer t = new TransferLog.Transfer(
                                lastIngestedUpdateId,
                                lastIngestedRecordTime,
                                lastIngestedOffset,
                                exercisedEvent.getNodeId(),
                                newTransferInfo.sender,
                                // TODO: validate this with the holding changes
                                newTransferInfo.receiver != null ? newTransferInfo.receiver : treasuryParty,
                                newTransferInfo.depositId,
                                instrumentId,
                                amount,
                                newTransferInfo.treasuryHoldingChanges,
                                newTransferInfo.transferSpec
                        );
                        transferLog.addTransfer(t);
                        return lastNodeId;
                    }
                }
            }
            // Default case: just flatten the parsing of the child nodes into the parent
            return exercisedEvent.getNodeId();
        }

        TransferInfo parseTransferInfoFromExerciseEvent(ExercisedEvent exercisedEvent) {
            if (exercisedEvent.getChoice().equals(TransferFactory.CHOICE_TransferFactory_Transfer.name)
                    && TemplateId.TRANSFER_FACTORY_INTERFACE_ID.matchesModuleAndTypeName(exercisedEvent.getInterfaceId())) {
                // Parse information from the token standard choice argument
                TransferFactory_Transfer transferChoiceArg = ConversionHelpers.convertViaJson(
                        exercisedEvent.getChoiceArgument(),
                        JSON.getGson()::toJson,
                        TransferFactory_Transfer::fromJson);
                Transfer t = transferChoiceArg.transfer;
                String memoTag = t.meta.values.getOrDefault(MEMO_KEY, "");
                TransferInstructionResult transferResult =
                        ConversionHelpers.convertViaJson(
                                exercisedEvent.getExerciseResult(),
                                JSON.getGson()::toJson,
                                TransferInstructionResult::fromJson);
                if (transferResult.output instanceof TransferInstructionResult_Completed) {
                    return new TransferInfo(
                            t.sender,
                            t.receiver,
                            memoTag,
                            t
                    );
                } else if (transferResult.output instanceof TransferInstructionResult_Pending) {
                    // TODO: implement multi-step transfer support
                    log.warning("Encountered pending multi-step transfers -- not yet supported:" + exercisedEvent.toJson());
                    return null;
                } else if (transferResult.output instanceof TransferInstructionResult_Failed) {
                    // TODO: implement multi-step transfer support
                    log.warning("Encountered aborted multi-step transfer -- not yet supported: " + exercisedEvent.toJson());
                    return null;
                } else {
                    log.severe("Encountered unknown transfer result: " + exercisedEvent.toJson());
                    return null;
                }
            } else if (packagesWithGenericMetadataParsing.contains(exercisedEvent.getPackageName())) {
                // This a fallback to parse the info from arbitrary "meta" fields in an exerciseResult
                Object result0 = exercisedEvent.getExerciseResult();
                // convert to JSON and parse as generic JSON to see whether there is a .meta field
                String resultJson = JSON.getGson().toJson(result0);
                log.fine("ATTEMPTING TO PARSE EXERCISE RESULT: " + resultJson);
                try {
                    JsonElement element = JsonParser.parseString(resultJson);
                    /* Example JSON:
                        {"round":{"number":"20"},"summary":{"inputAppRewardAmount":"0.0000000000","inputValidatorRewardAmount":"0.0000000000","inputSvRewardAmount":"0.0000000000","inputAmuletAmount":"110.0000000000","balanceChanges":[["alice::1220edbbec72ee1fb1b99d40e3a19a0bfc7ea1306e24023efb34e2b4444230158866",{"changeToInitialAmountAsOfRoundZero":"-100.0000000000","changeToHoldingFeesRate":"0.0000000000"}],["treasury::12206b095339d93f62c84ae52c8d60e057f6da8ad14903d5f4c43e5bb274fb5ea3d0",{"changeToInitialAmountAsOfRoundZero":"100.0761036000","changeToHoldingFeesRate":"0.0038051800"}]],"holdingFees":"0.0000000000","outputFees":["0.0000000000"],"senderChangeFee":"0.0000000000","senderChangeAmount":"10.0000000000","amuletPrice":"0.0050000000","inputValidatorFaucetAmount":"0.0000000000","inputUnclaimedActivityRecordAmount":"0.0000000000"},"createdAmulets":[{"tag":"TransferResultAmulet","value":"007cf8d59d435576203ed6dfcede6798b49a2fcdb3a7932cfb7295b71745e8d257ca111220497e2d05b3f64cc2c79b84ab525e2a5d2b17bdd1488c8db78446a582a668a22e"}],"senderChangeAmulet":"0003752939fc734f75a441de5ab43f650338dc293ac1c98a5aea41678676cf192eca1112206847143bd3ba6eebc0caebc8428378784057f7f04ba34a273c0e7b697c545a1f",
                         "meta":{"values":{"splice.lfdecentralizedtrust.org/sender":"alice::1220edbbec72ee1fb1b99d40e3a19a0bfc7ea1306e24023efb34e2b4444230158866","splice.lfdecentralizedtrust.org/tx-kind":"transfer"}}
                        }
                     */
                    JsonObject metadata = element.getAsJsonObject().getAsJsonObject("meta").getAsJsonObject("values");
                    if (metadata.has(TRANSFER_KIND_KEY) && "transfer".equals(metadata.get(TRANSFER_KIND_KEY).getAsString())) {
                        if (metadata.has(SENDER_KEY) && metadata.has(MEMO_KEY)) {
                            String sender = metadata.get(SENDER_KEY).getAsString();
                            String depositId = metadata.get(MEMO_KEY).getAsString();
                            // Here we don't know the receiver. We'll have to infer it from the Holding changes.
                            TransferInfo transferInfo = new TransferInfo(sender, null, depositId, null);
                            log.info(() -> "Detected transfer info " + transferInfo + " in exercise result: " + resultJson);
                            return transferInfo;
                        } else {
                            log.warning(() -> "Incomplete transfer info in exercise result: " + resultJson);
                        }
                    } else {
                        log.fine(() -> "Not a transfer: " + resultJson);
                    }
                } catch (Exception e) {
                    log.log(Level.FINE, e, () -> "Failed to detect transfer info in exercise result: " + resultJson);
                }
            }
            return null;
        }
    }

    public IntegrationStore(String treasuryParty) {
        this.treasuryParty = treasuryParty;
        transferLog = new TransferLog(treasuryParty);
    }

    @Override
    public String toString() {
        return ExtendedJson.gsonPretty.toJson(this);
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

    public Optional<List<String>> selectHoldingsForWithdrawal(InstrumentId instrumentId, BigDecimal amount) {
        requireAcsIngested();
        // Simple greedy algorithm: select arbitrary holdings until the amount is covered
        // TODO: switch to selecting as per https://docs.digitalasset.com/integrate/devnet/exchange-integration/workflows.html#utxo-selection-and-management
        List<String> selected = new ArrayList<>();
        BigDecimal total = BigDecimal.ZERO;
        for (Map.Entry<String, HoldingView> entry : activeHoldings.entrySet()) {
            HoldingView holding = entry.getValue();
            if (holding.instrumentId.equals(instrumentId)) {
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
        updateLastIngested(tx.getOffset(), tx.getSynchronizerId(), tx.getRecordTime(), tx.getUpdateId());
        assert tx.getEvents() != null;
        TransactionParser parser = new TransactionParser(-1, tx.getEvents().iterator(), Integer.MAX_VALUE, null);
        parser.parse();
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

    private void ingestCreateHoldingEvent(String cid, HoldingView holding, TransferInfo transferInfo) {
        // TODO: consider tracking non-treasury owned holdings -- may be useful for dealing with support requests
        if (treasuryParty.equals(holding.owner)) {
            assert !activeHoldings.containsKey(cid);
            log.info("New active holding for treasury party: " + cid + " -> " + holding.toJson());
            activeHoldings.put(cid, holding);
            if (transferInfo != null) {
                transferInfo.appendHoldingChange(cid, holding, false);
            }
        } else {
            log.finer(() -> "Ignoring creation of holding not owned by treasury party: " + cid + " -> " + holding.toJson());
        }
    }

    /**
     * Ingest a create holding event from the ACS
     */
    private void ingestActiveContract(JsContractEntry contract) {
        try {
            ContractAndId<HoldingView> holding =
                    ConversionHelpers.fromInterface(contract, TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson);
            if (holding == null) {
                log.info(() -> "Skipping contract (null): " + contract.toJson());
            } else {
                ingestCreateHoldingEvent(holding.contractId(), holding.record(), null);
            }
        } catch (Exception e) {
            log.log(Level.INFO, e, () -> "Skipping contract due to parsing exception: " + contract.toJson());
        }
    }

}
