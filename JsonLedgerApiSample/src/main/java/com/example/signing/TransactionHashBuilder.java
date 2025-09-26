package com.example.signing;

import com.daml.ledger.api.v2.ValueOuterClass;
import com.daml.ledger.api.v2.interactive.InteractiveSubmissionServiceOuterClass;
import com.daml.ledger.api.v2.interactive.transaction.v1.InteractiveSubmissionDataOuterClass;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class TransactionHashBuilder extends HashWriter {

    private interface EncodeCallback<T> {
        void call(T item) throws NoSuchAlgorithmException;
    }

    private interface EncodeAction {
        void call() throws NoSuchAlgorithmException;
    }

    private static final byte[] PREPARED_TRANSACTION_HASH_PURPOSE = {
            0x00, 0x00, 0x00, 0x30,
    };

    private static final byte NODE_ENCODING_VERSION = 0x01;
    private static final byte HASHING_SCHEME_VERSION_V2 = 0x02;

    private InteractiveSubmissionServiceOuterClass.PreparedTransaction preparedTransaction;
    private Map<String, InteractiveSubmissionServiceOuterClass.DamlTransaction.Node> nodesById;

    public TransactionHashBuilder(InteractiveSubmissionServiceOuterClass.PreparedTransaction preparedTransaction) {
        super();

        this.preparedTransaction = preparedTransaction;
        this.nodesById = new HashMap<>();
        for (InteractiveSubmissionServiceOuterClass.DamlTransaction.Node node : preparedTransaction.getTransaction().getNodesList()) {
            nodesById.put(node.getNodeId(), node);
        }
    }

    private void encode(byte[] bytes) {
        append(bytes.length);
        append(bytes);
    }

    private void encode(String s) {
        encode(s.getBytes(StandardCharsets.UTF_8));
    }

    private void encodeHex(String s) {
        encode(Encode.fromHexString(s));
    }

    private <T> void encode(Optional<T> opt, EncodeCallback<T> callback) throws NoSuchAlgorithmException {
        if (opt.isEmpty()) {
            append((byte)0);
        } else {
            append((byte)1);
            callback.call(opt.get());
        }
    }

    private <T> void encode(List<T> list, EncodeCallback<T> callback) throws NoSuchAlgorithmException {
        append(list.size());
        for (T item : list) {
            callback.call(item);
        }
    }

    private <T> void encode(T[] array, EncodeCallback<T> callback) throws NoSuchAlgorithmException {
        append(array.length);
        for (T item : array) {
            callback.call(item);
        }
    }

    private void encodeIdentifier(ValueOuterClass.Identifier identifier) throws NoSuchAlgorithmException {
        encode(identifier.getPackageId());
        encode(identifier.getModuleName().split("\\."), this::encode);
        encode(identifier.getEntityName().split("\\."), this::encode);
    }

    private void encodeMetadata(InteractiveSubmissionServiceOuterClass.Metadata metadata) throws NoSuchAlgorithmException {
        append(PREPARED_TRANSACTION_HASH_PURPOSE);
        append((byte)1);

        var submitterInfo = metadata.getSubmitterInfo();
        encode(submitterInfo.getActAsList(), this::encode);
        encode(submitterInfo.getCommandId());
        encode(metadata.getTransactionUuid());
        append(metadata.getMediatorGroup());
        encode(metadata.getSynchronizerId());
        append(metadata.getMinLedgerEffectiveTime()); // TODO: 'none' case not represented?
        append(metadata.getMaxLedgerEffectiveTime()); // TODO: 'none' case not represented?
        encode(metadata.getInputContractsList(), this::encodeInputContract);
    }

    private void encodeCreatedNode(InteractiveSubmissionDataOuterClass.Create created, EncodeAction writeNodeHash) throws NoSuchAlgorithmException {
        append(NODE_ENCODING_VERSION);
        encode(created.getLfVersion());
        append((byte)0); // create node tag
        writeNodeHash.call();
        encodeHex(created.getContractId());
        encode(created.getPackageName());
        encodeIdentifier(created.getTemplateId());
    }

    private void encodeInputContract(InteractiveSubmissionServiceOuterClass.Metadata.InputContract inputContract) throws NoSuchAlgorithmException {

        append(inputContract.getCreatedAt());

        var created = inputContract.getV1();
        hashed(() ->
            encodeCreatedNode(created, () -> {
                append((byte)0); // encode a 'nothing'
            }
        ));
    }

    private void encodeTransaction(InteractiveSubmissionServiceOuterClass.DamlTransaction transaction) throws NoSuchAlgorithmException {

        append(PREPARED_TRANSACTION_HASH_PURPOSE);
        encode(transaction.getVersion());
        encode(transaction.getRootsList(), (id) -> {
            InteractiveSubmissionServiceOuterClass.DamlTransaction.Node node = this.nodesById.get(id);
            assert node != null;
            hashed(() -> encodeNode(node));
        });
    }

    private void encodeNode(InteractiveSubmissionServiceOuterClass.DamlTransaction.Node node) throws NoSuchAlgorithmException {
        var v1 = node.getV1();
        switch (v1.getNodeTypeCase()) {
            case CREATE:
                encodeCreatedNode(v1.getCreate(), () -> {});
                break;
            case FETCH:
                break;
            case EXERCISE:
                break;
            case ROLLBACK:
                break;
            default:
                throw new IllegalStateException("Unsuppported node type: " + v1.getNodeTypeCase());
        }
    }

    public byte[] build() throws NoSuchAlgorithmException {
        hashed(() -> {
            append(PREPARED_TRANSACTION_HASH_PURPOSE);
            append(HASHING_SCHEME_VERSION_V2);
            hashed(() -> encodeTransaction(preparedTransaction.getTransaction()));
            hashed(() -> encodeMetadata(preparedTransaction.getMetadata()));
        });
        return finish();
    }
}
