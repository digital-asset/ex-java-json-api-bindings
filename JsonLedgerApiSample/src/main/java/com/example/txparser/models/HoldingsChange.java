package com.example.txparser.models;

import com.example.client.ledger.model.Update;
import com.example.models.ContractAndId;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;

public class HoldingsChange {
    ArrayList<ContractAndId<HoldingView>> createdHoldings = new ArrayList<>();
    ArrayList<ContractAndId<HoldingView>> archivedHolding = new ArrayList<>();

    public HoldingsChange() {
    }

    public void addCreated(ContractAndId<HoldingView> h) {
        this.createdHoldings.add(h);
    }

    public void addArchived(ContractAndId<HoldingView> h) {
        this.archivedHolding.add(h);
    }

    public HashMap<InstrumentId, BigDecimal> netChanges() {
        HashMap<InstrumentId, BigDecimal> changes = new HashMap<>();
        for (ContractAndId<HoldingView> h : this.createdHoldings) {
            changes.putIfAbsent(h.record().instrumentId, BigDecimal.ZERO);
            changes.put(h.record().instrumentId, changes.get(h.record().instrumentId).add(h.record().amount));
        }
        for (ContractAndId<HoldingView> h : this.archivedHolding) {
            changes.putIfAbsent(h.record().instrumentId, BigDecimal.ZERO);
            changes.put(h.record().instrumentId, changes.get(h.record().instrumentId).subtract(h.record().amount));
        }
        return changes;
    }

    public static HoldingsChange fromUpdate(Update update) {

    }
}

