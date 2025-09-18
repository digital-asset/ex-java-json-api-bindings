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

package com.example.services;

import com.example.client.scan.api.ScanApi;
import com.example.client.scan.invoker.ApiClient;
import com.example.client.scan.invoker.ApiException;
import com.example.client.scan.model.DomainScans;

import java.net.URI;
import java.net.URISyntaxException;

public class Scan {

    private final ScanApi scanApi;

    public Scan(String scanHostUrl) throws URISyntaxException {

        ApiClient client = new ApiClient();
        client.setReadTimeout(60 * 1000); // 60 seconds

        URI scanBaseUrl = (new URI(scanHostUrl)).resolve("/api/scan");
        client.setBasePath(scanBaseUrl.toString());

        this.scanApi = new ScanApi(client);
    }

    public String getSynchronizerId() throws ApiException {
        DomainScans domainScans = this.scanApi.listDsoScans()
                .getScans().stream().findFirst().orElseThrow();
        return domainScans.getDomainId();
    }
}
