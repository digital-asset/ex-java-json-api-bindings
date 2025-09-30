package com.example.signing;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class Sha256 {

    public static byte[] hash(byte[] bytes) {
        return new Sha256().append(bytes).hash();
    }

    private final MessageDigest messageDigest;

    public Sha256() {
        try {
            this.messageDigest = MessageDigest.getInstance("SHA-256");
        } catch (NoSuchAlgorithmException algorithmException) {
            throw new IllegalStateException("SHA256 algorithm was not available", algorithmException);
        }
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
