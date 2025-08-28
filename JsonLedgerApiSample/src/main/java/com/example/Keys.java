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

package com.example;

import java.security.*;
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

    public static KeyPair generate() {
        try {
            KeyPairGenerator keyGen = KeyPairGenerator.getInstance("Ed25519");
            return keyGen.generateKeyPair();
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return null;
        }
    }

    public static KeyPair createFromRawBase64(String publicRawBase64, String privatePublicRawBase64) throws Exception {
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

    public static void printKeyPair(String relatedPartyHint, KeyPair keyPair) throws Exception {

        System.out.println("==================== KEY FOR " + relatedPartyHint + " ============");
        System.out.println("Public key algorithm: " + keyPair.getPublic().getAlgorithm());
        System.out.println("              format: " + keyPair.getPublic().getFormat());
        System.out.println("      (Java, base64): " + Encode.toBase64String(keyPair.getPublic().getEncoded()));
        System.out.println("       (raw, base64): " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())));
        System.out.println("          (raw, hex): " + Encode.toHexString(Keys.toRawBytes(keyPair.getPublic())));

        System.out.println(" Private key algorithm: " + keyPair.getPrivate().getAlgorithm());
        System.out.println("                format: " + keyPair.getPrivate().getFormat());
        System.out.println("        (Java, base64): " + Encode.toBase64String(keyPair.getPrivate().getEncoded()));
        System.out.println("(raw + public, base64): " + Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));
        System.out.println("   (raw + public, hex): " + Encode.toHexString(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())));
        System.out.println();
    }

    public static KeyPair createAndValidateKeyPair(String relatedPartyHint, String publicKeyReference, String privateKeyReference) throws Exception {

        KeyPair keyPair = createFromRawBase64(publicKeyReference, privateKeyReference);

        printKeyPair(relatedPartyHint, keyPair);

        if (!publicKeyReference.equals(Encode.toBase64String(Keys.toRawBytes(keyPair.getPublic())))) {
            throw new Exception("Conversion error with public keys.");
        }

        if (!privateKeyReference.equals(Encode.toBase64String(Keys.toRawBytes(keyPair.getPrivate(), keyPair.getPublic())))) {
            throw new Exception("Conversion error with private keys.");
        }

        return keyPair;
    }

    public static String sign(PrivateKey privateKey, String inputString) {
        try {
            Signature signer = Signature.getInstance("Ed25519");
            signer.initSign(privateKey);
            signer.update(Encode.fromHexString(inputString));
            byte[] signature = signer.sign();
            return Encode.toHexString(signature);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return null;
        }
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
        byte[] keyBytesRaw = new byte[64];
        System.arraycopy(keyBytesPrivate, 0, keyBytesRaw, 0, 32);
        System.arraycopy(keyBytesPublic, 0, keyBytesRaw, 32, 32);
        return keyBytesRaw;
    }

    static String stripPublicKey(String privateKeyBase64) {
        byte[] keyBytes = Base64.getDecoder().decode(privateKeyBase64);
        if (keyBytes.length == 32) {
            return privateKeyBase64; // Already raw format
        } else if (keyBytes.length == 64) {
            byte[] rawKeyBytes = new byte[32];
            System.arraycopy(keyBytes, 0, rawKeyBytes, 0, 32);
            return Base64.getEncoder().encodeToString(rawKeyBytes);
        } else {
            throw new IllegalArgumentException("Invalid key length of " + keyBytes.length);
        }
    }

    static String addPublicKeyDerHeader(String base64String) {
        byte[] keyBytes = Base64.getDecoder().decode(base64String);
        byte[] derKeyBytes = new byte[derPublicKeyHeader.length + keyBytes.length];
        System.arraycopy(derPublicKeyHeader, 0, derKeyBytes, 0, derPublicKeyHeader.length);
        System.arraycopy(keyBytes, 0, derKeyBytes, derPublicKeyHeader.length, keyBytes.length);
        return Base64.getEncoder().encodeToString(derKeyBytes);
    }

    static String addPrivateKeyDerHeader(String base64String) {
        byte[] keyBytes = Base64.getDecoder().decode(base64String);
        byte[] derKeyBytes = new byte[derPrivateKeyHeader.length + keyBytes.length];
        System.arraycopy(derPrivateKeyHeader, 0, derKeyBytes, 0, derPrivateKeyHeader.length);
        System.arraycopy(keyBytes, 0, derKeyBytes, derPrivateKeyHeader.length, keyBytes.length);
        return Base64.getEncoder().encodeToString(derKeyBytes);
    }
}
