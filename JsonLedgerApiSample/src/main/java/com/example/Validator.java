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

import java.security.KeyPair;
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

    public String onboard(String partyHint, KeyPair keyPair) throws ApiException {
        String publicKey = Keys.toEd25519HexString(keyPair.getPublic());
        GenerateExternalPartyTopologyRequest request1 = new GenerateExternalPartyTopologyRequest();
        request1.setPartyHint(partyHint);
        request1.setPublicKey(publicKey);
        GenerateExternalPartyTopologyResponse response1 = this.validatorApi.generateExternalPartyTopology(request1);

        // TODO: move into a separate user signing method
        List<SignedTopologyTx> signedTxs = response1.getTopologyTxs().stream().map(tx -> {
            String signedHash = Keys.sign(keyPair.getPrivate(), tx.getHash());
            SignedTopologyTx signedTx = new SignedTopologyTx();
            signedTx.setTopologyTx(tx.getTopologyTx());
            signedTx.setSignedHash(signedHash);
            return signedTx;
        }).toList();

        // TODO: figure out why this fails
        SubmitExternalPartyTopologyRequest request2 = new SubmitExternalPartyTopologyRequest();
        request2.setSignedTopologyTxs(signedTxs);
        request2.setPublicKey(publicKey);
        SubmitExternalPartyTopologyResponse response2 = this.validatorApi.submitExternalPartyTopology(request2);
        return response2.getPartyId();
    }
}
