package com.example.signing;

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

    public ByteArrayBuilder append(byte b) {
        this.byteArrays.add(new byte[] { b });
        this.length++;
        return this;
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
