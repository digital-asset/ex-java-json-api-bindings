package com.example.testdata;

/** Info about a test workflow to be made available to the IntegrationStore tests. */
public record WorkflowInfo(
        String aliceDepositId,
        String aliceWithdrawalId
) {
}
