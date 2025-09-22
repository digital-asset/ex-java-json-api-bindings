package com.example.models;

import com.example.SampleUser;
import splice.api.token.holdingv1.Holding;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.metadatav1.ChoiceContext;
import splice.api.token.metadatav1.ExtraArgs;
import splice.api.token.metadatav1.Metadata;
import splice.api.token.transferinstructionv1.Transfer;
import splice.api.token.transferinstructionv1.TransferFactory_Transfer;
import splice.wallet.transferpreapproval.TransferPreapprovalProposal;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

public class Splice {
    public static TransferPreapprovalProposal makeTransferPreapprovalProposal(String receiver, String provider, String dso) {
        return new TransferPreapprovalProposal(receiver, provider, Optional.of(dso));
    }
}
