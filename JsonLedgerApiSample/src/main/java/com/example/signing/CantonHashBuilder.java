package com.example.signing;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.function.Supplier;

public abstract class CantonHashBuilder extends HashWriter {
    public CantonHashBuilder() {
        super();
    }

    protected void encode(byte[] bytes) {
        append(bytes.length);
        append(bytes);
    }

    protected void encode(String s) {
        encode(s.getBytes(StandardCharsets.UTF_8));
    }

    protected void encodeHex(String s) {
        encode(Encode.fromHexString(s));
    }

    protected <T> void encode(Optional<T> opt, EncodeCallback<T> callback) {
        if (opt.isEmpty()) {
            append((byte) 0);
        } else {
            append((byte) 1);
            callback.call(opt.get());
        }
    }

    protected <T> void encode(List<T> list, EncodeCallback<T> callback) {
        append(list.size());
        for (T item : list) {
            callback.call(item);
        }
    }

    protected <T> void encode(T[] array, EncodeCallback<T> callback) {
        append(array.length);
        for (T item : array) {
            callback.call(item);
        }
    }

    protected <T> void encodeProtoOptional(boolean isPresent, Supplier<T> getValue, EncodeCallback<T> callback) {
        if (isPresent) {
            append((byte) 1);
            callback.call(getValue.get());
        } else {
            append((byte) 0);
        }
    }

    public abstract byte[] hash();

    protected interface EncodeCallback<T> {
        void call(T item);
    }
}
