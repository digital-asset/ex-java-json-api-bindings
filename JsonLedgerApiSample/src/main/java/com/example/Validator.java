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

    public Validator(String baseUrl) {

        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds

        this.validatorApi = new ValidatorApi(client);
        this.validatorPublicApi = new ValidatorPublicApi(client);
    }

    // does not require authentication
    public String getValidatorParty() throws ApiException {
        GetValidatorUserInfoResponse response = this.validatorPublicApi.getValidatorUserInfo();
        return response.getPartyId();
    }

    // requires authentication
    public List<String> listUsers(String bearerToken) throws ApiException {
        this.validatorApi.getApiClient().setBearerToken(bearerToken);
        ListUsersResponse response = this.validatorApi.listUsers();
        return response.getUsernames();
    }

    public List<TopologyTx> prepareOnboarding(String bearerToken, String partyHint, PublicKey publicKey) throws ApiException {
        String publicKeyHex = Encode.toHexString(Keys.toRawBytes(publicKey));
        GenerateExternalPartyTopologyRequest request = new GenerateExternalPartyTopologyRequest();
        request.setPartyHint(partyHint);
        request.setPublicKey(publicKeyHex);
        this.validatorApi.getApiClient().setBearerToken(bearerToken);
        GenerateExternalPartyTopologyResponse response = this.validatorApi.generateExternalPartyTopology(request);
//        System.out.println("\nNew party: " + response.getPartyId());
//        System.out.println("\ngenerate response: " + response.toJson());
        return response.getTopologyTxs();
    }

    public String submitOnboarding(String bearerToken, List<SignedTopologyTx> signedTxs, PublicKey publicKey) throws ApiException {
        String publicKeyHex = Encode.toHexString(Keys.toRawBytes(publicKey));
        SubmitExternalPartyTopologyRequest request = new SubmitExternalPartyTopologyRequest();
        request.setSignedTopologyTxs(signedTxs);
        request.setPublicKey(publicKeyHex);
//        System.out.println("\nsubmit onboarding request: " + request.toJson() + "\n");
        this.validatorApi.getApiClient().setBearerToken(bearerToken);
        SubmitExternalPartyTopologyResponse response = this.validatorApi.submitExternalPartyTopology(request);
//        System.out.println("\nsubmit onboarding response: " + response.toJson() + "\n");
        return response.getPartyId();
    }

    public CreateExternalPartySetupProposalResponse createExternalPartySetupProposal(String bearerToken, String partyId) throws ApiException {
        CreateExternalPartySetupProposalRequest request = new CreateExternalPartySetupProposalRequest();
        request.setUserPartyId(partyId);

//        System.out.println("\ncreate external party setup proposal request: " + request.toJson() + "\n");
        this.validatorApi.getApiClient().setBearerToken(bearerToken);
        CreateExternalPartySetupProposalResponse response = this.validatorApi.createExternalPartySetupProposal(request);
//        System.out.println("\ncreate external party setup proposal response: " + response.toJson() + "\n");
        return response;
    }

    public PrepareAcceptExternalPartySetupProposalResponse prepareAcceptExternalPartySetupProposal(String partyId, String contractId) throws ApiException {
        PrepareAcceptExternalPartySetupProposalRequest request = new PrepareAcceptExternalPartySetupProposalRequest();
        request.setUserPartyId(partyId);
        request.setContractId(contractId);
        return this.validatorApi.prepareAcceptExternalPartySetupProposal(request);
    }

    public SubmitAcceptExternalPartySetupProposalResponse submitAcceptExternalPartySetupProposal(String bearerToken, ExternalPartySubmission acceptSubmission) throws ApiException {
        SubmitAcceptExternalPartySetupProposalRequest request = new SubmitAcceptExternalPartySetupProposalRequest();
        request.setSubmission(acceptSubmission);

//        System.out.println("\nsubmit acceptance of external party setup proposal request: " + request.toJson() + "\n");
        this.validatorApi.getApiClient().setBearerToken(bearerToken);
        SubmitAcceptExternalPartySetupProposalResponse response = this.validatorApi.submitAcceptExternalPartySetupProposal(request);
//        System.out.println("\nsubmit acceptance of external party setup proposal response: " + request.toJson() + "\n");
        return response;
    }
}
