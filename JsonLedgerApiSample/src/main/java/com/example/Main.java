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
import com.example.client.validator.invoker.ApiException;
import com.example.client.validator.model.*;
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
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.function.BiConsumer;

public class Main {

    private static final BigDecimal estimatedFeesMultiplier = new BigDecimal(0.1);

    public static void main(String[] args) {

        validateEnvironment();

        Ledger ledgerApi = new Ledger(Env.LEDGER_API_URL, Env.VALIDATOR_TOKEN);
        Validator validatorApi = new Validator(Env.VALIDATOR_API_URL, Env.VALIDATOR_TOKEN);
        Scan scanApi = new Scan(Env.SCAN_API_URL);
        ScanProxy scanProxyApi = new ScanProxy(Env.SCAN_PROXY_API_URL, Env.VALIDATOR_TOKEN);
        TransferInstruction transferInstructionApi = new TransferInstruction(Env.SCAN_API_URL);
        TokenMetadata tokenMetadataApi = new TokenMetadata(Env.SCAN_API_URL);

        try {
            // confirm environment and inputs
            confirmConnectivity(ledgerApi, validatorApi, scanApi, scanProxyApi, tokenMetadataApi);
            confirmAuthentication(ledgerApi, validatorApi);

            // onboard the treasury, if necessary
            if (Env.TREASURY_PARTY.isEmpty()) {
                KeyPair treasuryKeyPair = Keys.generate();
                Keys.printKeyPair(Env.TREASURY_PARTY_HINT, treasuryKeyPair);
                Env.TREASURY_PARTY = onboardNewUser(Env.TREASURY_PARTY_HINT, validatorApi, treasuryKeyPair);

                // preapprove Canton Coin transfers
                preapproveTransfers(validatorApi, Env.TREASURY_PARTY, treasuryKeyPair);
            }

            // get the sender key pair
            KeyPair senderKeyPair = null;
            if (Env.SENDER_PUBLIC_KEY.isEmpty() || Env.SENDER_PRIVATE_KEY.isEmpty()) {
                senderKeyPair = Keys.generate();
                Keys.printKeyPair(Env.SENDER_PARTY_HINT, senderKeyPair);
                Env.SENDER_PUBLIC_KEY = Encode.toBase64String(Keys.toRawBytes(senderKeyPair.getPublic()));
                Env.SENDER_PRIVATE_KEY = Encode.toBase64String(Keys.toRawBytes(senderKeyPair.getPrivate(), senderKeyPair.getPublic()));
            } else {
                senderKeyPair = Keys.createFromRawBase64(Env.SENDER_PUBLIC_KEY, Env.SENDER_PRIVATE_KEY);
            }

            // onboard the sender, if necessary
            if (Env.SENDER_PARTY.isEmpty()) {
                Env.SENDER_PARTY = onboardNewUser(Env.SENDER_PARTY_HINT, validatorApi, senderKeyPair);

                // preapprove Canton Coin transfers
                preapproveTransfers(validatorApi, Env.SENDER_PARTY, senderKeyPair);
            }

            // instrument and amount of transfer
            InstrumentId cantonCoinInstrumentId = new InstrumentId(Env.DSO_PARTY, "Amulet");
            printTotalHoldings(ledgerApi, cantonCoinInstrumentId);

            // calculate transfer amount
            BigDecimal transferAmount1 = new BigDecimal(Env.TRANSFER_AMOUNT);
            BigDecimal estimatedFees = transferAmount1.multiply(estimatedFeesMultiplier);
            if(getTotalHoldings(ledgerApi, Env.SENDER_PARTY, cantonCoinInstrumentId).subtract(estimatedFees).compareTo(BigDecimal.ZERO) < 0) {
                transferAmount1 = transferAmount1.add(estimatedFees);
            }

            // perform a transfer from a local party
            transferAsset(
                    transferInstructionApi,
                    ledgerApi,
                    Env.DSO_PARTY,
                    Env.VALIDATOR_PARTY,
                    Optional.empty(),
                    Env.SENDER_PARTY,
                    transferAmount1,
                    cantonCoinInstrumentId);

            printTotalHoldings(ledgerApi, cantonCoinInstrumentId);

            // calculate transfer amount
            BigDecimal transferAmount2 = new BigDecimal(Env.TRANSFER_AMOUNT);

            // perform a transfer from an external party
            BigDecimal priorBalance = getTotalHoldings(ledgerApi, Env.TREASURY_PARTY, cantonCoinInstrumentId);
            transferAsset(
                    transferInstructionApi,
                    ledgerApi,
                    Env.DSO_PARTY,
                    Env.SENDER_PARTY,
                    Optional.of(senderKeyPair),
                    Env.TREASURY_PARTY,
                    transferAmount2,
                    cantonCoinInstrumentId);

            // wait for the treasury party to receive the transfer
            BigDecimal updatedBalance = priorBalance;
            printStep("Waiting for holdings transfer to complete");
            do {
                System.out.println("Waiting...");
                Thread.sleep(2 * 1000);
                updatedBalance = getTotalHoldings(ledgerApi, Env.TREASURY_PARTY, cantonCoinInstrumentId);
            } while(updatedBalance.compareTo(priorBalance) == 0);

            printStep("Success!");
            printTotalHoldings(ledgerApi, cantonCoinInstrumentId);
            System.exit(0);
        } catch (Exception ex) {
            ex.printStackTrace();
            System.exit(1);
        }
    }

    private static void printTotalHoldings(Ledger ledgerApi, InstrumentId cantonCoinInstrumentId) throws Exception {
        BiConsumer<String, String> printTotalHoldingsInternal = (name, party) -> {
            try {
                BigDecimal totalHoldings = getTotalHoldings(ledgerApi, party, cantonCoinInstrumentId);
                System.out.println(name + " has " + totalHoldings + " " + cantonCoinInstrumentId.id);
            } catch (Exception ex) {
                ex.printStackTrace();
                System.exit(1);
            }
        };

        printTotalHoldingsInternal.accept(Env.LEDGER_USER_ID, Env.VALIDATOR_PARTY);
        printTotalHoldingsInternal.accept(Env.SENDER_PARTY_HINT, Env.SENDER_PARTY);
        printTotalHoldingsInternal.accept(Env.TREASURY_PARTY_HINT, Env.TREASURY_PARTY);
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

    private static BigDecimal getTotalHoldings(Ledger ledgerApi, String party, InstrumentId instrumentId) throws Exception {
        return queryForHoldings(ledgerApi, party, instrumentId).stream()
                .map(h -> h.record().amount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static List<ContractAndId<HoldingView>> queryForHoldings(Ledger ledgerApi, String party, InstrumentId instrumentId) throws Exception {
//        printStep("Querying for holdings");
//        System.out.println("Querying for holdings of " + instrumentId.id + " by " + party);

        return ledgerApi.getActiveContractsForInterface(party, TemplateId.HOLDING_INTERFACE_ID.getRaw()).stream()
                        .map(r -> fromInterface(r.getContractEntry(), TemplateId.HOLDING_INTERFACE_ID, HoldingView::fromJson))
                        .filter(v -> v != null && v.record().instrumentId.equals(instrumentId))
                        .toList();
    }

    private static List<ContractAndId<HoldingView>> selectHoldingsForTransfer(Ledger ledgerApi, String party, BigDecimal transferAmount, InstrumentId instrumentId) throws Exception {

        printStep("Selecting holdings");
        System.out.println("Selecting holdings for a " + transferAmount + " unit transfer from " + party);

        final BigDecimal[] remainingReference = {transferAmount};

        List<ContractAndId<HoldingView>> holdingsForTransfer =
                queryForHoldings(ledgerApi, party, instrumentId).stream()
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
        System.out.println("TREASURY_PARTY: " + Env.TREASURY_PARTY);
        printToken("TREASURY_TOKEN", Env.TREASURY_TOKEN);
        System.out.println();
        System.out.println("SENDER_PARTY_HINT: " + Env.SENDER_PARTY_HINT);
        System.out.println("SENDER_PARTY: " + Env.SENDER_PARTY);
        printToken("SENDER_TOKEN", Env.SENDER_TOKEN);
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
                System.err.println(ex.getMessage());
                System.exit(1);
            }
        }

        if (!Env.SENDER_PUBLIC_KEY.isEmpty() && !Env.SENDER_PRIVATE_KEY.isEmpty()) {
            try {
                KeyPair keyPair = Keys.createFromRawBase64(Env.SENDER_PUBLIC_KEY, Env.SENDER_PRIVATE_KEY);
                String calculatedFingerPrint = Encode.toHexString(Keys.fingerPrintOf(keyPair.getPublic()));
                String partyIdFingerPrint = Env.SENDER_PARTY.split("::")[1];
                if(!calculatedFingerPrint.equals(partyIdFingerPrint)) {
                    throw new IllegalArgumentException("The calculated finger print " + calculatedFingerPrint + " does not match the party id.");
                }
            } catch (Exception ex) {
                System.err.println("Error: Check that keys are valid and in raw + public, base64 format.");
                System.err.println(ex.getMessage());
                System.exit(1);
            }
        }

    }

    private static void confirmConnectivity(Ledger ledgerApi, Validator validatorApi, Scan scanApi, ScanProxy scanProxyApi, TokenMetadata tokenMetadataApi) throws Exception {
        printStep("Confirm API connectivity");

        System.out.println("Version: " + ledgerApi.getVersion());

        Env.VALIDATOR_PARTY = validatorApi.getValidatorParty();
        System.out.println("Validator Party: " + validatorApi.getValidatorParty());

        Env.SYNCHRONIZER_ID = scanApi.getSynchronizerId();
        System.out.println("Synchronizer id: " + Env.SYNCHRONIZER_ID);

        Env.DSO_PARTY = scanProxyApi.getDsoPartyId();
        System.out.println("DSO Party: " + Env.DSO_PARTY);

        GetRegistryInfoResponse registryInfo = tokenMetadataApi.getRegistryInfo();
        System.out.println("Registry Party: " + registryInfo.getAdminId());
    }

    private static void confirmAuthentication(Ledger ledgerApi, Validator validatorApi) throws Exception {
        printStep("Confirm authentication");
        // these require a valid Validator token
        System.out.println("Ledger end: " + ledgerApi.getLedgerEnd());
        System.out.println("Participant users: " + ledgerApi.getUsers());
        System.out.println("Validator users: " + validatorApi.listUsers());
    }

    private static String onboardNewUser(String partyHint, Validator validatorApi, KeyPair keyPair) throws Exception {

        printStep("Onboard " + partyHint);

        List<TopologyTx> txs = validatorApi.prepareOnboarding(partyHint, keyPair.getPublic());
        List<SignedTopologyTx> signedTxs = ExternalSigning.signOnboarding(txs, keyPair.getPrivate());
        String newParty = validatorApi.submitOnboarding(signedTxs, keyPair.getPublic());
        System.out.println("New party: " + newParty);

        return newParty;
    }

    // Note: the endpoints used here consume limited resources
    // (i.e., the 200 parties-per-node limitation).
    // TODO: Switch to "bare creating a TransferPreapprovalRequest"
    private static void preapproveTransfers(Validator validatorApi, String externalPartyId, KeyPair externalPartyKeyPair) throws ApiException {
        CreateExternalPartySetupProposalResponse proposalContract = validatorApi.createExternalPartySetupProposal(externalPartyId);
        PrepareAcceptExternalPartySetupProposalResponse preparedAccept = validatorApi.prepareAcceptExternalPartySetupProposal(externalPartyId, proposalContract.getContractId());
        ExternalPartySubmission acceptSubmission = ExternalSigning.signSubmission(externalPartyId, preparedAccept.getTransaction(), preparedAccept.getTxHash(), externalPartyKeyPair);
        validatorApi.submitAcceptExternalPartySetupProposal(acceptSubmission);
    }

    private static void transferAsset(
            TransferInstruction transferInstructionApi,
            Ledger ledgerApi,
            String adminParty,
            String sender,
            Optional<KeyPair> senderKeys,
            String receiver,
            BigDecimal amount,
            InstrumentId instrumentId) throws Exception {

        List<ContractAndId<HoldingView>> holdings = selectHoldingsForTransfer(ledgerApi, sender, amount, instrumentId);
        if (holdings == null) {
            System.err.println("Insufficient holdings to transfer " + amount + " units");
            System.exit(1);
        }

        printStep("Get transfer factory for " + sender);

        Instant requestDate = Instant.now();
        Instant requestExpiresDate = requestDate.plusSeconds(24 * 60 * 60);

        TransferFactory_Transfer proposedTransfer = makeProposedTransfer(adminParty, sender, receiver, amount, instrumentId, requestDate, requestExpiresDate, holdings);
        TransferFactoryWithChoiceContext transferFactoryWithChoiceContext = transferInstructionApi.getTransferFactory(proposedTransfer);
        TransferFactory_Transfer sentTransfer = adoptChoiceContext(proposedTransfer, transferFactoryWithChoiceContext);
        List<DisclosedContract> disclosures = transferFactoryWithChoiceContext
                .getChoiceContext()
                .getDisclosedContracts()
                .stream()
                .map((d) -> convertRecordViaJson(d, DisclosedContract::fromJson))
                .toList();
        printToken("Transfer factory: ", transferFactoryWithChoiceContext.getFactoryId());

        printStep("Transfer from " + sender + " to " + receiver);

        List<Command> transferCommands = Ledger.makeExerciseCommand(
                TemplateId.TRANSFER_FACTORY_INTERFACE_ID,
                "TransferFactory_Transfer",
                transferFactoryWithChoiceContext.getFactoryId(),
                sentTransfer
        );

        if (senderKeys.isEmpty()) {
            System.out.println("Transferring from local party");
            ledgerApi.submitAndWaitForCommands(
                    sender,
                    transferCommands,
                    disclosures);
        } else {
            System.out.println("Transferring from external party");
            JsPrepareSubmissionResponse preparedTransaction = ledgerApi.prepareSubmissionForSigning(sender, transferCommands, disclosures);
            SinglePartySignatures signature = ledgerApi.makeSingleSignature(preparedTransaction, sender, senderKeys.get());

            ledgerApi.executeSignedSubmission(preparedTransaction, List.of(signature));
        }
        System.out.println("Transfer complete");
    }

    private static TransferFactory_Transfer makeProposedTransfer(
            String admin,
            String sender,
            String receiver,
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

        Transfer transfer = new Transfer(sender, receiver, amount, instrumentId, requestedAt, executeBefore, holdingCids, emptyMetadata);
        return new TransferFactory_Transfer(admin, transfer, blankExtraArgs);
    }

    private static TransferFactory_Transfer adoptChoiceContext(
            TransferFactory_Transfer proposed,
            TransferFactoryWithChoiceContext fromApi) throws JsonLfDecoder.Error {

        Metadata emptyMetadata = new Metadata(new HashMap<>());

        // ChoiceContext from the transfer OpenAPI != ChoiceContext generated from the transfer DAR
        String choiceJson = GsonSingleton.getInstance().toJson(fromApi.getChoiceContext().getChoiceContextData());
//        System.out.println("Intermediate choice context JSON: " + choiceJson);
        ChoiceContext choiceContextFromApi = useValueParser(choiceJson, ChoiceContext::fromJson);

        ExtraArgs populatedExtraArgs = new ExtraArgs(choiceContextFromApi, emptyMetadata);
        return new TransferFactory_Transfer(proposed.expectedAdmin, proposed.transfer, populatedExtraArgs);
    }

    private static JsPrepareSubmissionResponse prepareTransferForSigning(
            Ledger ledgerApi,
            TransferFactoryWithChoiceContext factoryWithChoiceContext,
            TransferFactory_Transfer choicePayload,
            List<DisclosedContract> disclosedContracts) throws Exception {

        ExerciseCommand exerciseTransferCommand = new ExerciseCommand()
                .templateId(TemplateId.TRANSFER_FACTORY_INTERFACE_ID.getRaw())
                .contractId(factoryWithChoiceContext.getFactoryId())
                .choice("TransferFactory_Transfer")
                .choiceArgument(choicePayload);

        CommandOneOf3 subtype = new CommandOneOf3()
                .exerciseCommand(exerciseTransferCommand);

        Command command = new Command();
        command.setActualInstance(subtype);

        return ledgerApi.prepareSubmissionForSigning(
                choicePayload.transfer.sender,
                List.of(command),
                disclosedContracts);
    }
}
