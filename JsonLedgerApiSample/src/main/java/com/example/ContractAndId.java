package com.example;

import com.daml.ledger.javaapi.data.codegen.DamlRecord;

public record ContractAndId<T extends DamlRecord<T>>(
        String contractId,
        T record
) {
}
