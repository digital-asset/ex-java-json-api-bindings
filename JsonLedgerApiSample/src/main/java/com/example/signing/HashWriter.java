package com.example.signing;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;

public class HashWriter {

    public interface Hashed {
        void writeHashItems() throws NoSuchAlgorithmException;
    }

    ByteArrayBuilder currentContext;

    public HashWriter() {
        this.currentContext = new ByteArrayBuilder();
    }

    public void hashed(Hashed callback) throws NoSuchAlgorithmException {

        ByteArrayBuilder prev = this.currentContext;
        this.currentContext = new ByteArrayBuilder();

        callback.writeHashItems();

        byte[] hashedContext = Sha256.hash(this.currentContext.build());
        this.currentContext = prev.append(hashedContext);
    }

    public void append(byte[] bytes) {
        this.currentContext.append(bytes);
    }

    public void append(ByteString byteString) {
        this.currentContext.append(byteString);
    }

    public void append(byte b) {
        this.currentContext.append(b);
    }

    public void append(boolean value) {
        this.currentContext.append(value);
    }

    public void append(int i, ByteOrder byteOrder) {
        this.currentContext.append(i, byteOrder);
    }

    public void append(int i) {
        this.currentContext.append(i);
    }

    public void append(long l, ByteOrder byteOrder) {
        this.currentContext.append(l, byteOrder);
    }

    public void append(long l) {
        this.currentContext.append(l);
    }

    public void append(String s) {
        this.currentContext.append(s);
    }

    public byte[] finish() {
        return this.currentContext.build();
    }
}
