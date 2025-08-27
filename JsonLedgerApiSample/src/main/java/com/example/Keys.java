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
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Formatter;

public class Keys {

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

    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        Formatter formatter = new Formatter(sb);
        for (byte b : bytes) {
            formatter.format("%02x", b);
        }
        formatter.close();
        return sb.toString();
    }

    public static String toEd25519HexString(Key key) {
        byte[] encodedBytes = key.getEncoded();
        byte[] rawKeyBytes = new byte[32];
        System.arraycopy(encodedBytes, encodedBytes.length - 32, rawKeyBytes, 0, 32);
        return toHexString(rawKeyBytes);
    }

    public static String sign(PrivateKey privateKey, String inputString) {
        try {
            Signature ecdsaSign = Signature.getInstance("Ed25519");
            ecdsaSign.initSign(privateKey);
            ecdsaSign.update(inputString.getBytes(StandardCharsets.UTF_8));
            byte[] signature = ecdsaSign.sign();
            return toHexString(signature);
        } catch (Exception e) {
            System.out.println(e.getMessage());
            System.exit(1);
            return null;
        }
    }
}
