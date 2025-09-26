package com.example.store.models;

import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.util.HashMap;

public class Balances {
    final private HashMap<InstrumentId, BigDecimal> balances = new HashMap<>();

    public Balances() {
    }

    ;

    public void add(Balances other) {
        for (var entry : other.balances.entrySet()) {
            balances.put(entry.getKey(),
                    balances.getOrDefault(entry.getKey(), BigDecimal.ZERO).add(entry.getValue()));
        }
    }

    public void credit(InstrumentId instrumentId, BigDecimal amount) {
        this.balances.put(instrumentId, this.balances.getOrDefault(instrumentId, BigDecimal.ZERO).add(amount));
    }

    public void debit(InstrumentId instrumentId, BigDecimal amount) {
        this.balances.put(instrumentId, this.balances.getOrDefault(instrumentId, BigDecimal.ZERO).subtract(amount));
    }

    public BigDecimal getBalance(InstrumentId instrumentId) {
        return this.balances.getOrDefault(instrumentId, BigDecimal.ZERO);
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("Balances{\n");
        balances.forEach((instrumentId, balance) ->
                sb.append("  ").append(instrumentId).append(": ").append(balance).append("\n")
        );
        sb.append("}}");
        return sb.toString();
    }
}
