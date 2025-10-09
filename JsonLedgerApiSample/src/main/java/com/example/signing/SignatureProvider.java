/*
 * Copyright (c) 2025, by Digital Asset
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

package com.example.signing;

import com.example.client.ledger.model.Signature;
import com.google.protobuf.InvalidProtocolBufferException;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;

public interface SignatureProvider {
    /**
     * Using the provided keyPair, hash the provided transaction and sign it.
     *
     * The ledger API pre-calculates and provides a hash of the transaction to API callers as well.
     * The Wallet class provides the pre-calculated transaction hash to this method for reference.
     *
     * This delegate interface is provided so that an implementor can tailor review and verification of the provided
     * transactions to suit their needs.
     *
     * The Ledger class provides sample implementations and of this interface, which:
     * <ul>
     * <li> Sign the provided transaction hash. In this implementation, the hash provided by the participant node is trusted
     *      as a true hash of the transaction.
     * <li> Decodes and then prints the provided transaction to standard output.</li>
     * <li> Decodes the transaction, re-computes its hash, validates the computed hash against the one provided by the
     * API, and then signs the computed hash.</li>
     * </ul>
     *
     * @param keyPair - An external party's keypair, forwarded from the Wallet class
     * @param transactionHash - A base64-encoded hash of the provided transaction.
     * @param base64EncodedTransaction - A base64-encoded transaction. The transaction binary must first be decoded; Ledger.parseTransaction demonstrates how this could be done using classes provided by daml codegen.
     * @return A signature where the transaction was hashed, and then signed using the provided external party keypair.
     * @throws NoSuchAlgorithmException
     * @throws SignatureException
     * @throws InvalidKeyException
     * @throws InvalidProtocolBufferException
     */
    Signature sign(KeyPair keyPair, String transactionHash, String base64EncodedTransaction) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidProtocolBufferException;
}
