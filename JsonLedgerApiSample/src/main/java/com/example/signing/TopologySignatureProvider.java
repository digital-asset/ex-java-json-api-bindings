package com.example.signing;

import com.example.client.ledger.model.Signature;
import com.google.protobuf.InvalidProtocolBufferException;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;

public interface TopologySignatureProvider {
    Signature sign(KeyPair keyPair, List<String> transactions, String transactionMultiHash) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidProtocolBufferException;
}
