package com.example.store;

import com.example.ConversionHelpers;
import com.example.client.ledger.model.JsContractEntry;
import com.example.client.ledger.model.Update;
import com.example.client.scan.model.UpdateHistoryItem;
import com.example.models.ContractAndId;
import com.example.models.TemplateId;
import splice.api.token.holdingv1.HoldingView;

import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * In-memory store mocking the Canton Integration DB from the exchange integration docs
 */
public class IntegrationStore {

    private static final Logger log = Logger.getLogger(IntegrationStore.class.getName());

    private final HashMap<String, HoldingView> activeHoldings = new HashMap<>();
    private long lastIngestedOffset = -1;

    private final String treasuryParty;

    public IntegrationStore(String treasuryParty) {
        this.treasuryParty = treasuryParty;
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

    public to

    private void requireAcsIngested() {
        if (lastIngestedOffset <= 0) {
            throw new IllegalStateException("Must ingest ACS before calling this method.");
        }
    }

    public void ingestUpdate(Update update) {}

    private void ingestActiveContract(JsContractEntry contract) {
        try {
            ContractAndId<HoldingView> holding =
                ConversionHelpers.fromInterface(contract, TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson))
            if (holding == null) {
                log.log(Level.INFO, () -> "Skipping contract (null): " + contract.toJson());
            } else {
                activeHoldings.put(holding.contractId(), holding.record());
            }
        } catch (Exception e) {
            log.log(Level.INFO, e, () -> "Skipping contract (parsing exception): " + contract.toJson());
        }
    }

}
