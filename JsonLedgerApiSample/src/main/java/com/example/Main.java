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

import com.daml.ledger.javaapi.data.codegen.DamlRecord;
import com.daml.ledger.javaapi.data.codegen.json.JsonLfDecoder;
import com.example.GsonTypeAdapters.GsonSingleton;
import com.example.client.ledger.model.*;
import com.example.client.tokenMetadata.model.GetRegistryInfoResponse;
import com.example.client.transferInstruction.model.TransferFactoryWithChoiceContext;
import com.example.client.validator.model.SignedTopologyTx;
import com.example.client.validator.model.TopologyTx;
import splice.api.token.holdingv1.Holding;
import splice.api.token.holdingv1.HoldingView;
import splice.api.token.holdingv1.InstrumentId;
import splice.api.token.metadatav1.ChoiceContext;
import splice.api.token.metadatav1.ExtraArgs;
import splice.api.token.metadatav1.Metadata;
import splice.api.token.transferinstructionv1.Transfer;
import splice.api.token.transferinstructionv1.TransferFactory_Transfer;

import java.io.IOException;
import java.math.BigDecimal;
import java.security.KeyPair;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.function.BiFunction;

public class Main {

    private static final BigDecimal estimatedFeesMultiplier = new BigDecimal("0.1");

    public static void main(String[] args) {
        try {
            validateEnvironment();

            Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL);
            Validator validatorApi = new Validator(Env.VALIDATOR_API_URL);
            Scan scanApi = new Scan(Env.SCAN_API_URL);
            ScanProxy scanProxyApi = new ScanProxy(Env.SCAN_PROXY_API_URL);
            TransferInstruction transferInstructionApi = new TransferInstruction(Env.SCAN_API_URL);
            TokenMetadata tokenMetadataApi = new TokenMetadata(Env.SCAN_API_URL);

            confirmConnectivity(ledgerApi, validatorApi, scanApi, tokenMetadataApi);

            // setup sample's parties, keys, etc.
            SampleUser operator = new SampleUser("operator", Env.VALIDATOR_TOKEN, Env.VALIDATOR_PARTY);

            confirmAuthentication(operator, ledgerApi, validatorApi, scanProxyApi);

            SampleUser treasury = Env.TREASURY_PARTY.isBlank() ?
                    new SampleUser(Env.TREASURY_PARTY_HINT, Env.TREASURY_TOKEN, onboardExternalParty(operator, ledgerApi, validatorApi)) :
                    new SampleUser(Env.TREASURY_PARTY_HINT, Env.TREASURY_TOKEN, Env.TREASURY_PARTY);
            SampleUser sender = Env.SENDER_PARTY.isBlank() ?
                    new SampleUser(Env.SENDER_PARTY_HINT, Env.SENDER_TOKEN, onboardExternalParty(operator, ledgerApi, validatorApi)) :
                    new SampleUser(Env.SENDER_PARTY_HINT, Env.SENDER_TOKEN, Env.SENDER_PARTY, Env.SENDER_PUBLIC_KEY, Env.SENDER_PRIVATE_KEY);
            List<SampleUser> allUsers = List.of(operator, treasury, sender);

            // TODO: get this from the Canton Coin registry
            InstrumentId cantonCoinInstrumentId = new InstrumentId(Env.DSO_PARTY, "Amulet");

            // calculate the first transfer amount
            // sender needs at least enough coins
            // to cover the fees of transferring them out again
            BigDecimal transferAmount1 = new BigDecimal(Env.TRANSFER_AMOUNT);
            BigDecimal estimatedFees = transferAmount1.multiply(estimatedFeesMultiplier);
            if (getTotalHoldings(operator, ledgerApi, sender, cantonCoinInstrumentId).subtract(estimatedFees).compareTo(BigDecimal.ZERO) < 0) {
                transferAmount1 = transferAmount1.add(estimatedFees);
            }

            printTotalHoldings(operator, ledgerApi, allUsers, cantonCoinInstrumentId);

            // perform a transfer from the local party operator
            transferAsset(
                    operator,
                    transferInstructionApi,
                    ledgerApi,
                    operator,
                    sender,
                    transferAmount1,
                    cantonCoinInstrumentId);

            printTotalHoldings(operator, ledgerApi, allUsers, cantonCoinInstrumentId);

            // calculate transfer amount
            BigDecimal transferAmount2 = new BigDecimal(Env.TRANSFER_AMOUNT);

            // perform a transfer from the external party sender
            BigDecimal priorBalance = getTotalHoldings(operator, ledgerApi, treasury, cantonCoinInstrumentId);
            transferAsset(
                    operator,
                    transferInstructionApi,
                    ledgerApi,
                    sender,
                    treasury,
                    transferAmount2,
                    cantonCoinInstrumentId);

            // wait for the treasury party to receive the transfer
            BigDecimal updatedBalance = priorBalance;
            printStep("Waiting for holdings transfer to complete");
            waitFor(2 * 1000, 10, () -> {
                return getTotalHoldings(operator, ledgerApi, treasury, cantonCoinInstrumentId).compareTo(priorBalance) > 0;
            });

            printStep("Success!");
            printTotalHoldings(operator, ledgerApi, allUsers, cantonCoinInstrumentId);
            System.exit(0);
        } catch (Exception ex) {
            handleException(ex);
        }
    }

    private static void waitFor(long sleepForMillis, int retries, WaitLoopCheck checkState) throws Exception {
        while (!checkState.getAsBoolean() && retries > 0) {
            System.out.println("Waiting...");
            Thread.sleep(sleepForMillis);
            retries--;
        }
    }

    private static void printTotalHoldings(SampleUser operator, Ledger ledgerApi, List<SampleUser> users, InstrumentId cantonCoinInstrumentId) throws Exception {
        printStep("Print total holdings");
        for (SampleUser user : users) {
            BigDecimal totalHoldings = getTotalHoldings(operator, ledgerApi, user, cantonCoinInstrumentId);
            System.out.println(user.name + " has " + totalHoldings + " " + cantonCoinInstrumentId.id);
        }
    }

    private static <T> T convertRecordViaJson(
            Object recordPayload,
            JsonDecoder<T> valueParser
    ) {
        String raw = GsonSingleton.getInstance().toJson(recordPayload);
        return useValueParser(raw, valueParser);
    }

    private static <T> T useValueParser(
            String raw,
            JsonDecoder<T> valueParser
    ) {
        try {
            return valueParser.decode(raw);
        } catch (IOException ex) {
            System.out.println("Cannot decode interface view.");
            System.exit(1);
            return null;
        }
    }

    private static <T extends DamlRecord<T>> ContractAndId<T> fromInterface(
            JsContractEntry contractEntry,
            TemplateId interfaceId,
            JsonDecoder<T> interfaceValueParser
    ) {
        JsActiveContract activeContract = contractEntry.getJsContractEntryOneOf().getJsActiveContract();
        String instanceContractId = activeContract.getCreatedEvent().getContractId();

        List<JsInterfaceView> interfaceViews = activeContract.getCreatedEvent().getInterfaceViews();
        if (interfaceViews == null) return null;

        T record = interfaceViews
                .stream()
                .filter(v -> interfaceId.matchesModuleAndTypeName(v.getInterfaceId()))
                .map(v -> convertRecordViaJson(v.getViewValue(), interfaceValueParser))
                .findFirst()
                .orElseThrow();
        return new ContractAndId<>(instanceContractId, record);
    }

    private static BigDecimal getTotalHoldings(SampleUser operator, Ledger ledgerApi, SampleUser user, InstrumentId instrumentId) throws Exception {
        return queryForHoldings(operator, ledgerApi, user, instrumentId).stream()
                .map(h -> h.record().amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<ContractAndId<HoldingView>> queryForHoldings(SampleUser operator, Ledger ledgerApi, SampleUser user, InstrumentId instrumentId) throws Exception {
        CumulativeFilter holdingInterfaceFilter = Ledger.createFilterByInterface(TemplateId.HOLDING_INTERFACE_ID);
        return ledgerApi.getActiveContractsByFilter(operator.bearerToken, user.partyId, List.of(holdingInterfaceFilter)).stream()
                .map(r -> fromInterface(r.getContractEntry(), TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson))
                .filter(v -> v != null && v.record().instrumentId.equals(instrumentId))
                .toList();
    }

    private static List<ContractAndId<HoldingView>> selectHoldingsForTransfer(SampleUser operator, Ledger ledgerApi, SampleUser user, BigDecimal transferAmount, InstrumentId instrumentId) throws Exception {

        printStep("Selecting holdings");
        System.out.println("Selecting holdings for a " + transferAmount + " unit transfer from " + user.partyId);

        final BigDecimal[] remainingReference = {transferAmount};

        List<ContractAndId<HoldingView>> holdingsForTransfer =
                queryForHoldings(operator, ledgerApi, user, instrumentId).stream()
                        .filter(h -> h.record().lock.isEmpty())
                        .sorted(Comparator.comparing(c -> c.record().amount))
                        .takeWhile(c -> {
                            BigDecimal previousTotal = remainingReference[0];
                            remainingReference[0] = previousTotal.subtract(c.record().amount);
                            return previousTotal.compareTo(BigDecimal.ZERO) > 0;
                        })
                        .toList();

        if (remainingReference[0].compareTo(BigDecimal.ZERO) > 0) {
            return null;
        } else {
            System.out.println("Found sufficient holdings for transfer: ");
            for (ContractAndId<HoldingView> holding : holdingsForTransfer) {
                System.out.println("- " + holding.record().amount + " " + holding.record().instrumentId.id);
            }
        }
        return holdingsForTransfer;
    }

    private static void printStep(String step) {
        System.out.println("\n=== " + step + " ===");
    }

    private static void printToken(String name, String token) {
        int length = token.length();
        System.out.println(name + ": " +
                (token.trim().isEmpty() ? "<empty>" : token.substring(0, 10) + "..." + token.substring(length - 11, length - 1)));
    }

    private static void validateEnvironment() {
        printStep("Print environment variables");
        System.out.println("LEDGER_API_URL: " + Env.LEDGER_API_URL);
        System.out.println("VALIDATOR_API_URL: " + Env.VALIDATOR_API_URL);
        System.out.println("SCAN_PROXY_API_URL: " + Env.SCAN_PROXY_API_URL);
        System.out.println();
        System.out.println("LEDGER_USER_ID: " + Env.LEDGER_USER_ID);
        printToken("VALIDATOR_TOKEN", Env.VALIDATOR_TOKEN);
        System.out.println();
        System.out.println("TREASURY_PARTY_HINT: " + Env.TREASURY_PARTY_HINT);
        printToken("TREASURY_TOKEN", Env.TREASURY_TOKEN);
        System.out.println("TREASURY_PARTY: " + Env.TREASURY_PARTY);
        System.out.println();
        System.out.println("SENDER_PARTY_HINT: " + Env.SENDER_PARTY_HINT);
        printToken("SENDER_TOKEN", Env.SENDER_TOKEN);
        System.out.println("SENDER_PARTY: " + Env.SENDER_PARTY);
        System.out.println("SENDER_PUBLIC_KEY: " + Env.SENDER_PUBLIC_KEY);
        System.out.println("SENDER_PRIVATE_KEY: " + Env.SENDER_PRIVATE_KEY);

        if (Env.SCAN_API_URL == null || Env.SCAN_API_URL.isEmpty()) {
            System.err.println("Error: SCAN_API_URL environment variable must be set");
            System.exit(1);
        }

        if (!Env.SENDER_PARTY.isEmpty() && (Env.SENDER_PRIVATE_KEY.isEmpty() || Env.SENDER_PUBLIC_KEY.isEmpty())) {
            System.err.println("Error: If SENDER_PARTY is set, then both SENDER_PRIVATE_KEY and SENDER_PUBLIC_KEY must be set.");
            System.exit(1);
        }

        if (!Env.SENDER_PUBLIC_KEY.isEmpty() || !Env.SENDER_PRIVATE_KEY.isEmpty()) {
            try {
                Keys.createFromRawBase64(Env.SENDER_PUBLIC_KEY, Env.SENDER_PRIVATE_KEY);
            } catch (Exception ex) {
                System.err.println("Error: Check that keys are valid and in raw + public, base64 format.");
                handleException(ex);
            }
        }

        if (!Env.SENDER_PUBLIC_KEY.isEmpty() && !Env.SENDER_PRIVATE_KEY.isEmpty()) {
            try {
                KeyPair keyPair = Keys.createFromRawBase64(Env.SENDER_PUBLIC_KEY, Env.SENDER_PRIVATE_KEY);
                String calculatedFingerPrint = Encode.toHexString(Keys.fingerPrintOf(keyPair.getPublic()));
                String partyIdFingerPrint = Env.SENDER_PARTY.split("::")[1];
                if (!calculatedFingerPrint.equals(partyIdFingerPrint)) {
                    throw new IllegalArgumentException("The calculated finger print " + calculatedFingerPrint + " does not match the party id.");
                }
            } catch (Exception ex) {
                System.err.println("Error: Check that keys are valid and in raw + public, base64 format.");
                handleException(ex);
            }
        }

    }

    private static void confirmConnectivity(Ledger ledgerApi, Validator validatorApi, Scan scanApi, TokenMetadata tokenMetadataApi) throws Exception {
        printStep("Confirm API connectivity");

        System.out.println("Version: " + ledgerApi.getVersion());

        Env.VALIDATOR_PARTY = validatorApi.getValidatorParty();
        System.out.println("Validator Party: " + validatorApi.getValidatorParty());

        Env.SYNCHRONIZER_ID = scanApi.getSynchronizerId();
        System.out.println("Synchronizer id: " + Env.SYNCHRONIZER_ID);

        GetRegistryInfoResponse registryInfo = tokenMetadataApi.getRegistryInfo();
        System.out.println("Registry Party: " + registryInfo.getAdminId());
    }

    private static void confirmAuthentication(SampleUser user, Ledger ledgerApi, Validator validatorApi, ScanProxy scanProxyApi) throws Exception {
        printStep("Confirm authentication");

        // these require a valid Validator token
        Env.DSO_PARTY = scanProxyApi.getDsoPartyId(user.bearerToken);
        System.out.println("DSO Party: " + Env.DSO_PARTY);
        System.out.println("Ledger end: " + ledgerApi.getLedgerEnd(user.bearerToken));
        System.out.println("Participant users: " + ledgerApi.getUsers(user.bearerToken));
        System.out.println("Validator users: " + validatorApi.listUsers(user.bearerToken));
    }

    private static BiFunction<String, KeyPair, String> onboardExternalParty(SampleUser operator, Ledger ledgerApi, Validator validatorApi) {
        return (partyHint, externalPartyKeyPair) -> {
            printStep("Onboard " + partyHint);

            try {
                List<TopologyTx> txs = validatorApi.prepareOnboarding(operator.bearerToken, partyHint, externalPartyKeyPair.getPublic());
                List<SignedTopologyTx> signedTxs = ExternalSigning.signOnboarding(txs, externalPartyKeyPair.getPrivate());
                String newParty = validatorApi.submitOnboarding(operator.bearerToken, signedTxs, externalPartyKeyPair.getPublic());
                System.out.println("New party: " + newParty);
                Keys.printKeyPair(partyHint, externalPartyKeyPair);
                preapproveTransfers(operator, validatorApi, ledgerApi, newParty, externalPartyKeyPair);
                System.out.println("Created transfer preapproval for " + newParty);
                User user = ledgerApi.getOrCreateUser(operator.bearerToken, partyHint, newParty);
                System.out.println("User " + user.getId() + " can read from the ledger as " + newParty);
                return newParty;
            } catch (Exception ex) {
                handleException(ex);
                return null;
            }
        };
    }

    // Note: the endpoints used here consume limited resources
    // (i.e., the 200 parties-per-node limitation).
    // https://docs.dev.sync.global/scalability/scalability.html#bypassing-the-limit
    private static void preapproveTransfers(SampleUser operator, Validator validatorApi, Ledger ledgerApi, String externalPartyId, KeyPair externalPartyKeyPair) throws Exception {
        List<DisclosedContract> noDisclosures = new ArrayList<>();
        var transferPreapprovalProposal = Splice.makeTransferPreapprovalProposal(externalPartyId, operator.partyId, Env.DSO_PARTY);
        List<Command> createTransferPreapprovalCommands = Ledger.makeCreateCommand(TemplateId.TRANSFER_PREAPPROVAL_PROPOSAL_ID, transferPreapprovalProposal);
        prepareAndSign(ledgerApi, operator.bearerToken, externalPartyId, externalPartyKeyPair, createTransferPreapprovalCommands, noDisclosures);

        // the validator node will automatically accept any transfer preapproval proposal submitted to it.
        CumulativeFilter transferPreapprovalFilter = Ledger.createFilterByTemplate(TemplateId.TRANSFER_PREAPPROVAL_ID);
        System.out.println("Waiting for TransferPreapprovalProposal contract to be accepted for " + externalPartyId + "...");
        waitFor(5 * 1000, 12, () -> {
            List<JsGetActiveContractsResponse> activeContracts = ledgerApi.getActiveContractsByFilter(operator.bearerToken, externalPartyId, List.of(transferPreapprovalFilter));
            return !activeContracts.isEmpty();
        });
    }

    private static void transferAsset(
            SampleUser operator,
            TransferInstruction transferInstructionApi,
            Ledger ledgerApi,
            SampleUser sender,
            SampleUser receiver,
            BigDecimal amount,
            InstrumentId instrumentId) throws Exception {

        List<ContractAndId<HoldingView>> holdings = selectHoldingsForTransfer(operator, ledgerApi, sender, amount, instrumentId);
        if (holdings == null) {
            System.err.println("Insufficient holdings to transfer " + amount + " units");
            System.exit(1);
        }

        printStep("Get transfer factory for " + sender.name);

        Instant requestDate = Instant.now();
        Instant requestExpiresDate = requestDate.plusSeconds(24 * 60 * 60);

        TransferFactory_Transfer proposedTransfer = makeProposedTransfer(sender, receiver, amount, instrumentId, requestDate, requestExpiresDate, holdings);
        TransferFactoryWithChoiceContext transferFactoryWithChoiceContext = transferInstructionApi.getTransferFactory(proposedTransfer);
        TransferFactory_Transfer sentTransfer = adoptChoiceContext(proposedTransfer, transferFactoryWithChoiceContext);
        List<DisclosedContract> disclosures = transferFactoryWithChoiceContext
                .getChoiceContext()
                .getDisclosedContracts()
                .stream()
                .map((d) -> convertRecordViaJson(d, DisclosedContract::fromJson))
                .toList();
        printToken("Transfer factory: ", transferFactoryWithChoiceContext.getFactoryId());

        printStep("Transfer from " + sender.partyId + " to " + receiver.partyId);

        List<Command> transferCommands = Ledger.makeExerciseCommand(
                TemplateId.TRANSFER_FACTORY_INTERFACE_ID,
                "TransferFactory_Transfer",
                transferFactoryWithChoiceContext.getFactoryId(),
                sentTransfer
        );

        if (sender.keyPair.isEmpty()) {
            // transfer from local party
            ledgerApi.submitAndWaitForCommands(
                    operator.bearerToken,
                    sender.partyId,
                    transferCommands,
                    disclosures);
        } else {
            // transfer from external party
            prepareAndSign(ledgerApi, operator.bearerToken, sender.partyId, sender.keyPair.get(), transferCommands, disclosures);
        }
        System.out.println("Transfer complete");
    }

    private static void prepareAndSign(Ledger ledgerApi, String bearerToken, String partyId, KeyPair keyPair, List<Command> commands, List<DisclosedContract> disclosures) throws Exception {
        JsPrepareSubmissionResponse preparedTransaction = ledgerApi.prepareSubmissionForSigning(bearerToken, partyId, commands, disclosures);
        SinglePartySignatures signature = Ledger.makeSingleSignature(preparedTransaction, partyId, keyPair);
        ledgerApi.executeSignedSubmission(preparedTransaction, List.of(signature));
    }

    private static TransferFactory_Transfer makeProposedTransfer(
            SampleUser sender,
            SampleUser receiver,
            BigDecimal amount,
            InstrumentId instrumentId,
            Instant requestedAt,
            Instant executeBefore,
            List<ContractAndId<HoldingView>> holdings) {

        List<Holding.ContractId> holdingCids = holdings
                .stream()
                .map((h) -> new Holding.ContractId(h.contractId()))
                .toList();

        Metadata emptyMetadata = new Metadata(new HashMap<>());
        ChoiceContext noContext = new ChoiceContext(new HashMap<>());
        ExtraArgs blankExtraArgs = new ExtraArgs(noContext, emptyMetadata);

        Transfer transfer = new Transfer(sender.partyId, receiver.partyId, amount, instrumentId, requestedAt, executeBefore, holdingCids, emptyMetadata);
        return new TransferFactory_Transfer(instrumentId.admin, transfer, blankExtraArgs);
    }

    private static TransferFactory_Transfer adoptChoiceContext(
            TransferFactory_Transfer proposed,
            TransferFactoryWithChoiceContext fromApi) throws JsonLfDecoder.Error {

        Metadata emptyMetadata = new Metadata(new HashMap<>());

        // ChoiceContext from the transfer OpenAPI != ChoiceContext generated from the transfer DAR
        String choiceJson = GsonSingleton.getInstance().toJson(fromApi.getChoiceContext().getChoiceContextData());
        ChoiceContext choiceContextFromApi = useValueParser(choiceJson, ChoiceContext::fromJson);

        ExtraArgs populatedExtraArgs = new ExtraArgs(choiceContextFromApi, emptyMetadata);
        return new TransferFactory_Transfer(proposed.expectedAdmin, proposed.transfer, populatedExtraArgs);
    }

    private static void handleException(Exception ex) {
        System.err.println(ex.getMessage());
        ex.printStackTrace();
        System.exit(1);
    }

    private interface WaitLoopCheck {
        boolean getAsBoolean() throws Exception;
    }
}
