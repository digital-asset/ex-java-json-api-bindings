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

import com.example.GsonTypeAdapters.GsonSingleton;
import com.example.client.ledger.api.DefaultApi;
import com.example.client.ledger.invoker.ApiClient;
import com.example.client.ledger.invoker.ApiException;
import com.example.client.ledger.invoker.JSON;
import com.example.client.ledger.model.*;
import org.jetbrains.annotations.NotNull;

import java.security.KeyPair;
import java.util.List;
import java.util.Map;

public class Ledger {
    private final DefaultApi ledgerApi;

    public Ledger(String baseUrl, String bearerToken) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        if (!bearerToken.isEmpty())
            client.setBearerToken(bearerToken);

        JSON.setGson(GsonSingleton.getInstance());
        this.ledgerApi = new DefaultApi(client);
    }

    // does not require authentication
    public String getVersion() throws ApiException {
        GetLedgerApiVersionResponse response = this.ledgerApi.getV2Version();
        return response.getVersion();
    }

    // requires authentication
    public Long getLedgerEnd() throws ApiException {
        GetLedgerEndResponse response = this.ledgerApi.getV2StateLedgerEnd();
        return response.getOffset();
    }

    public List<JsGetActiveContractsResponse> getActiveContractsForInterface(String partyId, String interfaceId) throws Exception {
        long offset = getLedgerEnd();

        InterfaceFilter1 interfaceFilter1 = new InterfaceFilter1()
                .includeCreatedEventBlob(false)
                .includeInterfaceView(true)
                .interfaceId(interfaceId);

        InterfaceFilter interfaceFilter = new InterfaceFilter()
                .value(interfaceFilter1);

        IdentifierFilterOneOf1 identifierFilterOneOf1 = new IdentifierFilterOneOf1()
                .interfaceFilter(interfaceFilter);

        IdentifierFilter identifierFilter = new IdentifierFilter();
        identifierFilter.setActualInstance(identifierFilterOneOf1);

        CumulativeFilter cumulativeFilter = new CumulativeFilter()
                .identifierFilter(identifierFilter);

        Filters filters = new Filters()
                .addCumulativeItem(cumulativeFilter);

        TransactionFilter transactionFilter = new TransactionFilter()
                .filtersByParty(Map.of(partyId, filters));

        GetActiveContractsRequest request = new GetActiveContractsRequest()
                .verbose(false)
                .activeAtOffset(offset)
                .filter(transactionFilter);

        System.out.println("\nget active contracts by interface request: " + request.toJson() + "\n");
        List<JsGetActiveContractsResponse> response = this.ledgerApi.postV2StateActiveContracts(request, 100L, null);
        System.out.println("\nget active contracts by interface response: " + JSON.getGson().toJson(response) + "\n");

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

    public void submitAndWaitForCommands(
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

        System.out.println("\nsubmit and wait for commands request: " + request.toJson() + "\n");
        JsSubmitAndWaitForTransactionResponse response = this.ledgerApi.postV2CommandsSubmitAndWaitForTransaction(request);
        System.out.println("\nsubmit and wait for commands response: " + response.toJson() + "\n");
    }

    public JsPrepareSubmissionResponse prepareSubmissionForSigning(
            String partyId,
            List<Command> commands,
            List<DisclosedContract> disclosedContracts
    ) throws ApiException {
        String commandId = java.util.UUID.randomUUID().toString();
        JsPrepareSubmissionRequest request = new JsPrepareSubmissionRequest()
                .synchronizerId(Env.SYNCHRONIZER_ID)
                .userId(Env.LEDGER_USER_ID)
                .actAs(List.of(partyId))
                .commandId(commandId)
                .commands(commands)
                .disclosedContracts(disclosedContracts)
                .verboseHashing(false);

        System.out.println("\nprepare submission request: " + request.toJson() + "\n");
        JsPrepareSubmissionResponse response = this.ledgerApi.postV2InteractiveSubmissionPrepare(request);
        System.out.println("\nprepare submission response: " + response.toJson() + "\n");
        return response;
    }

    public String executeSignedSubmission(JsPrepareSubmissionResponse preparedSubmission, PartySignatures partySignatures) throws ApiException {
        String submissionId = "Java JSON API Sample";

        DeduplicationPeriod2OneOf2 deduplicationPeriodSelection = new DeduplicationPeriod2OneOf2().empty(new Object());

        DeduplicationPeriod2 useMaximum = new DeduplicationPeriod2();
        useMaximum.setActualInstance(deduplicationPeriodSelection);

        JsExecuteSubmissionRequest request = new JsExecuteSubmissionRequest()
                .userId(Env.LEDGER_USER_ID)
                .submissionId(submissionId)
                .preparedTransaction(preparedSubmission.getPreparedTransaction())
                .hashingSchemeVersion(preparedSubmission.getHashingSchemeVersion())
                .partySignatures(partySignatures)
                .deduplicationPeriod(useMaximum);

        System.out.println("\nexecute prepared submission request: " + request.toJson() + "\n");
        Object response = this.ledgerApi.postV2InteractiveSubmissionExecute(request);
        System.out.println("\nexecute prepared submission response: " + GsonSingleton.getInstance().toJson(response) + "\n");

        return GsonSingleton.getInstance().toJson(response);
    }
}
