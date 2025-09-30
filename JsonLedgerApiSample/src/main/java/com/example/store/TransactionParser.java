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
 * Parsing the changes to the treasury's holdings and pending transfer instructions from a transaction.
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

    /**
     * Parse a (sub)transaction starting at the given exercise event.
     *
     * @param exercisedEvent the root exercise event of the (sub)transaction, or null if parsing the root transaction
     * @return the list of log entries parsed from this (sub)transaction
     * <p>
     * Must be called at most once per TransactionParser instance.
     */
    List<TxHistoryEntry> parse(ExercisedEvent exercisedEvent) {
        // Shared values
        String treasuryParty = utxoStore.treasuryPartyId();

        // Recording consumption
        Optional<HoldingView> consumedHolding = Optional.empty();
        Optional<TransferInstructionView> consumedTransferInstruction = Optional.empty();
        if (exercisedEvent != null) {
            if (exercisedEvent.getConsuming()) {
                consumedHolding = utxoStore.ingestHoldingArchival(exercisedEvent.getContractId());
                consumedTransferInstruction = utxoStore.ingestTransferInstructionArchival(exercisedEvent.getContractId());
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

        // compute changes to pending transfer instructions
        List<TxHistoryEntry.TransferInstructionChange> instructionChanges = new ArrayList<>();
        if (consumedTransferInstruction.isPresent()) {
            TxHistoryEntry.TransferInstructionChange archival = new TxHistoryEntry.TransferInstructionChange(
                    exercisedEvent.getContractId(),
                    consumedTransferInstruction.get(),
                    true
            );
            instructionChanges.add(archival);
        }
        for (TxHistoryEntry entry : childEntries) {
            instructionChanges.addAll(entry.pendingTransferInstructionChanges());
        }

        // FIXME: gather them from input list of events
        // gather events that form subtransaction
        List<Event> transactionEvents = new ArrayList<>();
        if (exercisedEvent != null) {
            transactionEvents.add(new Event(new EventOneOf2().exercisedEvent(exercisedEvent)));
        }
        for (TxHistoryEntry entry : childEntries) {
            transactionEvents.addAll(entry.transactionEvents());
        }

        // Determine log entries to return
        if (holdingChanges.isEmpty() && instructionChanges.isEmpty()) {
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

            // Attempt to determine transfer
            TransferInstructionResult transferResult =
                    ConversionHelpers.convertViaJson(
                            exercisedEvent.getExerciseResult(),
                            JSON.getGson()::toJson,
                            TransferInstructionResult::fromJson);
            Optional<TxHistoryEntry.Transfer> transfer = parseTransfer(t, treasuryParty, exercisedEvent.getChoice(), transferResult, null);
            if (transfer.isPresent()) {
                TxHistoryEntry entry = new TxHistoryEntry(
                        updateMetadata,
                        exercisedEvent.getNodeId(),
                        transfer.get(),
                        null,
                        holdingChanges,
                        instructionChanges,
                        transactionEvents
                );
                return List.of(entry);
            }
        } else if (consumedTransferInstruction.isPresent()
                && TemplateId.TRANSFER_INSTRUCTION_INTERFACE_ID.matchesModuleAndTypeName(exercisedEvent.getInterfaceId())) {
            // we are parsing a TransferInstruction choice ==> determine kind of transfer
            Transfer t = consumedTransferInstruction.get().transfer;
            String multiStepCorrelationId = getMultiStepCorrelationId(exercisedEvent.getContractId(), consumedTransferInstruction.get());

            // Attempt to determine transfer
            TransferInstructionResult transferResult =
                    ConversionHelpers.convertViaJson(
                            exercisedEvent.getExerciseResult(),
                            JSON.getGson()::toJson,
                            TransferInstructionResult::fromJson);
            Optional<TxHistoryEntry.Transfer> transfer = parseTransfer(t, treasuryParty, exercisedEvent.getChoice(), transferResult, multiStepCorrelationId);
            if (transfer.isPresent()) {
                TxHistoryEntry entry = new TxHistoryEntry(
                        updateMetadata,
                        exercisedEvent.getNodeId(),
                        transfer.get(),
                        null,
                        holdingChanges,
                        instructionChanges,
                        transactionEvents
                );
                return List.of(entry);
            }
        } else {
            // Attempt to parse the info from a "meta" field in an exerciseResult
            //
            // This is a fallback method used by token admins to tag transfers executed using token-specific workflows.
            // For example, Amulet uses it to tag its 1-step transfers and the Splice Wallet legacy transfer offer flow.
            //
            // Note that we expect the need for this fallback to go away once transfer preapprovals have also been
            // standardized (tracking ticket: https://github.com/hyperledger-labs/splice/issues/2085).

            // convert to JSON and parse as generic JSON to see whether there is a .meta field
            Object result0 = exercisedEvent.getExerciseResult();
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
                            // We only parse incoming transfers, as these outgoing transfers should be parsed
                            // using the standard TransferFactory_Transfer choice.
                            if (balanceChange.compareTo(BigDecimal.ZERO) > 0) {
                                TxHistoryEntry.TransferDetails details = new TxHistoryEntry.TransferDetails(
                                        memoTag,
                                        instrumentId,
                                        balanceChange,
                                        TxHistoryEntry.TransferStatus.COMPLETED,
                                        null,
                                        null);
                                TxHistoryEntry.Transfer transfer = new TxHistoryEntry.Transfer(sender, treasuryParty, TxHistoryEntry.TransferKind.TRANSFER_IN, details);
                                TxHistoryEntry entry = new TxHistoryEntry(
                                        updateMetadata,
                                        exercisedEvent.getNodeId(),
                                        transfer,
                                        null,
                                        holdingChanges,
                                        instructionChanges,
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
        TxHistoryEntry.Unrecognized unrecognized = new TxHistoryEntry.Unrecognized(
                TxHistoryEntry.UnrecognizedKind.UNRECOGNIZED_CHOICE,
                new TreeMap<>(Map.of(
                        "packageName", exercisedEvent.getPackageName(),
                        "templateId", exercisedEvent.getTemplateId(),
                        "choiceName", exercisedEvent.getChoice()
                ))
        );
        TxHistoryEntry entry = new TxHistoryEntry(
                updateMetadata,
                exercisedEvent.getNodeId(),
                null,
                unrecognized,
                holdingChanges,
                instructionChanges,
                transactionEvents
        );
        if (consumedHolding.isPresent()) {
            // This is a non-standard choice consuming a holding
            return List.of(entry);
        } else if (childEntries.stream().allMatch(e -> e.transfer() == null)) {
            // No child entry represents a transfer ==> explain them succinctly via this exercise node
            return List.of(entry);
        } else {
            // There were some recognized transfers ==> return them together with unrecognized ones
            return childEntries;
        }
    }

    private String getMultiStepCorrelationId(String instructionCid, TransferInstructionView transferInstructionView) {
        if (transferInstructionView.originalInstructionCid.isPresent()) {
            return transferInstructionView.originalInstructionCid.get().contractId;
        } else
            return instructionCid;
    }

    private Optional<TxHistoryEntry.Transfer> parseTransfer(Transfer t, String treasuryParty, String choiceName, TransferInstructionResult result, String multiStepCorrelationId) {
        String memoTag = t.meta.values.getOrDefault(MEMO_KEY, "");

        // parse status of transfer
        TxHistoryEntry.TransferStatus transferStatus = null;
        TransferInstruction.ContractId pendingInstructionCid = null;
        String actualCorrelationId = multiStepCorrelationId;
        if (result.output instanceof TransferInstructionResult_Completed) {
            transferStatus = TxHistoryEntry.TransferStatus.COMPLETED;
        } else if (result.output instanceof TransferInstructionResult_Pending pendingResult) {
            String pendingCid = pendingResult.transferInstructionCid.contractId;
            pendingInstructionCid = new TransferInstruction.ContractId(pendingCid);
            transferStatus = TxHistoryEntry.TransferStatus.PENDING;
            // If no correlation ID is known yet, then the contract-id of the new pending instruction serves as the correlation ID
            actualCorrelationId = actualCorrelationId == null ? pendingCid : actualCorrelationId;
        } else if (result.output instanceof TransferInstructionResult_Failed) {
            if (choiceName.equals(TransferInstruction.CHOICE_TransferInstruction_Reject.name)) {
                transferStatus = TxHistoryEntry.TransferStatus.REJECTED;
            } else if (choiceName.equals(TransferInstruction.CHOICE_TransferInstruction_Withdraw.name)) {
                transferStatus = TxHistoryEntry.TransferStatus.WITHDRAWN;
            } else {
                transferStatus = TxHistoryEntry.TransferStatus.FAILED_OTHER;
            }
        } else {
            log.warning("Unrecognized transfer result output:" + ExtendedJson.gson.toJson(result.output));
        }

        // if status is determined, then determine direction of transfer
        if (transferStatus != null) {
            TxHistoryEntry.TransferDetails details = new TxHistoryEntry.TransferDetails(memoTag, t.instrumentId, t.amount, transferStatus, actualCorrelationId, pendingInstructionCid);
            return TxHistoryEntry.tryMkTransfer(treasuryParty, t.sender, t.receiver, details);
        } else {
            return Optional.empty();
        }
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
                    // A bare create event of a holding is never a transfer, but it may be due to minting
                    // We must parse it as it may create a holding owned by the treasury
                    String viewJson = JSON.getGson().toJson(view.getViewValue());
                    HoldingView holding = ConversionHelpers.convertFromJson(viewJson, HoldingView::fromJson);
                    if (holding.owner.equals(utxoStore.treasuryPartyId())) {
                        // We only care about created holdings owned by the treasury
                        String cid = createdEvent.getContractId();
                        utxoStore.ingestHoldingCreation(cid, holding);
                        TxHistoryEntry.HoldingChange creation = new TxHistoryEntry.HoldingChange(cid, holding, false);
                        TxHistoryEntry.Unrecognized unrecognized = new TxHistoryEntry.Unrecognized(
                                TxHistoryEntry.UnrecognizedKind.BARE_CREATE,
                                new TreeMap<>(Map.of(
                                        "templateId", createdEvent.getTemplateId(),
                                        "packageName", createdEvent.getPackageName()
                                ))
                        );
                        childEntries.add(new TxHistoryEntry(
                                updateMetadata,
                                createdEvent.getNodeId(),
                                null,
                                unrecognized,
                                List.of(creation),
                                List.of(),
                                List.of(new Event(new EventOneOf1().createdEvent(createdEvent))
                                )));
                    }
                } else if (TemplateId.TRANSFER_INSTRUCTION_INTERFACE_ID.matchesModuleAndTypeName(view.getInterfaceId())) {
                    // A bare create of a transfer instruction is a pending transfer
                    String viewJson = JSON.getGson().toJson(view.getViewValue());
                    TransferInstruction.ContractId pendingInstructionCid = new TransferInstruction.ContractId(createdEvent.getContractId());
                    TransferInstructionView instruction = ConversionHelpers.convertFromJson(viewJson, TransferInstructionView::fromJson);
                    Transfer t = instruction.transfer;
                    String multiStepCorrelationId = getMultiStepCorrelationId(createdEvent.getContractId(), instruction);
                    TxHistoryEntry.TransferDetails details = new TxHistoryEntry.TransferDetails("", t.instrumentId, t.amount, TxHistoryEntry.TransferStatus.PENDING, multiStepCorrelationId, pendingInstructionCid);
                    Optional<TxHistoryEntry.Transfer> transfer = TxHistoryEntry.tryMkTransfer(utxoStore.treasuryPartyId(), t.sender, t.receiver, details);
                    if (transfer.isPresent()) {
                        String cid = createdEvent.getContractId();
                        TxHistoryEntry.TransferInstructionChange creation = new TxHistoryEntry.TransferInstructionChange(cid, instruction, false);
                        utxoStore.ingestTransferInstructionCreation(cid, instruction);
                        childEntries.add(new TxHistoryEntry(
                                updateMetadata,
                                createdEvent.getNodeId(),
                                transfer.get(),
                                null,
                                List.of(),
                                List.of(creation),
                                List.of(new Event(new EventOneOf1().createdEvent(createdEvent))
                                )));
                    }
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
