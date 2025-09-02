package com.example;

import com.daml.ledger.javaapi.data.codegen.json.JsonLfDecoder;

public interface DamlDecoder<T> {
    T decode(String input) throws JsonLfDecoder.Error;
}
