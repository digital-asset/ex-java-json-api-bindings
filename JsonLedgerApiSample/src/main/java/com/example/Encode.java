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

import java.security.Key;
import java.util.Base64;
import java.util.Formatter;

public class Encode {

    public static String toHexString(byte[] bytes) {
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    public static String toHexString(Key key) {
        return toHexString(key.getEncoded());
    }

    public static byte[] fromHexString(String hex) {
        if (hex == null || hex.length() % 2 != 0) {
            throw new IllegalArgumentException("Invalid hexadecimal string");
        }

        int length = hex.length();
        byte[] bytes = new byte[length / 2];
        for (int i = 0; i < length; i += 2) {
            bytes[i / 2] = (byte) ((Character.digit(hex.charAt(i), 16) << 4)
                    + Character.digit(hex.charAt(i + 1), 16));
        }
        return bytes;
    }

    public static String toEd25519HexString(Key key) {
        byte[] encodedBytes = key.getEncoded();
        byte[] rawKeyBytes = new byte[32];
        System.arraycopy(bytes, bytes.length - 32, rawKeyBytes, 0, 32);
        return toHexString(encodedBytes);
    }

    public static String toBase64String(Key key) {
        return Base64.getEncoder().encodeToString(key.getEncoded());
    }

    public static String fromBase64String(String s) {
        byte[] decodedBytes = Base64.getDecoder().decode(s);
        return new String(decodedBytes, StandardCharsets.UTF_8);
    }
}
