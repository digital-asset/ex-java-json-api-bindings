package com.example.store.models;

import jakarta.annotation.Nonnull;
import org.checkerframework.checker.nullness.qual.AssertNonNullIfNonNull;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TransferLog {

    /** A complete transfer from or to the treasury party */
    public record Transfer (
            // transaction metadata
            @Nonnull String updateId,
            @Nonnull String recordTime,
            long offset,
            int exerciseNodeId,

            // transfer data
            @Nonnull String senderPartyId,
            @Nonnull String receiverPartyId,
            @Nonnull String memoTag,
            @Nonnull InstrumentId instrumentId,
            @Nonnull BigDecimal amount,

            // changes to the treasury holdings that happened as part of this transfer
            @Nonnull List<HoldingChange> treasuryHoldingChanges,

            // only available if it was a token standard transfer
            splice.api.token.transferinstructionv1.Transfer transferSpec
    ) {
        public Balances getBalanceChanges() {
            Balances balances = new Balances();
            for (HoldingChange hc : treasuryHoldingChanges) {
                InstrumentId instrumentId = hc.holding.instrumentId;
                BigDecimal amount = hc.holding.amount;
                if (hc.archived) {
                    balances.debit(instrumentId, amount);
                } else {
                    balances.credit(instrumentId, amount);
                }
            }
            return balances;
        }
    }

    public record HoldingChange (
            @Nonnull String contractId,
            @Nonnull HoldingView holding,
            boolean archived
    ) { }

    final private String treasuryPartyId;
    final private ArrayList<Transfer> transfers = new ArrayList<>();

    public TransferLog(String treasuryPartyId) {
        this.treasuryPartyId = treasuryPartyId;
    }

    public List<Transfer> getTransfers() {
        return transfers;
    }

    public void addTransfer(Transfer transfer) {
        // TODO: validate that all holdings are owned by the treasury
        this.transfers.add(transfer);
    }

    public Balances getTreasuryBalances() {
        final Balances balances = new Balances();
        for (Transfer t : transfers) {
            balances.add(t.getBalanceChanges());
        }
        return balances;
    }

    public Map<String, Balances> getDepositBalances() {
        // Map from memoTag to Balances
        final Map<String, Balances> depositBalances = new java.util.HashMap<>();
        for (Transfer t : transfers) {
            // Only consider deposits to the treasury
            if (t.receiverPartyId.equals(treasuryPartyId)) {
                String memoTag = t.memoTag;
                final Balances balances = depositBalances.getOrDefault(memoTag, new Balances());
                balances.add(t.getBalanceChanges());
                depositBalances.put(memoTag, balances);
            }
        }
        return depositBalances;
    }
}
