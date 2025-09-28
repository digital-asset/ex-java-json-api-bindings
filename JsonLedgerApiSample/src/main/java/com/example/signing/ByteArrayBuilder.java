package com.example.signing;

import com.google.protobuf.ByteString;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

public class ByteArrayBuilder {

    public static byte[] concat(byte[] lhs, byte[] rhs) {
        return new ByteArrayBuilder()
                .append(lhs)
                .append(rhs)
                .build();
    }

    private List<byte[]> byteArrays;
    private int length;

    public ByteArrayBuilder() {
        this.byteArrays = new ArrayList<>();
        this.length = 0;
    }

    public ByteArrayBuilder append(byte[] bytes) {
        this.byteArrays.add(bytes);
        this.length += bytes.length;
        return this;
    }

    public ByteArrayBuilder append(ByteString byteString) {
        return this.append(byteString.toByteArray());
    }

    public ByteArrayBuilder append(byte b) {
        return this.append(new byte[] { b });
    }

    public ByteArrayBuilder append(boolean value) {
        return value ? this.append((byte)1) : this.append((byte)0);
    }

    public ByteArrayBuilder append(int i, ByteOrder byteOrder) {
        ByteBuffer buf = ByteBuffer
                .allocate(4)
                .order(byteOrder)
                .putInt(i);

        return this.append(buf.array());
    }

    public ByteArrayBuilder append(int i) {
        return this.append(i, ByteOrder.BIG_ENDIAN);
    }

    public ByteArrayBuilder append(long l, ByteOrder byteOrder) {
        ByteBuffer buf = ByteBuffer
                .allocate(8)
                .order(byteOrder)
                .putLong(l);

        return this.append(buf.array());
    }

    public ByteArrayBuilder append(long l) {
        return this.append(l, ByteOrder.BIG_ENDIAN);
    }

    public ByteArrayBuilder append(String s) {
        return this.append(s.getBytes(StandardCharsets.UTF_8));
    }

    public byte[] build() {
        byte[] result = new byte[this.length];
        int offset = 0;

        for (byte[] byteArray : this.byteArrays) {
            System.arraycopy(byteArray, 0, result, offset, byteArray.length);
            offset += byteArray.length;
        }

        return result;
    }
}
