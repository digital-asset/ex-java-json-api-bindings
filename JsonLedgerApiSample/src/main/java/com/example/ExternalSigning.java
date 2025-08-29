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
