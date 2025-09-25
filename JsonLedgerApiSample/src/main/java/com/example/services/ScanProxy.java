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

import com.example.access.LedgerUser;
import com.example.client.scanProxy.api.ScanProxyApi;
import com.example.client.scanProxy.invoker.ApiClient;
import com.example.client.scanProxy.invoker.ApiException;
import com.example.client.scanProxy.model.ContractWithState;
import com.example.client.scanProxy.model.GetDsoPartyIdResponse;
import com.example.client.scanProxy.model.LookupTransferPreapprovalByPartyResponse;

import java.util.Optional;

public class ScanProxy {

    private final ScanProxyApi scanProxyApi;

    public ScanProxy(String scanProxyBaseUrl, LedgerUser user) {
        ApiClient client = new ApiClient();
        client.setBasePath(scanProxyBaseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds
        client.setBearerToken(user.bearerToken());
        this.scanProxyApi = new ScanProxyApi(client);
    }

    public String getDsoPartyId() throws ApiException {
        GetDsoPartyIdResponse response = this.scanProxyApi.getDsoPartyId();
        return response.getDsoPartyId();
    }

    public Optional<ContractWithState> getTransferPreapproval(String partyId) throws ApiException {
        LookupTransferPreapprovalByPartyResponse response;
        try {
            response = this.scanProxyApi.lookupTransferPreapprovalByParty(partyId);
        } catch (ApiException ex) {
            if (ex.getCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }

        return Optional.of(response.getTransferPreapproval());
    }
}
