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

import com.daml.ledger.javaapi.data.Template;
import com.example.GsonTypeAdapters.GsonSingleton;
import com.example.client.ledger.api.DefaultApi;
import com.example.client.ledger.invoker.ApiClient;
import com.example.client.ledger.invoker.ApiException;
import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.*;
import org.jetbrains.annotations.NotNull;

import java.security.InvalidKeyException;
import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.SignatureException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Ledger {
    private final DefaultApi ledgerApi;

    public Ledger(String baseUrl) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        client.setReadTimeout(60 * 1000); // 60 seconds

        JSON.setGson(GsonSingleton.getInstance());
        this.ledgerApi = new DefaultApi(client);
    }

    // does not require authentication
    public String getVersion() throws ApiException {
        GetLedgerApiVersionResponse response = this.ledgerApi.getV2Version();
        return response.getVersion();
    }

    // requires authentication
    public Long getLedgerEnd(String bearerToken) throws ApiException {
        this.ledgerApi.getApiClient().setBearerToken(bearerToken);
        GetLedgerEndResponse response = this.ledgerApi.getV2StateLedgerEnd();
        return response.getOffset();
    }

    // TODO: support multiple pages of response
    public List<String> getUsers(String bearerToken) throws ApiException {
        this.ledgerApi.getApiClient().setBearerToken(bearerToken);
        ListUsersResponse response = this.ledgerApi.getV2Users(100, null);
        return response.getUsers().stream().map(u -> u.getId()).toList();
    }    public Optional<User> getUser(String bearerToken, String userId) throws ApiException {
        this.ledgerApi.getApiClient().setBearerToken(bearerToken);
        ListUsersResponse response = this.ledgerApi.getV2Users(100, null);
        return response.getUsers().stream().filter(u -> u.getId().equals(userId)).findFirst();
    }

    public User getOrCreateUser(String bearerToken, String partyHint, String partyId) throws ApiException {
        Optional<User> currentUser = getUser(bearerToken, partyHint);

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

            Kind kind = new Kind();
            KindOneOf1 canReadAsKind = new KindOneOf1().canReadAs(new CanReadAs().value(new CanReadAs1().party(partyId)));
            kind.setActualInstance(canReadAsKind);
            Right right = new Right().kind(kind);
            CreateUserRequest request = new CreateUserRequest().user(user).addRightsItem(right);

            this.ledgerApi.getApiClient().setBearerToken(bearerToken);
            this.ledgerApi.postV2Users(request);
            return user;
        }

        // user exists, but the expected party id was not given in the environment
        else if (partyId.isBlank()) {
            throw new IllegalArgumentException("The user " + partyHint + " exists for party " + partyId + ". Missing environment variable for user party?");
        }

        else {
            this.ledgerApi.getApiClient().setBearerToken(bearerToken);
            Optional<CanReadAs> canReadAsPartyId =
                    ledgerApi.getV2UsersUserIdRights(currentUser.get().getId()).getRights().stream()
                            .filter(r -> r.getKind().getActualInstance() instanceof CanReadAs)
                            .map(r -> r.getKind().getKindOneOf1().getCanReadAs())
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

    public static CumulativeFilter createFilterByInterface(TemplateId interfaceId) {

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

    public List<JsGetActiveContractsResponse> getActiveContractsByFilter(String bearerToken, String partyId, List<CumulativeFilter> cumulativeFilters) throws Exception {
        long offset = getLedgerEnd(bearerToken);

        Filters filters = new Filters()
                .cumulative(cumulativeFilters);

        TransactionFilter transactionFilter = new TransactionFilter()
                .filtersByParty(Map.of(partyId, filters));

        GetActiveContractsRequest request = new GetActiveContractsRequest()
                .verbose(false)
                .activeAtOffset(offset)
                .filter(transactionFilter);

//        System.out.println("\nget active contracts by interface request: " + request.toJson() + "\n");
        this.ledgerApi.getApiClient().setBearerToken(bearerToken);
        List<JsGetActiveContractsResponse> response = this.ledgerApi.postV2StateActiveContracts(request, 100L, null);
//        System.out.println("\nget active contracts by interface response: " + JSON.getGson().toJson(response) + "\n");

        return response;
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

        CreateCommand createCommand = new CreateCommand()
                .templateId(templateId.getRaw())
                .createArguments(payload);

        CommandOneOf1 subtype = new CommandOneOf1()
                .createCommand(createCommand);

        Command command = new Command();
        command.setActualInstance(subtype);

        return List.of(command);
    }

    public JsSubmitAndWaitForTransactionResponse submitAndWaitForCommands(
            String bearerToken,
            String actAs,
            List<Command> commandsList,
            List<DisclosedContract> disclosedContracts
    ) throws ApiException {
        String commandId = java.util.UUID.randomUUID().toString();

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
        this.ledgerApi.getApiClient().setBearerToken(bearerToken);
        JsSubmitAndWaitForTransactionResponse response = this.ledgerApi.postV2CommandsSubmitAndWaitForTransaction(request);
//        System.out.println("\nsubmit and wait for commands response: " + response.toJson() + "\n");
        return response;
    }

    public JsPrepareSubmissionResponse prepareSubmissionForSigning(
            String bearerToken,
            String partyId,
            List<Command> commands,
            List<DisclosedContract> disclosedContracts
    ) throws ApiException {
        String commandId = java.util.UUID.randomUUID().toString();
        JsPrepareSubmissionRequest request = new JsPrepareSubmissionRequest()
                .synchronizerId(Env.SYNCHRONIZER_ID)
                .userId(Env.LEDGER_USER_ID) // TODO: replace this
                .actAs(List.of(partyId))
                .commandId(commandId)
                .commands(commands)
                .disclosedContracts(disclosedContracts)
                .verboseHashing(false);

//        System.out.println("\nprepare submission request: " + request.toJson() + "\n");
        this.ledgerApi.getApiClient().setBearerToken(bearerToken);
        JsPrepareSubmissionResponse response = this.ledgerApi.postV2InteractiveSubmissionPrepare(request);
//        System.out.println("\nprepare submission response: " + response.toJson() + "\n");
        return response;
    }

    public static SinglePartySignatures makeSingleSignature(JsPrepareSubmissionResponse prepareSubmissionResponse, String partyId, KeyPair keyPair) throws NoSuchAlgorithmException, SignatureException, InvalidKeyException {

        String fingerprint = Encode.toHexString(Keys.fingerPrintOf(keyPair.getPublic()));

        Signature signature = new Signature()
                .format("SIGNATURE_FORMAT_CONCAT")
                .signature(Keys.signBase64(keyPair.getPrivate(), prepareSubmissionResponse.getPreparedTransactionHash()))
                .signedBy(fingerprint)
                .signingAlgorithmSpec("SIGNING_ALGORITHM_SPEC_ED25519");

        return new SinglePartySignatures()
                .party(partyId)
                .signatures(List.of(signature));
    }

    public void executeSignedSubmission(JsPrepareSubmissionResponse preparedSubmission, List<SinglePartySignatures> singlePartySignatures) throws ApiException {
        String submissionId = java.util.UUID.randomUUID().toString();

        DeduplicationPeriod2OneOf2 deduplicationPeriodSelection = new DeduplicationPeriod2OneOf2().empty(new Object());

        DeduplicationPeriod2 useMaximum = new DeduplicationPeriod2();
        useMaximum.setActualInstance(deduplicationPeriodSelection);

        PartySignatures partySignatures = new PartySignatures()
                .signatures(singlePartySignatures);

        JsExecuteSubmissionRequest request = new JsExecuteSubmissionRequest()
                .userId(Env.LEDGER_USER_ID)
                .submissionId(submissionId)
                .preparedTransaction(preparedSubmission.getPreparedTransaction())
                .hashingSchemeVersion(preparedSubmission.getHashingSchemeVersion())
                .partySignatures(partySignatures)
                .deduplicationPeriod(useMaximum);

//        System.out.println("\nexecute prepared submission request: " + request.toJson() + "\n");
        Object response = this.ledgerApi.postV2InteractiveSubmissionExecute(request);
//        System.out.println("\nexecute prepared submission response: " + GsonSingleton.getInstance().toJson(response) + "\n");
    }
}
