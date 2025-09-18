package com.example.models;

import splice.wallet.transferpreapproval.TransferPreapprovalProposal;

import java.util.Optional;

public class Splice {
    public static TransferPreapprovalProposal makeTransferPreapprovalProposal(String receiver, String provider, String dso) {
        return new TransferPreapprovalProposal(receiver, provider, Optional.of(dso));
    }
}
