package com.example.signing;

import com.daml.ledger.api.v2.ValueOuterClass;
import com.daml.ledger.api.v2.interactive.InteractiveSubmissionServiceOuterClass;
import com.daml.ledger.api.v2.interactive.transaction.v1.InteractiveSubmissionDataOuterClass;
import com.example.services.Ledger;
import com.google.protobuf.ByteString;

import java.util.*;

public class TopologyHashBuilder extends CantonHashBuilder {

    private static final byte[] HASH_PREFIX = {
            0x12, 0x20,
    };

    private static final int TOPOLOGY_TRANSACTION_SIGNATURE_HASH_PURPOSE = 11;
    private static final int MULTI_TOPOLOGY_TRANSACTION_HASH_PURPOSE = 55;

    List<String> topologyTransactionsBase64;

    public TopologyHashBuilder(List<String> topologyTransactionsBase64) {
        super();
        this.topologyTransactionsBase64 = new ArrayList<>(topologyTransactionsBase64);
    }

    private byte[] topologyTransactionHash(byte[] content) {
        byte[] hashedContent = Sha256.hash(new ByteArrayBuilder()
                .append(TOPOLOGY_TRANSACTION_SIGNATURE_HASH_PURPOSE)
                .append(content)
                .build());

        return ByteArrayBuilder.concat(HASH_PREFIX, hashedContent);
    }

    @Override
    public byte[] hash() {

        List<byte[]> transactionHashes = new ArrayList<>();
        for (var transaction: this.topologyTransactionsBase64) {
            byte[] rawTransaction = Encode.fromBase64String(transaction);
            byte[] rawTransactionHash = topologyTransactionHash(rawTransaction);
            transactionHashes.add(rawTransactionHash);
        }
        transactionHashes.sort(Comparator.comparing(Encode::toHexString));

        append(HASH_PREFIX);
        hashed(() -> {
            append(MULTI_TOPOLOGY_TRANSACTION_HASH_PURPOSE);
            encode(transactionHashes, this::encode);
        });
        return finish();
    }
}
