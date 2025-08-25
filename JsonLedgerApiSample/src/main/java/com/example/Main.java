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

public class Main {
    public static void main(String[] args) {
        try {
            Ledger ledgerApi = new Ledger("http://wallet.localhost/api/participant");
            System.out.println("Version: " + ledgerApi.getVersion());
            Validator validatorApi = new Validator("http://wallet.localhost/api/validator");
            System.out.println("Party: " + validatorApi.getValidatorParty());
        }
        catch(Exception ex)
        {
            System.out.println(ex.getMessage());
            System.exit(1);
        }
    }
}