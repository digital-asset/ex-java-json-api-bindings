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

import com.example.client.scanProxy.api.ScanProxyApi;
import com.example.client.scanProxy.invoker.ApiClient;
import com.example.client.scanProxy.invoker.ApiException;
import com.example.client.scanProxy.model.GetDsoPartyIdResponse;

public class ScanProxy {

    private final ScanProxyApi scanProxyApi;

    public ScanProxy(String scanProxyBaseUrl, String bearerToken) {

        ApiClient client = new ApiClient();
        client.setBasePath(scanProxyBaseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds
        if (!bearerToken.isEmpty())
            client.setBearerToken(bearerToken);

        this.scanProxyApi = new ScanProxyApi(client);
    }

    public String getDsoPartyId() throws ApiException {
        GetDsoPartyIdResponse response = this.scanProxyApi.getDsoPartyId();
        return response.getDsoPartyId();
    }
}
