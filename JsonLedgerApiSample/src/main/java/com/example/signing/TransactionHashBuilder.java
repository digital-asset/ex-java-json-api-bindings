package com.example.signing;

import com.daml.ledger.api.v2.ValueOuterClass;
import com.daml.ledger.api.v2.interactive.InteractiveSubmissionServiceOuterClass;
import com.daml.ledger.api.v2.interactive.transaction.v1.InteractiveSubmissionDataOuterClass;
import com.google.protobuf.ByteString;

import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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

    private final InteractiveSubmissionServiceOuterClass.PreparedTransaction preparedTransaction;
    private final Map<String, InteractiveSubmissionServiceOuterClass.DamlTransaction.Node> nodesById;
    private Map<String, InteractiveSubmissionServiceOuterClass.DamlTransaction.NodeSeed> nodeSeedsById;

    public TransactionHashBuilder(InteractiveSubmissionServiceOuterClass.PreparedTransaction preparedTransaction) {
        super();

        this.preparedTransaction = preparedTransaction;

        this.nodesById = new HashMap<>();
        var transactionBody = preparedTransaction.getTransaction();
        for (InteractiveSubmissionServiceOuterClass.DamlTransaction.Node node : transactionBody.getNodesList()) {
            nodesById.put(node.getNodeId(), node);
        }

        this.nodeSeedsById = new HashMap<>();
        for (InteractiveSubmissionServiceOuterClass.DamlTransaction.NodeSeed seed : transactionBody.getNodeSeedsList()) {
            nodeSeedsById.put(seed.getNodeId() + "", seed);
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

    private <T> void encodeProtoOptional(boolean isPresent, Supplier<T> getValue, EncodeCallback<T> callback) throws NoSuchAlgorithmException {
        if (isPresent) {
            append((byte)1);
            callback.call(getValue.get());
        } else {
            append((byte)0);
        }
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
        encodeProtoOptional(metadata.hasMinLedgerEffectiveTime(), metadata::getMinLedgerEffectiveTime, this::append);
        encodeProtoOptional(metadata.hasMaxLedgerEffectiveTime(), metadata::getMaxLedgerEffectiveTime, this::append);
        encode(metadata.getInputContractsList(), this::encodeInputContract);
    }

    private void encodeCreatedNode(InteractiveSubmissionDataOuterClass.Create create, Optional<ByteString> nodeSeed) throws NoSuchAlgorithmException {
        append(NODE_ENCODING_VERSION);
        encode(create.getLfVersion());
        append((byte)0); // 'create' node tag
        encode(nodeSeed, this::append);
        encodeHex(create.getContractId());
        encode(create.getPackageName());
        encodeIdentifier(create.getTemplateId());
        encodeValue(create.getArgument());
        encode(create.getSignatoriesList(), this::encode);
        encode(create.getStakeholdersList(), this::encode);
    }

    private void encodeExerciseNode(InteractiveSubmissionDataOuterClass.Exercise exercise, ByteString nodeSeed) throws NoSuchAlgorithmException {
        append(NODE_ENCODING_VERSION);
        encode(exercise.getLfVersion());
        append((byte)1); // 'exercise' node tag
        append(nodeSeed);
        encodeHex(exercise.getContractId());
        encode(exercise.getPackageName());
        encodeIdentifier(exercise.getTemplateId());
        encode(exercise.getSignatoriesList(), this::encode);
        encode(exercise.getActingPartiesList(), this::encode);
        encodeProtoOptional(exercise.hasInterfaceId(), exercise::getInterfaceId, this::encodeIdentifier);
        encode(exercise.getChoiceId());
        encodeValue(exercise.getChosenValue());
        append(exercise.getConsuming());
        encodeProtoOptional(exercise.hasExerciseResult(), exercise::getExerciseResult, this::encodeValue);
        encode(exercise.getChoiceObserversList(), this::encode);
        encode(exercise.getChildrenList(), this::encodeNodeById);
    }

    private void encodeFetchNode(InteractiveSubmissionDataOuterClass.Fetch fetch) throws NoSuchAlgorithmException {
        append(NODE_ENCODING_VERSION);
        encode(fetch.getLfVersion());
        append((byte)2); // 'fetch' node tag
        encodeHex(fetch.getContractId());
        encode(fetch.getPackageName());
        encodeIdentifier(fetch.getTemplateId());
        encode(fetch.getSignatoriesList(), this::encode);
        encode(fetch.getStakeholdersList(), this::encode);
        encodeProtoOptional(fetch.hasInterfaceId(), fetch::getInterfaceId, this::encodeIdentifier);
        encode(fetch.getActingPartiesList(), this::encode);
    }

    private void encodeRollbackNode(InteractiveSubmissionDataOuterClass.Rollback rollback) throws NoSuchAlgorithmException {
        append(NODE_ENCODING_VERSION);
        append((byte)3); // 'rollback' node tag
        encode(rollback.getChildrenList(), this::encodeNodeById);
    }

    private void encodeValue(ValueOuterClass.Value value) throws NoSuchAlgorithmException {
        // note: the initial 'type' tag bytes are not the same as 'value.getSumCase().getNumber()'.
        switch (value.getSumCase()) {
            case UNIT:
                append((byte)0x00);
                break;
            case BOOL:
                append((byte)0x01);
                append(value.getBool());
                break;
            case INT64:
                append((byte)0x02);
                append(value.getInt64());
                break;
            case NUMERIC:
                append((byte)0x03);
                encode(value.getNumeric());
                break;
            case TIMESTAMP:
                append((byte)0x04);
                encode(value.getTimestamp() + ""); // TODO: check why this is converted to a string before encoding
                break;
            case DATE:
                append((byte)0x05);
                append(value.getDate());
                break;
            case PARTY:
                append((byte)0x06);
                encode(value.getParty());
                break;
            case TEXT:
                append((byte)0x07);
                encode(value.getText());
                break;
            case CONTRACT_ID:
                append((byte)0x08);
                encodeHex(value.getContractId());
                break;
            case OPTIONAL:
                append((byte)0x09);
                ValueOuterClass.Optional optional = value.getOptional();
                encodeProtoOptional(optional.hasValue(), optional::getValue, this::encodeValue);
                break;
            case LIST:
                append((byte)0x0a);
                encode(value.getList().getElementsList(), this::encodeValue);
                break;
            case TEXT_MAP:
                append((byte)0x0b);
                encode(value.getTextMap().getEntriesList(), this::encodeTextMapEntry);
                break;
            case RECORD:
                append((byte)0x0c);
                ValueOuterClass.Record record = value.getRecord();
                encodeProtoOptional(record.hasRecordId(), record::getRecordId, this::encodeIdentifier);
                encode(record.getFieldsList(), this::encodeRecordField);
                break;
            case VARIANT:
                append((byte)0x0d);
                ValueOuterClass.Variant variant = value.getVariant();
                encodeProtoOptional(variant.hasVariantId(), variant::getVariantId, this::encodeIdentifier);
                encode(variant.getConstructor());
                encodeValue(variant.getValue());
                break;
            case ENUM:
                append((byte)0x0e);
                ValueOuterClass.Enum enum_ = value.getEnum();
                encodeProtoOptional(enum_.hasEnumId(), enum_::getEnumId, this::encodeIdentifier);
                encode(enum_.getConstructor());
                break;
            case GEN_MAP:
                append((byte)0x0f);
                encode(value.getGenMap().getEntriesList(), this::encodeGenMapEntry);
                break;
            default:
                throw new IllegalStateException("Unsuppported value type: " + value.getSumCase());
        }
    }

    private void encodeTextMapEntry(ValueOuterClass.TextMap.Entry entry) throws NoSuchAlgorithmException {
        encode(entry.getKey());
        encodeValue(entry.getValue());
    }

    private void encodeRecordField(ValueOuterClass.RecordField field) throws NoSuchAlgorithmException {
        encode(Optional.of(field.getLabel()), this::encode);
        encodeValue(field.getValue());
    }

    private void encodeGenMapEntry(ValueOuterClass.GenMap.Entry entry) throws NoSuchAlgorithmException {
        encodeValue(entry.getKey());
        encodeValue(entry.getValue());
    }

    private void encodeInputContract(InteractiveSubmissionServiceOuterClass.Metadata.InputContract inputContract) throws NoSuchAlgorithmException {
        append(inputContract.getCreatedAt());
        hashed(() -> encodeCreatedNode(inputContract.getV1(), Optional.empty()));
    }

    private void encodeNodeById(String id) throws NoSuchAlgorithmException {
        InteractiveSubmissionServiceOuterClass.DamlTransaction.Node node = this.nodesById.get(id);
        assert node != null;
        hashed(() -> encodeNode(node));
    }

    private void encodeTransaction(InteractiveSubmissionServiceOuterClass.DamlTransaction transaction) throws NoSuchAlgorithmException {
        append(PREPARED_TRANSACTION_HASH_PURPOSE);
        encode(transaction.getVersion());
        encode(transaction.getRootsList(), this::encodeNodeById);
    }

    private void encodeNode(InteractiveSubmissionServiceOuterClass.DamlTransaction.Node node) throws NoSuchAlgorithmException {
        var v1 = node.getV1();
        ByteString seed = this.nodeSeedsById.get(node.getNodeId()).getSeed();
        switch (v1.getNodeTypeCase()) {
            case CREATE:
                encodeCreatedNode(v1.getCreate(), Optional.of(seed));
                break;
            case FETCH:
                encodeFetchNode(v1.getFetch());
                break;
            case EXERCISE:
                encodeExerciseNode(v1.getExercise(), seed);
                break;
            case ROLLBACK:
                encodeRollbackNode(v1.getRollback());
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
