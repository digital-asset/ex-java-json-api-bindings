package com.example.signing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256 {

    public static byte[] hash(byte[] bytes) throws NoSuchAlgorithmException {
        return new Sha256().append(bytes).hash();
    }

    private final MessageDigest messageDigest;

    public Sha256() throws NoSuchAlgorithmException {
        this.messageDigest = MessageDigest.getInstance("SHA-256");
    }

    public Sha256 append(byte[] bytes) {
        messageDigest.update(bytes);
        return this;
    }

    public Sha256 append(byte b) {
        messageDigest.update(b);
        return this;
    }

    public byte[] hash() {
        return messageDigest.digest();
    }
}
