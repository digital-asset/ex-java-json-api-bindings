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

import com.example.client.validator.api.ValidatorApi;
import com.example.client.validator.api.ValidatorPublicApi;
import com.example.client.validator.invoker.ApiClient;
import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.*;

import java.security.PublicKey;
import java.util.List;

public class Validator {

    private final ValidatorApi validatorApi;
    private final ValidatorPublicApi validatorPublicApi;

    public Validator(String baseUrl, String bearerToken) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        if (!bearerToken.isEmpty())
            client.setBearerToken(bearerToken);
        this.validatorApi = new ValidatorApi(client);
        this.validatorPublicApi = new ValidatorPublicApi(client);
    }

    public String getValidatorParty() throws ApiException {
        GetValidatorUserInfoResponse response = this.validatorPublicApi.getValidatorUserInfo();
        return response.getPartyId();
    }

    public List<String> listUsers() throws ApiException {
        ListUsersResponse response = this.validatorApi.listUsers();
        return response.getUsernames();
    }

    public List<TopologyTx> prepareOnboarding(String partyHint, PublicKey publicKey) throws ApiException {
        String publicKeyHex = Keys.toEd25519HexString(publicKey);
        GenerateExternalPartyTopologyRequest request = new GenerateExternalPartyTopologyRequest();
        request.setPartyHint(partyHint);
        request.setPublicKey(publicKeyHex);
        GenerateExternalPartyTopologyResponse response = this.validatorApi.generateExternalPartyTopology(request);
        return response.getTopologyTxs();
    }

    public String submitOnboarding(List<SignedTopologyTx> signedTxs, PublicKey publicKey) throws ApiException {
        // TODO: figure out why this fails
        String publicKeyHex = Keys.toEd25519HexString(publicKey);
        SubmitExternalPartyTopologyRequest request = new SubmitExternalPartyTopologyRequest();
        request.setSignedTopologyTxs(signedTxs);
        request.setPublicKey(publicKeyHex);
        SubmitExternalPartyTopologyResponse response = this.validatorApi.submitExternalPartyTopology(request);
        return response.getPartyId();
    }
}
