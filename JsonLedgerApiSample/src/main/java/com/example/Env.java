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

public class Env {

    public static String LEDGER_API_URL = getenv("LEDGER_API_URL", "http://wallet.localhost/api/participant");
    public static String VALIDATOR_API_URL = getenv("VALIDATOR_API_URL", "http://wallet.localhost/api/validator");
    public static String SCAN_PROXY_API_URL = getenv("SCAN_PROXY_API_URL", Env.VALIDATOR_API_URL);
    public static String SCAN_API_URL = getenv("SCAN_API_URL", "");

    public static String VALIDATOR_TOKEN = getenv("VALIDATOR_TOKEN", "");

    public static String TREASURY_PARTY_HINT = getenv("TREASURY_PARTY_HINT", "treasury");
    public static String TREASURY_PARTY = getenv("TREASURY_PARTY", "");
    public static String TREASURY_TOKEN = getenv("TREASURY_TOKEN", "");

    public static String SENDER_PARTY_HINT = getenv("SENDER_PARTY_HINT", "alice");
    public static String SENDER_PARTY = getenv("SENDER_PARTY", "");
    public static String SENDER_TOKEN = getenv("SENDER_TOKEN", "");
    public static String SENDER_PUBLIC_KEY = getenv("SENDER_PUBLIC_KEY", "");
    public static String SENDER_PRIVATE_KEY = getenv("SENDER_PRIVATE_KEY", "");

    public static String TRANSFER_AMOUNT = getenv("TRANSFER_AMOUNT", "100");
    public static String SYNCHRONIZER_ID = "";
    public static String DSO_PARTY = "";
    public static String VALIDATOR_PARTY = "";

    private static String getenv(String name, String defaultValue) {
        String envValue = System.getenv(name);
        return envValue != null ? envValue : defaultValue;
    }
}