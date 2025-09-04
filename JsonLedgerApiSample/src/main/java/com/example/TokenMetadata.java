package com.example;

import com.example.client.tokenMetadata.invoker.ApiException;
import com.example.client.tokenMetadata.api.DefaultApi;
import com.example.client.tokenMetadata.invoker.ApiClient;
import com.example.client.tokenMetadata.model.GetRegistryInfoResponse;

public class TokenMetadata {

    private final DefaultApi tokenMetadataApi;

    public TokenMetadata(String scanBaseUrl) {

        ApiClient client = new ApiClient();
        client.setBasePath(scanBaseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds

        this.tokenMetadataApi = new DefaultApi(client);
    }

    public GetRegistryInfoResponse getRegistryInfo() throws ApiException {
        return this.tokenMetadataApi.getRegistryInfo();
    }
}
