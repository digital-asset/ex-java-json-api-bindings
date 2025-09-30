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

import com.daml.ledger.api.v2.interactive.InteractiveSubmissionServiceOuterClass;
import com.example.GsonTypeAdapters.*;
import com.daml.ledger.javaapi.data.TransactionShape;
import com.example.access.LedgerUser;
import com.example.client.ledger.api.DefaultApi;
import com.example.client.ledger.invoker.ApiClient;
import com.example.client.ledger.invoker.ApiException;
import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.*;
import com.example.client.ledger.model.Signature;
import com.example.models.TemplateId;
import com.example.signing.Encode;
import com.example.signing.Keys;
import com.example.signing.TransactionHashBuilder;
import com.google.protobuf.InvalidProtocolBufferException;
import org.jetbrains.annotations.NotNull;
import splice.api.token.metadatav1.anyvalue.AV_ContractId;

import java.security.*;
import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Ledger {

    static {
        JSON.setGson(ExtendedJson.gson);
    }

    private final DefaultApi ledgerApi;
    private final LedgerUser user;

    public Ledger(String baseUrl, LedgerUser user) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds
        client.setBearerToken(user.bearerToken());

        this.ledgerApi = new DefaultApi(client);
        this.user = user;
    }

    public static CumulativeFilter wildcardFilter() {

        /* Sample JSON
          {"identifierFilter": {"WildcardFilter": {"value": {"includeCreatedEventBlob": false}}}},
         */

        WildcardFilter wildcardFilter = new WildcardFilter()
                .value(new WildcardFilter1()
                        .includeCreatedEventBlob(false)
                );

        IdentifierFilterOneOf3 identifierFilterOneOf3 = new IdentifierFilterOneOf3()
                .wildcardFilter(wildcardFilter);

        IdentifierFilter identifierFilter = new IdentifierFilter();
        identifierFilter.setActualInstance(identifierFilterOneOf3);

        return new CumulativeFilter()
                .identifierFilter(identifierFilter);
    }

    public static CumulativeFilter createFilterByInterface(TemplateId interfaceId) {

        /* Sample JSON
         {
           "identifierFilter": {
             "InterfaceFilter": {
               "value": {
                 "interfaceId": "#splice-api-token-holding-v1:Splice.Api.Token.HoldingV1:Holding",
                 "includeInterfaceView": true,
                 "includeCreatedEventBlob": false
               }
             }
           }
         }
         */

        InterfaceFilter1 interfaceFilter1 = new InterfaceFilter1()
                .includeCreatedEventBlob(false)
                .includeInterfaceView(true)
                .interfaceId(interfaceId.getRaw());

        InterfaceFilter interfaceFilter = new InterfaceFilter()
                .value(interfaceFilter1);

        IdentifierFilterOneOf1 identifierFilterOneOf1 = new IdentifierFilterOneOf1()
                .interfaceFilter(interfaceFilter);

        IdentifierFilter identifierFilter = new IdentifierFilter();
        identifierFilter.setActualInstance(identifierFilterOneOf1);

        return new CumulativeFilter()
                .identifierFilter(identifierFilter);
    }

    public static CumulativeFilter createFilterByTemplate(TemplateId templateId) {

        /* Sample JSON
         {
           "identifierFilter": {
             "TemplateFilter": {
               "value": {
                 "templateId": "#splice-amulet:Splice.AmuletRules:TransferPreapproval",
                 "includeCreatedEventBlob": true
               }
             }
           }
         }
         */

        TemplateFilter1 templateFilter1 = new TemplateFilter1()
                .templateId(templateId.getRaw())
                .includeCreatedEventBlob(true);

        TemplateFilter templateFilter = new TemplateFilter()
                .value(templateFilter1);

        IdentifierFilterOneOf2 identifierFilterOneOf2 = new IdentifierFilterOneOf2()
                .templateFilter(templateFilter);

        IdentifierFilter identifierFilter = new IdentifierFilter();
        identifierFilter.setActualInstance(identifierFilterOneOf2);

        return new CumulativeFilter()
                .identifierFilter(identifierFilter);
    }

    @NotNull
    public static List<Command> makeExerciseCommand(TemplateId templateId, String choiceName, String transferFactoryContractId, Object choicePayload) {
        ExerciseCommand exerciseTransferCommand = new ExerciseCommand()
                .templateId(templateId.getRaw())
                .contractId(transferFactoryContractId)
                .choice(choiceName)
                .choiceArgument(choicePayload);

        CommandOneOf3 subtype = new CommandOneOf3()
                .exerciseCommand(exerciseTransferCommand);

        Command command = new Command();
        command.setActualInstance(subtype);

        return List.of(command);
    }

    @NotNull
    public static List<Command> makeCreateCommand(TemplateId templateId, Object payload) {

        /* Sample JSON
         [{
           "CreateCommand": {
             "templateId": "#splice-wallet:Splice.Wallet.TransferPreapproval:TransferPreapprovalProposal",
             "createArguments": {
               "receiver": "bob::1220e2b2f76b762df6b9e085f6601f51ad4561c1217cd79372cc78e5a634df753699",
               "provider": "da-wallace-1::12206b78020b91dac97ee57eccd91bec29074367be0abd2fd5e99f15eb7675b5ecf3",
               "expectedDso": "DSO::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a"
             }
           }
         }]
         */

        CreateCommand createCommand = new CreateCommand()
                .templateId(templateId.getRaw())
                .createArguments(payload);

        CommandOneOf1 subtype = new CommandOneOf1()
                .createCommand(createCommand);

        Command command = new Command();
        command.setActualInstance(subtype);

        return List.of(command);
    }

    public static InteractiveSubmissionServiceOuterClass.PreparedTransaction parseTransaction(String base64EncodedPayload) throws InvalidProtocolBufferException {

        byte[] transactionBytes = Encode.fromBase64String(base64EncodedPayload);

        return InteractiveSubmissionServiceOuterClass.PreparedTransaction.parseFrom(transactionBytes);
    }

    public static Signature printAndSign(KeyPair keyPair, String base64EncodedPayload, String hashedPayload) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidProtocolBufferException {

        InteractiveSubmissionServiceOuterClass.PreparedTransaction preparedTransaction = parseTransaction(base64EncodedPayload);
        String transactionSubmitted = preparedTransaction.toString();
        System.out.println("Raw transaction: " + transactionSubmitted);

        return sign(keyPair, base64EncodedPayload, hashedPayload);
    }

    public static Signature sign(KeyPair keyPair, String base64EncodedPayload, String hashedPayload) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        String fingerprint = Encode.toHexString(Keys.fingerPrintOf(keyPair.getPublic()));

        return new Signature()
                .format("SIGNATURE_FORMAT_CONCAT")
                .signature(Keys.signBase64(keyPair.getPrivate(), hashedPayload))
                .signedBy(fingerprint)
                .signingAlgorithmSpec("SIGNING_ALGORITHM_SPEC_ED25519");
    }

    public static Signature verifyAndSign(KeyPair keyPair, String base64EncodedPayload, String hashedPayload) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException, InvalidProtocolBufferException {

        InteractiveSubmissionServiceOuterClass.PreparedTransaction preparedTransaction = parseTransaction(base64EncodedPayload);

        byte[] transactionHash = new TransactionHashBuilder(preparedTransaction).hash();
        byte[] rawProvidedHash = Encode.fromBase64String(hashedPayload);

        if (!Arrays.equals(transactionHash, rawProvidedHash)) {
            String base64ComputedHash = Encode.toBase64String(transactionHash);
            throw new IllegalStateException("Transaction hash mismatch: %s (provided) vs %s (computed) for transaction %s\nraw: %s"
                    .formatted(hashedPayload, base64ComputedHash, base64EncodedPayload, preparedTransaction.toString()));
        }

        return sign(keyPair, base64EncodedPayload, hashedPayload);
    }

    public static SinglePartySignatures makeSingleSignature(JsPrepareSubmissionResponse prepareSubmissionResponse, String partyId, KeyPair keyPair) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        return new SinglePartySignatures()
                .party(partyId)
                .signatures(List.of());
    }

    public static Right makeCanReadAsAnyRight() {
        Kind kind = new Kind();
        KindOneOf4 canReadAsAnyKind = new KindOneOf4().canReadAsAnyParty(new CanReadAsAnyParty());
        kind.setActualInstance(canReadAsAnyKind);
        return new Right().kind(kind);
    }

    public static Right makeCanExecuteAsAnyRight() {
        Kind kind = new Kind();
        KindOneOf2 canExecuteAsAnyKind = new KindOneOf2().canExecuteAsAnyParty(new CanExecuteAsAnyParty());
        kind.setActualInstance(canExecuteAsAnyKind);
        return new Right().kind(kind);
    }

    public static Right makeCanReadAsRight(String partyId) {
        Kind kind = new Kind();
        KindOneOf3 canReadAsKind = new KindOneOf3().canReadAs(new CanReadAs().value(new CanReadAs1().party(partyId)));
        kind.setActualInstance(canReadAsKind);
        return new Right().kind(kind);
    }

    public static Right makeCanExecuteAsRight(String partyId) {
        Kind kind = new Kind();
        KindOneOf1 canExecuteAsKind = new KindOneOf1().canExecuteAs(new CanExecuteAs().value(new CanExecuteAs1().party(partyId)));
        kind.setActualInstance(canExecuteAsKind);
        return new Right().kind(kind);
    }

    // does not require authentication
    public String getVersion() throws ApiException {
        GetLedgerApiVersionResponse response = this.ledgerApi.getV2Version();
        return response.getVersion();
    }

    public Long getLedgerEnd() throws ApiException {
        GetLedgerEndResponse response = this.ledgerApi.getV2StateLedgerEnd();
        return response.getOffset();
    }

    public Optional<PartyDetails> getPartyDetails(String partyId) throws ApiException {

        GetPartiesResponse response;
        try {
            response = this.ledgerApi.getV2PartiesParty(partyId, null, null);
        } catch (ApiException ex) {
            if (ex.getCode() == 404) {
                return Optional.empty();
            }
            throw ex;
        }

        List<PartyDetails> partyDetailsList = response.getPartyDetails();
        if (partyDetailsList == null || partyDetailsList.isEmpty()) {
            return Optional.empty();
        }

        return partyDetailsList.stream().findFirst();
    }

    // TODO: support multiple pages of response
    public List<String> getUsers() throws ApiException {
        ListUsersResponse response = this.ledgerApi.getV2Users(100, null);
        return response.getUsers().stream().map(User::getId).toList();
    }

    public Optional<User> getUser(String userId) throws ApiException {
        ListUsersResponse response = this.ledgerApi.getV2Users(100, null);
        return response.getUsers().stream().filter(u -> u.getId().equals(userId)).findFirst();
    }

    public User getOrCreateUser(String partyHint, String partyId) throws ApiException {
        Optional<User> currentUser = getUser(partyHint);

        // user does not exist
        if (currentUser.isEmpty()) {
            User user = new User()
                    .id(partyHint)
                    .primaryParty("")
                    .isDeactivated(false)
                    .identityProviderId("")
                    .metadata(new ObjectMeta()
                            .annotations(Map.of())
                            .resourceVersion(""));

            CreateUserRequest request = new CreateUserRequest().user(user).addRightsItem(makeCanReadAsRight(partyId));

            this.ledgerApi.postV2Users(request);
            return user;
        }

        // user exists, but the expected party id was not given in the environment
        else if (partyId.isBlank()) {
            throw new IllegalArgumentException("The user " + partyHint + " exists for party " + partyId + ". Missing environment variable for user party?");
        } else {
            Optional<CanReadAs> canReadAsPartyId =
                    ledgerApi.getV2UsersUserIdRights(currentUser.get().getId()).getRights().stream()
                            .filter(r -> r.getKind().getActualInstance() instanceof CanReadAs)
                            .map(r -> r.getKind().getKindOneOf3().getCanReadAs())
                            .filter(r -> r.getValue().getParty().equals(partyId))
                            .findFirst();

            // user exists, but cannot read as the given party id
            if (canReadAsPartyId.isEmpty()) {
                throw new IllegalArgumentException("The user " + partyHint + " already exists but cannot read as party " + partyId);
            }

            // user exists and can read as the given party
            return currentUser.get();
        }
    }

    public GenerateExternalPartyTopologyResponse generateExternalPartyTopology(String synchronizerId, String partyHint, PublicKey publicKey) throws ApiException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        String publicKeyHex = Encode.toBase64String(publicKey.getEncoded());

        SigningPublicKey signingPublicKey = new SigningPublicKey()
                .format("CRYPTO_KEY_FORMAT_DER_X509_SUBJECT_PUBLIC_KEY_INFO")
                .keyData(publicKeyHex)
                .keySpec("SIGNING_KEY_SPEC_EC_CURVE25519");

        GenerateExternalPartyTopologyRequest request = new GenerateExternalPartyTopologyRequest()
                .synchronizer(synchronizerId)
                .partyHint(partyHint)
                .publicKey(signingPublicKey)
                .localParticipantObservationOnly(false)
                .confirmationThreshold(0);

        // System.out.println("\ngenerate external party topology request: " + request.toJson() + "\n");
        GenerateExternalPartyTopologyResponse response = this.ledgerApi.postV2PartiesExternalGenerateTopology(request);
        // System.out.println("\ngenerate external party topology response: " + response.toJson() + "\n");

        return response;
    }

    public void allocateExternalParty(String synchronizerId, List<String> transactions, Signature multiHashSignature) throws ApiException, NoSuchAlgorithmException, SignatureException, InvalidKeyException {
        List<SignedTransaction> signedTransactions = transactions.stream()
                .map((t) -> new SignedTransaction()
                        .transaction(t))
                .toList();

        AllocateExternalPartyRequest request = new AllocateExternalPartyRequest()
                .synchronizer(synchronizerId)
                .onboardingTransactions(signedTransactions)
                .multiHashSignatures(List.of(multiHashSignature))
                .identityProviderId(user.identityProviderId());

        // System.out.println("\ngenerate external party topology request: " + request.toJson() + "\n");
        AllocateExternalPartyResponse response = this.ledgerApi.postV2PartiesExternalAllocate(request);
        // System.out.println("\ngenerate external party topology response: " + response.toJson() + "\n");
    }

    public void grantUserRights(String userId, List<Right> rights) throws ApiException {

        GrantUserRightsRequest request = new GrantUserRightsRequest()
                .userId(userId)
                .identityProviderId(user.identityProviderId())
                .rights(rights);

        // System.out.println("\ngrant user rights request: " + request.toJson() + "\n");
        GrantUserRightsResponse response = this.ledgerApi.postV2UsersUserIdRights(userId, request);
        // System.out.println("\ngrant user rights response: " + response.toJson() + "\n");
    }

    public List<JsGetActiveContractsResponse> getActiveContractsByFilter(String partyId, List<CumulativeFilter> cumulativeFilters) throws Exception {
        long offset = getLedgerEnd();

        Filters filters = new Filters()
                .cumulative(cumulativeFilters);

        TransactionFilter transactionFilter = new TransactionFilter()
                .filtersByParty(Map.of(partyId, filters));

        GetActiveContractsRequest request = new GetActiveContractsRequest()
                .verbose(false)
                .activeAtOffset(offset)
                .filter(transactionFilter);

//        System.out.println("\nget active contracts by interface request: " + request.toJson() + "\n");
        List<JsGetActiveContractsResponse> response = this.ledgerApi.postV2StateActiveContracts(request, 100L, null);
//        System.out.println("\nget active contracts by interface response: " + JSON.getGson().toJson(response) + "\n");

        return response;
    }

    public List<JsGetUpdatesResponse> getUpdatesWithFilter(String partyId, List<CumulativeFilter> cumulativeFilters, long beginAfterOffset ) throws Exception {
        Filters filters = new Filters()
                .cumulative(cumulativeFilters);

        TransactionFormat transactionFormat = new TransactionFormat()
                .transactionShape("TRANSACTION_SHAPE_LEDGER_EFFECTS")
                .eventFormat(
                        new EventFormat()
                                .verbose(true)
                                .filtersByParty(Map.of(partyId, filters))
                );

        UpdateFormat updateFormat = new UpdateFormat()
                .includeTransactions(transactionFormat);

        GetUpdatesRequest request = new GetUpdatesRequest()
                .verbose(false)
                .beginExclusive(beginAfterOffset)
                .updateFormat(updateFormat);

        // System.out.println("\nget updates by interface request: " + request.toJson() + "\n");
        List<JsGetUpdatesResponse> response = this.ledgerApi.postV2Updates(request, 100L, null);
        // System.out.println("\nget updates by interface response: " + JSON.getGson().toJson(response) + "\n");

        return response;
    }

    public JsSubmitAndWaitForTransactionResponse submitAndWaitForCommands(
            String actAs,
            String commandId,
            List<Command> commandsList,
            List<DisclosedContract> disclosedContracts
    ) throws ApiException {

        List<String> parties = List.of(actAs);

        JsCommands commands = new JsCommands()
                .commands(commandsList)
                .commandId(commandId)
                .actAs(parties)
                .readAs(parties)
                .disclosedContracts(disclosedContracts);

        JsSubmitAndWaitForTransactionRequest request = new JsSubmitAndWaitForTransactionRequest();
        request.setCommands(commands);

//        System.out.println("\nsubmit and wait for commands request: " + request.toJson() + "\n");
        JsSubmitAndWaitForTransactionResponse response = this.ledgerApi.postV2CommandsSubmitAndWaitForTransaction(request);
//        System.out.println("\nsubmit and wait for commands response: " + response.toJson() + "\n");
        return response;
    }

    public JsPrepareSubmissionResponse prepareSubmissionForSigning(
            String synchronizerId,
            String partyId,
            String commandId,
            List<Command> commands,
            List<DisclosedContract> disclosedContracts
    ) throws ApiException {
        JsPrepareSubmissionRequest request = new JsPrepareSubmissionRequest()
                .synchronizerId(synchronizerId)
                .userId(user.userId())
                .actAs(List.of(partyId))
                .commandId(commandId)
                .commands(commands)
                .disclosedContracts(disclosedContracts)
                .verboseHashing(false);

//        System.out.println("\nprepare submission request: " + request.toJson() + "\n");
        JsPrepareSubmissionResponse response = this.ledgerApi.postV2InteractiveSubmissionPrepare(request);
//        System.out.println("\nprepare submission response: " + response.toJson() + "\n");
        return response;
    }

    public void executeSignedSubmission(JsPrepareSubmissionResponse preparedSubmission, String partyId, Signature signature) throws ApiException {
        String submissionId = java.util.UUID.randomUUID().toString();

        DeduplicationPeriod2OneOf2 deduplicationPeriodSelection = new DeduplicationPeriod2OneOf2().empty(new Object());

        DeduplicationPeriod2 useMaximum = new DeduplicationPeriod2();
        useMaximum.setActualInstance(deduplicationPeriodSelection);

        SinglePartySignatures singlePartySignatures = new SinglePartySignatures()
                .party(partyId)
                .signatures(List.of(signature));

        PartySignatures partySignatures = new PartySignatures()
                .signatures(List.of(singlePartySignatures));

        JsExecuteSubmissionRequest request = new JsExecuteSubmissionRequest()
                .userId(user.userId())
                .submissionId(submissionId)
                .preparedTransaction(preparedSubmission.getPreparedTransaction())
                .hashingSchemeVersion(preparedSubmission.getHashingSchemeVersion())
                .partySignatures(partySignatures)
                .deduplicationPeriod(useMaximum);

        // System.out.println("\nexecute prepared submission request: " + request.toJson() + "\n");
        Object response = this.ledgerApi.postV2InteractiveSubmissionExecute(request);
        // System.out.println("\nexecute prepared submission response: " + JSON.getGson().toJson(response) + "\n");
    }

    public List<CompletionStreamResponse> getCompletions(List<String> parties, Long beginExclusive) throws ApiException {
        CompletionStreamRequest request = new CompletionStreamRequest()
                .userId(user.userId())
                .parties(parties)
                .beginExclusive(beginExclusive);

        // System.out.println("\nget completions request: " + request.toJson() + "\n");
        List<CompletionStreamResponse> response = this.ledgerApi.postV2CommandsCompletions(request, null, null);
        // System.out.println("\nget completions response: " + JSON.getGson().toJson(response) + "\n");

        return response;
    }
}
