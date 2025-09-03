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

import com.example.client.ledger.api.DefaultApi;
import com.example.client.ledger.invoker.ApiClient;
import com.example.client.ledger.invoker.ApiException;
import com.example.client.ledger.model.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class Ledger {
    private final DefaultApi ledgerApi;

    public Ledger(String baseUrl, String bearerToken) {
        ApiClient client = new ApiClient();
        client.setBasePath(baseUrl);
        if (!bearerToken.isEmpty())
            client.setBearerToken(bearerToken);
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

        return this.ledgerApi.postV2StateActiveContracts(request, 100L, null);
    }

    public void exercise(
            TemplateId templateId,
            String transferFactoryContractId,
            String choiceName,
            Object choicePayload,
            List<DisclosedContract> disclosedContracts
    ) throws ApiException {
        String commandId = java.util.UUID.randomUUID().toString();

        ExerciseCommand exerciseTransferCommand = new ExerciseCommand();
        exerciseTransferCommand.setTemplateId(templateId.getRaw());
        exerciseTransferCommand.setContractId(transferFactoryContractId);
        exerciseTransferCommand.setChoice(choiceName);
        exerciseTransferCommand.setChoiceArgument(choicePayload);

        Command command = new Command();
        command.setActualInstance(exerciseTransferCommand);

        List<Command> commandsList = new ArrayList<>();
        commandsList.add(command);

        JsCommands commands = new JsCommands();
        // TODO: more fields may need to be set here: actAs, readAs
        commands.setCommands(commandsList);
        commands.setCommandId(commandId);
        commands.setDisclosedContracts(disclosedContracts);

        JsSubmitAndWaitForTransactionRequest request = new JsSubmitAndWaitForTransactionRequest();
        request.setCommands(commands);

        System.out.println("\nget transfer factory request: " + request.toJson() + "\n");
        JsSubmitAndWaitForTransactionResponse response = this.ledgerApi.postV2CommandsSubmitAndWaitForTransaction(request);
        System.out.println("\nget transfer factory response: " + response.toJson() + "\n");
    }
}
