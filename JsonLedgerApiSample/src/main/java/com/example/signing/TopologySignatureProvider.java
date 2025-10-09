package com.example.signing;

import com.example.client.ledger.model.Signature;
import com.google.protobuf.InvalidProtocolBufferException;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;

public interface TopologySignatureProvider {
    /**
     * Using the provided keyPair, hash the provided topology transactions and sign them.
     *
     * The ledger API pre-calculates and provides an aggregate hash of the transactions to API callers as well.
     * The Wallet class provides the pre-calculated transaction hash to this method for reference.
     *
     * This delegate interface is provided so that an implementor can tailor review and verification of the provided
     * topology transactions to suit their needs.
     *
     * The Ledger class provides sample implementations and of this interface, which:
     * <ul>
     * <li> Sign the provided topology transaction aggregate hash. In this implementation, the hash provided by the
     * participant node is trusted as a true hash of the topology transactions.</li>
     * <li> Re-calculates the aggregate transaction hash based on the input transactions, validates the calculated hash
     *      against the one provided by the API, and then signs the calculated hash.</li>
     * </ul>
     *
     * @param keyPair - An external party's keypair, forwarded from the Wallet class
     * @param base64EncodedTransactions - The base64-encoded topology transactions.
     *                                  The provided transactions only need to be base64-decoded to binary; the hashing
     *                                  algorithm uses the provided binary format directly to compute the aggregate
     *                                  transaction topology hash.
     * @param transactionMultiHash - A base64-encoded aggregate hash of the provided transactions.
     * @return A signature where the topology transactions were in aggregate hashed, and then signed using the provided
     * external party keypair.
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws InvalidKeyException
     * @throws InvalidProtocolBufferException
     */
    Signature sign(KeyPair keyPair, List<String> base64EncodedTransactions, String transactionMultiHash) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidProtocolBufferException;
}
