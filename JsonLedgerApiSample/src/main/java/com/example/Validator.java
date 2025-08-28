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

import java.math.BigDecimal;
import java.security.KeyPair;
import java.security.PublicKey;
import java.time.OffsetDateTime;
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
        String publicKeyHex = Encode.toHexString(Keys.toRawBytes(publicKey));
        GenerateExternalPartyTopologyRequest request = new GenerateExternalPartyTopologyRequest();
        request.setPartyHint(partyHint);
        request.setPublicKey(publicKeyHex);
        GenerateExternalPartyTopologyResponse response = this.validatorApi.generateExternalPartyTopology(request);
        System.out.println("\nNew party: " + response.getPartyId());
        System.out.println("\ngenerate response: " + response.toJson());
        return response.getTopologyTxs();
    }

    public String submitOnboarding(List<SignedTopologyTx> signedTxs, PublicKey publicKey) throws ApiException {
        String publicKeyHex = Encode.toHexString(Keys.toRawBytes(publicKey));
        SubmitExternalPartyTopologyRequest request = new SubmitExternalPartyTopologyRequest();
        request.setSignedTopologyTxs(signedTxs);
        request.setPublicKey(publicKeyHex);
        System.out.println("\nsubmit onboarding request: " + request.toJson() + "\n");
        SubmitExternalPartyTopologyResponse response = this.validatorApi.submitExternalPartyTopology(request);
        System.out.println("\nsubmit onboarding response: " + response.toJson() + "\n");
        return response.getPartyId();
    }

    public String createExternalPartySetupProposal(String partyId) throws ApiException {
        CreateExternalPartySetupProposalRequest request = new CreateExternalPartySetupProposalRequest();
        request.setUserPartyId(partyId);

        System.out.println("\ncreate external party setup proposal request: " + request.toJson() + "\n");
        CreateExternalPartySetupProposalResponse response = this.validatorApi.createExternalPartySetupProposal(request);
        System.out.println("\ncreate external party setup proposal response: " + response.toJson() + "\n");
        return response.getContractId();
    }

    public SubmitAcceptExternalPartySetupProposalResponse preapproveTransactions(KeyPair receiverKeyPair, String senderPartyId, String receiverPartyId) throws ApiException {

        // step 1 - propose transfer pre-approval as the validator node operator
        CreateExternalPartySetupProposalRequest step1Request = new CreateExternalPartySetupProposalRequest();
        step1Request.setUserPartyId(receiverPartyId);

        System.out.println("\ncreate external party setup proposal request: " + step1Request.toJson() + "\n");
        CreateExternalPartySetupProposalResponse step1Response = this.validatorApi.createExternalPartySetupProposal(step1Request);
        System.out.println("\ncreate external party setup proposal response: " + step1Response.toJson() + "\n");

        // step 2 - prepare a transaction that accepts the pre-approval
        PrepareAcceptExternalPartySetupProposalRequest step2Request = new PrepareAcceptExternalPartySetupProposalRequest();
        step2Request.setContractId(step1Response.getContractId());
        step2Request.setUserPartyId(receiverPartyId);
        step2Request.setVerboseHashing(true); // discouraged in production use

        System.out.println("\nprepare acceptance of external party setup proposal request: " + step2Request.toJson() + "\n");
        PrepareAcceptExternalPartySetupProposalResponse step2Response = this.validatorApi.prepareAcceptExternalPartySetupProposal(step2Request);
        System.out.println("\nprepare acceptance of external party setup proposal response: " + step2Response.toJson() + "\n");

        // step 3 - submit the transaction that accepts the pre-approval
        ExternalPartySubmission step3Submission = new ExternalPartySubmission();
        step3Submission.setPartyId(receiverPartyId);
        step3Submission.setTransaction(step2Response.getTransaction());
        step3Submission.setSignedTxHash(Keys.sign(receiverKeyPair.getPrivate(), step2Response.getTxHash()));
        String publicKeyHex = Encode.toHexString(Keys.toRawBytes(receiverKeyPair.getPublic()));
        step3Submission.setPublicKey(publicKeyHex);

        SubmitAcceptExternalPartySetupProposalRequest step3Request = new SubmitAcceptExternalPartySetupProposalRequest();
        step3Request.setSubmission(step3Submission);

        System.out.println("\nsubmit acceptance of external party setup proposal request: " + step3Request.toJson() + "\n");
        SubmitAcceptExternalPartySetupProposalResponse step3Response = this.validatorApi.submitAcceptExternalPartySetupProposal(step3Request);
        System.out.println("\nsubmit acceptance of external party setup proposal response: " + step3Response.toJson() + "\n");

        return step3Response;
    }

    public String sendWithPreApproval(KeyPair senderKeyPair, String senderPartyId, String receiverPartyId, double amount, int nonce) throws ApiException {

        // step 1 - prepare a transaction that sends canton coin
        PrepareTransferPreapprovalSendRequest step1Request = new PrepareTransferPreapprovalSendRequest();
        step1Request.setSenderPartyId(senderPartyId);
        step1Request.setReceiverPartyId(receiverPartyId);
        step1Request.setAmount(new BigDecimal(amount));
        step1Request.setExpiresAt(OffsetDateTime.now().plusMinutes(3));
        step1Request.setNonce(Long.valueOf(nonce));
        step1Request.setVerboseHashing(true); // discouraged in production use

        System.out.println("\nprepare acceptance of external party setup proposal request: " + step1Request.toJson() + "\n");
        PrepareTransferPreapprovalSendResponse step1Response = this.validatorApi.prepareTransferPreapprovalSend(step1Request);
        System.out.println("\nprepare acceptance of external party setup proposal response: " + step1Response.toJson() + "\n");

        // step 2 - submit the transaction that sends canton coin
        ExternalPartySubmission step2Submission = new ExternalPartySubmission();
        step2Submission.setPartyId(senderPartyId);
        step2Submission.setTransaction(step1Response.getTransaction());
        step2Submission.setSignedTxHash(Keys.sign(senderKeyPair.getPrivate(), step1Response.getTxHash()));
        String publicKeyHex = Encode.toHexString(Keys.toRawBytes(senderKeyPair.getPublic()));
        step2Submission.setPublicKey(publicKeyHex);

        SubmitTransferPreapprovalSendRequest step2Request = new SubmitTransferPreapprovalSendRequest();
        step2Request.setSubmission(step2Submission);

        System.out.println("\nsubmit acceptance of external party setup proposal request: " + step2Request.toJson() + "\n");
        SubmitTransferPreapprovalSendResponse step2Response = this.validatorApi.submitTransferPreapprovalSend(step2Request);
        System.out.println("\nsubmit acceptance of external party setup proposal response: " + step2Response.toJson() + "\n");

        return step2Response.getUpdateId();
    }
}
