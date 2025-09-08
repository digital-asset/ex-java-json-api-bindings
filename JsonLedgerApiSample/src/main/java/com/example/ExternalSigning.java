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

package com.example;

import com.example.client.validator.model.ExternalPartySubmission;
import com.example.client.validator.model.SignedTopologyTx;
import com.example.client.validator.model.TopologyTx;

import java.security.*;
import java.util.List;

public class ExternalSigning {

    public static List<SignedTopologyTx> signOnboarding(List<TopologyTx> topologyTxs, PrivateKey privateKey) {
        return topologyTxs.stream().map(tx -> {

            String topologyTx = tx.getTopologyTx();
            String hash = tx.getHash();

            try {
                String signedHash = Keys.signHex(privateKey, hash);
                SignedTopologyTx signedTx = new SignedTopologyTx();
                signedTx.setTopologyTx(topologyTx);
                signedTx.setSignedHash(signedHash);
                return signedTx;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }

        }).toList();
    }

    public static ExternalPartySubmission signSubmission(String partyId, String tx, String txHash, KeyPair keyPair) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        ExternalPartySubmission submission = new ExternalPartySubmission();
        submission.setPartyId(partyId);
        submission.setTransaction(tx);
        submission.setSignedTxHash(Keys.signHex(keyPair.getPrivate(), txHash));
        submission.setPublicKey(Encode.toHexString(Keys.toRawBytes(keyPair.getPublic())));
        return submission;
    }
}
