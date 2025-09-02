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

import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.ExternalPartySubmission;
import com.example.client.validator.model.SignedTopologyTx;
import com.example.client.validator.model.TopologyTx;

import java.security.KeyPair;
import java.security.PrivateKey;
import java.util.List;

public class ExternalSigning {

    public static List<SignedTopologyTx> signOnboarding(List<TopologyTx> topologyTxs, PrivateKey privateKey) throws ApiException {
        return topologyTxs.stream().map(tx -> {

            String topologyTx = tx.getTopologyTx();
            String hash = tx.getHash();
            String signedHash = Keys.sign(privateKey, hash);

            SignedTopologyTx signedTx = new SignedTopologyTx();
            signedTx.setTopologyTx(topologyTx);
            signedTx.setSignedHash(signedHash);

            return signedTx;
        }).toList();
    }

    public static ExternalPartySubmission signSubmission(String partyId, String tx, String txHash, KeyPair keyPair) {
        ExternalPartySubmission submission = new ExternalPartySubmission();
        submission.setPartyId(partyId);
        submission.setTransaction(tx);
        submission.setSignedTxHash(Keys.sign(keyPair.getPrivate(), txHash));
        submission.setPublicKey(Encode.toHexString(Keys.toRawBytes(keyPair.getPublic())));
        return submission;
    }
}
