package com.example.store;

import com.example.ConversionHelpers;
import com.example.GsonTypeAdapters.ExtendedJson;
import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.*;
import com.example.models.TemplateId;
import com.example.store.models.Balances;
import com.example.store.models.TxHistoryEntry;
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
import java.util.logging.Logger;

import static com.example.models.TokenStandard.*;

/**
 * Parser for transactions to determine changes to treasury holdings and their causes.
 * Transactions that do not affect treasury holdings are ignored.
 */
public class TransactionParser {

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    final private IUtxoStore utxoStore;
    final private TxHistoryEntry.UpdateMetadata updateMetadata;
    private final Iterator<Event> events;
    final private ArrayList<TxHistoryEntry> childEntries = new ArrayList<>();

    /**
     * An interface to abstract over the tracking of UTXOs for holdings and transfer instructions.
     */
    public interface IUtxoStore {
        String treasuryPartyId();

        TransferInstructionView getTransferInstruction(String contractId);

        void ingestTransferInstructionCreation(String contractId, TransferInstructionView transferInstruction);

        @Nonnull
        Optional<TransferInstructionView> ingestTransferInstructionArchival(String contractId);

        void ingestHoldingCreation(String contractId, HoldingView holding);

        @Nonnull
        Optional<HoldingView> ingestHoldingArchival(String contractId);
    }

    TransactionParser(TxHistoryEntry.UpdateMetadata updateMetadata, IUtxoStore utxoStore, @Nonnull Iterator<Event> events) {
        this.updateMetadata = updateMetadata;
        this.utxoStore = utxoStore;
        this.events = events;
    }

    List<TxHistoryEntry> parse(ExercisedEvent exercisedEvent) {
        // record a possible consumed holding
        Optional<HoldingView> consumedHolding = Optional.empty();
        if (exercisedEvent != null) {
            if (exercisedEvent.getConsuming()) {
                consumedHolding = utxoStore.ingestHoldingArchival(exercisedEvent.getContractId());
                // We currently don't track transfers instruction archivals in the TxLogEntry itself,
                // which is why we ignore the result here.
                utxoStore.ingestTransferInstructionArchival(exercisedEvent.getContractId());
            }
        }
        int lastDescendantNodeId = exercisedEvent == null ? Integer.MAX_VALUE : exercisedEvent.getLastDescendantNodeId();

        // parse all child events
        int nodeId = exercisedEvent == null ? -1 : exercisedEvent.getNodeId();
        while (events.hasNext() && nodeId < lastDescendantNodeId) {
            nodeId = parseEvent(events.next());
        }

        // compute changes to treasury holdings
        List<TxHistoryEntry.HoldingChange> holdingChanges = new ArrayList<>();
        if (consumedHolding.isPresent()) {
            TxHistoryEntry.HoldingChange archival = new TxHistoryEntry.HoldingChange(
                    exercisedEvent.getContractId(),
                    consumedHolding.get(),
                    true
            );
            holdingChanges.add(archival);
        }
        for (TxHistoryEntry entry : childEntries) {
            holdingChanges.addAll(entry.treasuryHoldingChanges());
        }

        // gather events that form subtransaction
        List<Event> transactionEvents = new ArrayList<>();
        if (exercisedEvent != null) {
            transactionEvents.add(new Event(new EventOneOf2().exercisedEvent(exercisedEvent)));
        }
        for (TxHistoryEntry entry : childEntries) {
            transactionEvents.addAll(entry.transactionEvents());
        }

        // Determine log entries to return
        if (holdingChanges.isEmpty()) {
            // no holding changes ==> nothing to report
            return List.of();

        } else if (exercisedEvent == null) {
            // we are parsing the root transaction ==> just return the parsed child entries
            return childEntries;

        } else if (exercisedEvent.getChoice().equals(TransferFactory.CHOICE_TransferFactory_Transfer.name)
                && TemplateId.TRANSFER_FACTORY_INTERFACE_ID.matchesModuleAndTypeName(exercisedEvent.getInterfaceId())) {
            // we are parsing TransferFactory_Transfer choice ==> determine kind of transfer

            TransferFactory_Transfer transferChoiceArg = ConversionHelpers.convertViaJson(
                    exercisedEvent.getChoiceArgument(),
                    JSON.getGson()::toJson,
                    TransferFactory_Transfer::fromJson);
            Transfer t = transferChoiceArg.transfer;
            String memoTag = t.meta.values.getOrDefault(MEMO_KEY, "");

            // Attempt to determine label
            String treasuryParty = utxoStore.treasuryPartyId();
            TransferInstructionResult transferResult =
                    ConversionHelpers.convertViaJson(
                            exercisedEvent.getExerciseResult(),
                            JSON.getGson()::toJson,
                            TransferInstructionResult::fromJson);
            Optional<TxHistoryEntry.Label> label = parseTransferLabel(t, treasuryParty, memoTag, transferResult);
            if (label.isPresent()) {
                TxHistoryEntry entry = new TxHistoryEntry(
                        updateMetadata,
                        exercisedEvent.getNodeId(),
                        label.get(),
                        holdingChanges,
                        transactionEvents
                );
                return List.of(entry);
            }
        } else {
            // Attempt to parse the info from a "meta" field in an exerciseResult
            // This is a fallback method to tag registry internal workflows that change holdings.
            Object result0 = exercisedEvent.getExerciseResult();
            // convert to JSON and parse as generic JSON to see whether there is a .meta field
            String resultJson = JSON.getGson().toJson(result0);
            try {
                JsonElement element = JsonParser.parseString(resultJson);
                    /* Example JSON:
                        {"round":{"number":"20"},"summary":{"inputAppRewardAmount":"0.0000000000","inputValidatorRewardAmount":"0.0000000000","inputSvRewardAmount":"0.0000000000","inputAmuletAmount":"110.0000000000","balanceChanges":[["alice::1220edbbec72ee1fb1b99d40e3a19a0bfc7ea1306e24023efb34e2b4444230158866",{"changeToInitialAmountAsOfRoundZero":"-100.0000000000","changeToHoldingFeesRate":"0.0000000000"}],["treasury::12206b095339d93f62c84ae52c8d60e057f6da8ad14903d5f4c43e5bb274fb5ea3d0",{"changeToInitialAmountAsOfRoundZero":"100.0761036000","changeToHoldingFeesRate":"0.0038051800"}]],"holdingFees":"0.0000000000","outputFees":["0.0000000000"],"senderChangeFee":"0.0000000000","senderChangeAmount":"10.0000000000","amuletPrice":"0.0050000000","inputValidatorFaucetAmount":"0.0000000000","inputUnclaimedActivityRecordAmount":"0.0000000000"},"createdAmulets":[{"tag":"TransferResultAmulet","value":"007cf8d59d435576203ed6dfcede6798b49a2fcdb3a7932cfb7295b71745e8d257ca111220497e2d05b3f64cc2c79b84ab525e2a5d2b17bdd1488c8db78446a582a668a22e"}],"senderChangeAmulet":"0003752939fc734f75a441de5ab43f650338dc293ac1c98a5aea41678676cf192eca1112206847143bd3ba6eebc0caebc8428378784057f7f04ba34a273c0e7b697c545a1f",
                         "meta":{"values":{"splice.lfdecentralizedtrust.org/sender":"alice::1220edbbec72ee1fb1b99d40e3a19a0bfc7ea1306e24023efb34e2b4444230158866","splice.lfdecentralizedtrust.org/tx-kind":"transfer"}}
                        }
                     */
                JsonObject metadata = element.getAsJsonObject().getAsJsonObject("meta").getAsJsonObject("values");
                if (metadata.has(TRANSFER_KIND_KEY) && metadata.has(SENDER_KEY) && metadata.has(MEMO_KEY)) {
                    String kind = metadata.get(TRANSFER_KIND_KEY).getAsString();
                    String sender = metadata.get(SENDER_KEY).getAsString();
                    String memoTag = metadata.get(MEMO_KEY).getAsString();
                    if (kind.equals("transfer") && !sender.equals(utxoStore.treasuryPartyId())) {
                        // We only expect incoming transfers to be tagged that way.
                        Balances balances = new Balances();
                        for (TxHistoryEntry.HoldingChange hc : holdingChanges) {
                            if (hc.archived()) {
                                balances.debit(hc.holding().instrumentId, hc.holding().amount);
                            } else {
                                balances.credit(hc.holding().instrumentId, hc.holding().amount);
                            }
                        }
                        // We expect exactly one instrument ID to be affected
                        Map<InstrumentId, BigDecimal> balanceMap = balances.getBalanceMap();
                        if (balanceMap.size() == 1) {
                            Map.Entry<InstrumentId, BigDecimal> balanceEntry = balanceMap.entrySet().iterator().next();
                            InstrumentId instrumentId = balanceEntry.getKey();
                            BigDecimal balanceChange = balanceEntry.getValue();
                            // Only parse credits
                            if (balanceChange.compareTo(BigDecimal.ZERO) > 0) {
                                // This is an incoming transfer
                                TxHistoryEntry entry = new TxHistoryEntry(
                                        updateMetadata,
                                        exercisedEvent.getNodeId(),
                                        new TxHistoryEntry.TransferIn(sender, memoTag, instrumentId, balanceChange, null),
                                        holdingChanges,
                                        transactionEvents
                                );
                                return List.of(entry);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                // ignore parsing exceptions -- we'll parse this using the fallback below
            }
        }

        // Fallback case
        TxHistoryEntry.Label label = new TxHistoryEntry.UnrecognizedChoice(
                exercisedEvent.getPackageName(),
                exercisedEvent.getTemplateId(),
                exercisedEvent.getChoice());
        TxHistoryEntry entry = new TxHistoryEntry(
                updateMetadata,
                exercisedEvent.getNodeId(),
                label,
                holdingChanges,
                transactionEvents
        );
        if (consumedHolding.isPresent()) {
            // This is a non-standard choice consuming a holding
            return List.of(entry);
        } else if (childEntries.stream().noneMatch(e -> e.details().isRecognized())) {
            // No child entry uses a recognized label ==> explain them succinctly via this exercise node
            return List.of(entry);
        } else {
            // There were some recognized child entries ==> return them together with unrecognized ones
            return childEntries;
        }
    }

    private Optional<TxHistoryEntry.Label> parseTransferLabel(Transfer t, String treasuryParty, String memoTag, TransferInstructionResult result) {
        // TODO: pending and failed transfers
        if (t.sender.equals(treasuryParty)) {
            if (t.receiver.equals(t.sender)) {
                if (result.output instanceof TransferInstructionResult_Completed) {
                    return Optional.of(new TxHistoryEntry.SplitMerge(t.instrumentId));
                } else {
                    // TODO: consider recording these log messages on the transaction log entry itself
                    log.warning("Unexpected pending or failed self-transfer.");
                }
            } else {
                // parse an outgoing transfer one-step or multi-step
                TxHistoryEntry.TransferStatus transferStatus = null;
                TransferInstructionView pendingInstruction = null;
                if (result.output instanceof TransferInstructionResult_Completed) {
                    transferStatus = TxHistoryEntry.TransferStatus.COMPLETED;
                } else if (result.output instanceof TransferInstructionResult_Pending pendingResult) {
                    pendingInstruction = utxoStore.getTransferInstruction(pendingResult.transferInstructionCid.contractId);
                    if (pendingInstruction != null) {
                        transferStatus = TxHistoryEntry.TransferStatus.PENDING;
                    } else {
                        log.warning("Unexpectedly failed to find pending transfer instruction: " + pendingResult.transferInstructionCid.contractId);
                    }
                } else if (result.output instanceof TransferInstructionResult_Failed) {
                    transferStatus = TxHistoryEntry.TransferStatus.FAILED;
                } else {
                    log.warning("Unrecognized transfer result output:" + ExtendedJson.gson.toJson(result.output));
                }
                if (transferStatus != null) {
                    return Optional.of(new TxHistoryEntry.TransferOut(t.receiver, memoTag, t.instrumentId, t.amount, transferStatus, pendingInstruction));
                }
            }
        } else if (t.receiver.equals(treasuryParty)) {
            return Optional.of(new TxHistoryEntry.TransferIn(t.sender, memoTag, t.instrumentId, t.amount, null));
        }
        // label could not be determined
        return Optional.empty();
    }

    /**
     * @return the node ID of the last event parsed.
     */
    private int parseEvent(Event event0) {
        if (event0.getActualInstance() instanceof EventOneOf event) {
            throw new UnsupportedOperationException("Did not expect archive event as we are subscribing using LEDGER_EFFECTS: " + event.toJson());
        } else if (event0.getActualInstance() instanceof EventOneOf1 event) {
            return parseCreateEvent(event.getCreatedEvent());
        } else if (event0.getActualInstance() instanceof EventOneOf2 event) {
            return parseExerciseEvent(event.getExercisedEvent());
        } else {
            throw new UnsupportedOperationException("Failed to handle event: " + event0.toJson());
        }
    }

    private int parseCreateEvent(CreatedEvent createdEvent) {
        List<JsInterfaceView> interfaceViews = createdEvent.getInterfaceViews();
        if (interfaceViews != null) {
            for (JsInterfaceView view : interfaceViews) {
                if (TemplateId.HOLDING_INTERFACE_ID.matchesModuleAndTypeName(view.getInterfaceId())) {
                    String viewJson = JSON.getGson().toJson(view.getViewValue());
                    HoldingView holding = ConversionHelpers.convertFromJson(viewJson, HoldingView::fromJson);
                    if (holding.owner.equals(utxoStore.treasuryPartyId())) {
                        // We only care about created holdings owned by the treasury
                        String cid = createdEvent.getContractId();
                        utxoStore.ingestHoldingCreation(cid, holding);
                        TxHistoryEntry.HoldingChange creation = new TxHistoryEntry.HoldingChange(cid, holding, false);
                        childEntries.add(new TxHistoryEntry(
                                updateMetadata,
                                createdEvent.getNodeId(),
                                new TxHistoryEntry.BareCreate(createdEvent.getTemplateId()),
                                List.of(creation),
                                List.of(new Event(new EventOneOf1().createdEvent(createdEvent))
                                )));
                    }
                } else if (TemplateId.TRANSFER_INSTRUCTION_INTERFACE_ID.matchesModuleAndTypeName(view.getInterfaceId())) {
                    String viewJson = JSON.getGson().toJson(view.getViewValue());
                    TransferInstructionView instruction = ConversionHelpers.convertFromJson(viewJson, TransferInstructionView::fromJson);
                    String cid = createdEvent.getContractId();
                    utxoStore.ingestTransferInstructionCreation(cid, instruction);
                    // We do not create a log entry for the creation of a pending transfer instruction, as
                    // we expect all of their changes to come about using one of the four choices on TransferInstruction
                    // interface: https://github.com/hyperledger-labs/splice/blob/9d1025c0d5016c6040c49981c97d3f61a14edadf/token-standard/splice-api-token-transfer-instruction-v1/daml/Splice/Api/Token/TransferInstructionV1.daml#L123-L168
                }
            }
        }
        return createdEvent.getNodeId();
    }

    private int parseExerciseEvent(ExercisedEvent exercisedEvent) {
        TransactionParser subtransactionParser = new TransactionParser(updateMetadata, utxoStore, events);
        List<TxHistoryEntry> parsedChildEntries = subtransactionParser.parse(exercisedEvent);
        childEntries.addAll(parsedChildEntries);
        return exercisedEvent.getLastDescendantNodeId();

    }
}
