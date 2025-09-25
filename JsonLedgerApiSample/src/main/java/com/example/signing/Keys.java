/*
 * Copyright (c) 2025, by Digital Asset
 *
 * Permission to use, copy, modify, and/or distribute this software for any
 * purpose with or without fee is hereby granted.
 *
 * THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH
 * REGARD TO THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY
 * AND FITNESS. IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT,
 * INDIRECT, OR CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM
 * LOSS OF USE, DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR
 * OTHER TORTIOUS ACTION, ARISING OUT OF OR IN CONNECTION WITH THE USE OR
 * PERFORMANCE OF THIS SOFTWARE.
 */

package com.example.signing;

import java.security.*;
import java.security.spec.InvalidKeySpecException;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

public class Keys {

    static byte[] derPublicKeyHeader = {
            0x30, 0x2A, 0x30, 0x05, 0x06, 0x03, 0x2B, 0x65, 0x70, 0x03, 0x21, 0x00
    };
    static byte[] derPrivateKeyHeader = {
            0x30, 0x2e, 0x02, 0x01, 0x00, 0x30, 0x05, 0x06, 0x03, 0x2b, 0x65, 0x70, 0x04, 0x22, 0x04, 0x20
    };

    public static KeyPair generate() throws NoSuchAlgorithmException {
        KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
        return keyGen.generateKeyPair();
    }

    public static KeyPair createFromRawBase64(String publicRawBase64, String privatePublicRawBase64) throws NoSuchAlgorithmException, InvalidKeySpecException {
        String publicKeyString = addPublicKeyDerHeader(publicRawBase64);
        String privateKeyString = addPrivateKeyDerHeader(stripPublicKey(privatePublicRawBase64));
        KeyFactory keyFactory = KeyFactory.getInstance("Ed25519");

        byte[] publicKeyBytes = Base64.getDecoder().decode(publicKeyString);
        X509EncodedKeySpec publicKeySpec = new X509EncodedKeySpec(publicKeyBytes);
        PublicKey publicKey = keyFactory.generatePublic(publicKeySpec);

        byte[] privateKeyBytes = Base64.getDecoder().decode(privateKeyString);
        PKCS8EncodedKeySpec privateKeySpec = new PKCS8EncodedKeySpec(privateKeyBytes);
        PrivateKey privateKey = keyFactory.generatePrivate(privateKeySpec);

        return new KeyPair(publicKey, privateKey);
    }

    public static void printKeyPairDetails(String relatedPartyHint, KeyPair keyPair) {

        System.out.println("\n==================== KEY FOR " + relatedPartyHint + " ============");
        System.out.println("Public key");
        System.out.println("             algorithm: " + keyPair.getPublic().getAlgorithm());
        System.out.println("                format: " + keyPair.getPublic().getFormat());
        System.out.println("        (Java, base64): " + Encode.toBase64String(keyPair.getPublic().getEncoded()));
        System.out.println("         (raw, base64): " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())));
        System.out.println("            (raw, hex): " + Encode.toHexString(Keys.toRawBytes(keyPair.getPublic())));
        System.out.println("Private key");
        System.out.println("             algorithm: " + keyPair.getPrivate().getAlgorithm());
        System.out.println("                format: " + keyPair.getPrivate().getFormat());
        System.out.println("        (Java, base64): " + Encode.toBase64String(keyPair.getPrivate().getEncoded()));
        System.out.println("(raw + public, base64): " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));
        System.out.println("   (raw + public, hex): " + Encode.toHexString(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));
        System.out.println();
    }

    public static void printKeyPairSummary(String relatedPartyHint, KeyPair keyPair) {
        System.out.println(relatedPartyHint + " public key:  " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())));
        System.out.println(relatedPartyHint + " private key: " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));
        System.out.println();
    }

    public static KeyPair createAndValidateKeyPair(String publicKeyReference, String privateKeyReference) throws NoSuchAlgorithmException, InvalidKeySpecException {

        KeyPair keyPair = createFromRawBase64(publicKeyReference, privateKeyReference);

        if (!publicKeyReference.equals(Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())))) {
            throw new IllegalArgumentException("Conversion error with public keys.");
        }

        if (!privateKeyReference.equals(Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())))) {
            throw new IllegalArgumentException("Conversion error with private keys.");
        }

        return keyPair;
    }

    public static String signHex(PrivateKey privateKey, String inputString) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        byte[] inputBytes = Encode.fromHexString(inputString);
        byte[] signature = signBytes(privateKey, inputBytes);
        return Encode.toHexString(signature);
    }

    public static String signBase64(PrivateKey privateKey, String inputString) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        byte[] inputBytes = Encode.fromBase64String(inputString);
        byte[] signature = signBytes(privateKey, inputBytes);
        return Encode.toBase64String(signature);
    }

    private static byte[] signBytes(PrivateKey privateKey, byte[] inputBytes) throws NoSuchAlgorithmException, InvalidKeyException, SignatureException {
        Signature signer = Signature.getInstance("Ed25519");
        signer.initSign(privateKey);
        signer.update(inputBytes);
        return signer.sign();
    }

    private static byte[] uint32ToByteArray(long value) {
        byte[] buf = new byte[4];
        buf[0] = (byte) (value >>> 24);
        buf[1] = (byte) (value >>> 16);
        buf[2] = (byte) (value >>> 8);
        buf[3] = (byte) (value >>> 0);
        return buf;
    }

    public static byte[] fingerPrintOf(PublicKey key) throws NoSuchAlgorithmException {

        byte[] purposeBytes = uint32ToByteArray(12L);
        byte[] digestInput = ByteArrayBuilder.concat(purposeBytes, toRawBytes(key));

        byte[] digestOutput = Sha256.hash(digestInput);
        byte[] hashPrefix = new byte[]{0x12, 0x20};
        return ByteArrayBuilder.concat(hashPrefix, digestOutput);
    }

    public static byte[] toRawBytes(PrivateKey key) {
        byte[] keyBytes = key.getEncoded();
        if (keyBytes.length != derPrivateKeyHeader.length + 32) {
            throw new IllegalArgumentException("unexpected key length");
        }
        byte[] keyBytesRaw = new byte[32];
        System.arraycopy(keyBytes, derPrivateKeyHeader.length, keyBytesRaw, 0, 32);
        return keyBytesRaw;
    }

    public static byte[] toRawBytes(PublicKey key) {
        byte[] keyBytes = key.getEncoded();
        if (keyBytes.length != derPublicKeyHeader.length + 32) {
            throw new IllegalArgumentException("unexpected key length");
        }
        byte[] keyBytesRaw = new byte[32];
        System.arraycopy(keyBytes, derPublicKeyHeader.length, keyBytesRaw, 0, 32);
        return keyBytesRaw;
    }

    public static byte[] toRawBytes(PrivateKey privateKey, PublicKey publicKey) {
        byte[] keyBytesPrivate = toRawBytes(privateKey);
        byte[] keyBytesPublic = toRawBytes(publicKey);
        return ByteArrayBuilder.concat(keyBytesPrivate, keyBytesPublic);
    }

    static String stripPublicKey(String privateKeyBase64) {
        byte[] keyBytes = Encode.fromBase64String(privateKeyBase64);
        if (keyBytes.length == 32) {
            return privateKeyBase64; // Already raw format
        } else if (keyBytes.length == 64) {
            byte[] rawKeyBytes = new byte[32];
            System.arraycopy(keyBytes, 0, rawKeyBytes, 0, 32);
            return Encode.toBase64String(rawKeyBytes);
        } else {
            throw new IllegalArgumentException("Invalid key length of " + keyBytes.length);
        }
    }

    static String addPublicKeyDerHeader(String base64String) {
        byte[] keyBytes = Encode.fromBase64String(base64String);
        byte[] withHeader = ByteArrayBuilder.concat(derPublicKeyHeader, keyBytes);
        return Encode.toBase64String(withHeader);
    }

    static String addPrivateKeyDerHeader(String base64String) {
        byte[] keyBytes = Encode.fromBase64String(base64String);
        byte[] withHeader = ByteArrayBuilder.concat(derPrivateKeyHeader, keyBytes);
        return Encode.toBase64String(withHeader);
    }
}
