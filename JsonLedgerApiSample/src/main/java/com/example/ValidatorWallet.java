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

import com.example.client.wallet.internal.api.WalletApi;
import com.example.client.wallet.internal.invoker.ApiClient;
import com.example.client.wallet.internal.invoker.ApiException;
import com.example.client.wallet.internal.model.*;

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.OffsetDateTime;
import java.util.List;

public class ValidatorWallet {

    private final WalletApi walletApi;

    public ValidatorWallet(String baseUrl) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        this.walletApi = new WalletApi(client);
    }

    public void tap(double amount) throws ApiException {
        TapRequest request = new TapRequest();
        request.setAmount(amount + "");

        System.out.println("\ntap request: " + request.toJson() + "\n");
        TapResponse response = walletApi.tap(request);
        System.out.println("\ntap response: " + response.toJson() + "\n");
    }
}
