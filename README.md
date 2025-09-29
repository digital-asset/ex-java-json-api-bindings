# JSON API Samples with Java

Copyright (c) 2025, by Digital Asset

Permission to use, copy, modify, and/or distribute this software for any purpose
with or without fee is hereby granted.

THE SOFTWARE IS PROVIDED "AS IS" AND THE AUTHOR DISCLAIMS ALL WARRANTIES WITH REGARD TO
THIS SOFTWARE INCLUDING ALL IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS.
IN NO EVENT SHALL THE AUTHOR BE LIABLE FOR ANY SPECIAL, DIRECT, INDIRECT, OR
CONSEQUENTIAL DAMAGES OR ANY DAMAGES WHATSOEVER RESULTING FROM LOSS OF USE,
DATA OR PROFITS, WHETHER IN AN ACTION OF CONTRACT, NEGLIGENCE OR OTHER TORTIOUS ACTION,
ARISING OUT OF OR IN CONNECTION WITH THE USE OR PERFORMANCE OF THIS SOFTWARE.

## Purpose

A minimal sample of using Java to call the JSON API endpoints (Ledger API, Validator API, etc.)
The sample illustrates the endpoints, the request payloads, and their responses for the following:

* Use the Open API specs to generate Java bindings for API endpoints
* Use the `daml codegen` to generate Java bindings for Daml types
* Programmatically retrieve the Synchronizer id
* Programmatically retrieve the DSO party
* Programmatically retrieve the Validator party
* Onboard an external party
* Sign transactions as an external party
* Establish transfer pre-approvals for an external party
* Query for a list of token standard holdings
* Transfer token standard assets
* Digest the transaction stream

The sample uses the following tools:

* Maven
* [Open API Generator](https://openapi-generator.tech/docs/generators/java)

The following elements are not included,
which are typically required for production implementations:

* Detailed Error Handling
* Automatic Retries
* Timeouts
* Circuit Breaker Pattern
* External Configuration
* Logging
* Metrics and Monitoring
* Non-Blocking Calls
* SSL/TLS

## Version

This sample was last tested with Canton Network APIs `0.4.18`.

## Setup

1. **Start** a LocalNet or DevNet validator.
   For example, use [these instructions](https://docs.sync.global/app_dev/testing/localnet.html)
   to start a Docker Compose-based LocalNet on your workstation.
2. **Navigate** to your validator's built-in wallet app.
3. **Initialize** your validator's wallet with CC using the "Tap" button.
4. If your setup requires an authentication token, **create** a script that generates a token for a given username.
5. **Confirm** that you can access the various endpoints.
   Depending on your host and ingress setup, you should be able to do something like:

    ```
    # JSON Ledger API
    curl http://canton.localhost:2975/v2/version
    ```
   
    ```
    # Validator API
    curl http://wallet.localhost:2000/api/validator/v0/validator-user
    ```

    ```
    # Scan API
    curl http://scan.localhost:4000/api/scan/v0/scans 
    ```

    ```
    # Scan Proxy API
    curl --location 'http://wallet.localhost:2903/api/validator/v0/scan-proxy/dso-party-id' \
      --header 'Accept: application/json' \
      --header 'Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJhdWQiOiJodHRwczovL2NhbnRvbi5uZXR3b3JrLmdsb2JhbCIsInN1YiI6ImxlZGdlci1hcGktdXNlciJ9.A0VZW69lWWNVsjZmDDpVvr1iQ_dJLga3f-K2bicdtsc'
    ```

## Running

1. **Export** environment variables, as needed, for the values shown in
   [.env.example](./JsonLedgerApiSample/.env.example).

2. **Run** the sample with either:

    ```
    mvn clean compile exec:java
    ```

    Or:

    ```
    mvn clean package

    java -jar target/JsonLedgerApiSample-1.0-SNAPSHOT.jar
    ```

## Sample output

```
=== Confirm API connectivity ===
Version: 3.3.0-SNAPSHOT
Synchronizer id: global-domain::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98
Registry Party: DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98

=== Confirm authentication ===
DSO Party: DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98
Ledger end: 1389

=== Setup exchange parties (or read from cache) ===
Attempting to read identities from cache file: identities-cache.json
(use `export IDENTITIES_CACHE=-` to disable cache usage)
Using cached identities:
  Synchronizer ID: global-domain::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98
  DSO: DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98
  Exchange: app_user_localnet-localparty-1::1220b154113cfc2077eb1b775aba56588bae2efdbbee8066b1a0f8b5ddaddf22496c
  Treasury: treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57
  Alice: alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa

=== Initialize integration store ===
State of local store after initial ingestion
IntegrationStore{
treasuryParty='treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57'
lastIngestedOffset=1389
sourceSynchronizerId='null'
lastIngestedRecordTime='null'
activeHoldings={
}
userBalances={
}}

=== Print total holdings ===
treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57 has 320.0000000000 Amulet
alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa has 120.0000000000 Amulet


=== Transfer 110.0 from app_user_localnet-localparty-1::1220b154113cfc2077eb1b775aba56588bae2efdbbee8066b1a0f8b5ddaddf22496c to alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa ===
Found sufficient holdings for transfer:
- 221371.2480001280 Amulet
Awaiting completion of transfer from app_user_localnet-localparty-1::1220b154113cfc2077eb1b775aba56588bae2efdbbee8066b1a0f8b5ddaddf22496c to alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa (Command ID d49be36e-b1b8-4e96-bc74-806c0d9fe659)

Transfer complete

=== Print total holdings ===
treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57 has 320.0000000000 Amulet
alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa has 230.0000000000 Amulet


=== Transfer 100 from alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa to treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57 ===
Found sufficient holdings for transfer:
- 20.0000000000 Amulet
- 100.0000000000 Amulet
Awaiting completion of transfer from alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa to treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57 (Command ID 846fb430-9c9d-45a0-aa6f-41863f14d433)

Transfer complete

=== Success! ===

=== Print total holdings ===
treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57 has 420.0000000000 Amulet
alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa has 130.0000000000 Amulet

Sep 29, 2025 5:34:15 PM com.example.store.IntegrationStore$TransactionParser parseTransferInfoFromExerciseEvent
INFO: Detected transfer info TransferInfo[sender=alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa, depositId=f806261e-bb56-4e09-a586-cd1aaffacd10] in exercise result: {"result":{"round":{"number":"39"},"summary":{"inputAppRewardAmount":"0.0000000000","inputValidatorRewardAmount":"0.0000000000","inputSvRewardAmount":"0.0000000000","inputAmuletAmount":"120.0000000000","balanceChanges":[["alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa",{"changeToInitialAmountAsOfRoundZero":"-100.0038051800","changeToHoldingFeesRate":"-0.0038051800"}],["treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57",{"changeToInitialAmountAsOfRoundZero":"100.1484020200","changeToHoldingFeesRate":"0.0038051800"}]],"holdingFees":"0.0000000000","outputFees":["0.0000000000"],"senderChangeFee":"0.0000000000","senderChangeAmount":"20.0000000000","amuletPrice":"0.0050000000","inputValidatorFaucetAmount":"0.0000000000","inputUnclaimedActivityRecordAmount":"0.0000000000"},"createdAmulets":[{"tag":"TransferResultAmulet","value":"008151517d220a572da243f103f4ab8cb0798bd298c80d8c1f93b695b361659f78ca111220200e86a983df8dfe485d4cadbaaa83e0346982b6eabebc9b09fd690a7da12e08"}],"senderChangeAmulet":"00917599df70742bcff71c8d447e713cbb98243a9b2f47a3cdbfe6f55831e4c714ca1112203ca9a25143fac8488345601cf1bff06836191c97563a6f74465580a001971414"},"meta":{"values":{"splice.lfdecentralizedtrust.org/reason":"f806261e-bb56-4e09-a586-cd1aaffacd10","splice.lfdecentralizedtrust.org/sender":"alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa","splice.lfdecentralizedtrust.org/tx-kind":"transfer"}}}
Sep 29, 2025 5:34:15 PM com.example.store.IntegrationStore ingestCreateHoldingEvent
INFO: New active holding for treasury party: 008151517d220a572da243f103f4ab8cb0798bd298c80d8c1f93b695b361659f78ca111220200e86a983df8dfe485d4cadbaaa83e0346982b6eabebc9b09fd690a7da12e08 -> {"owner": "treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57", "instrumentId": {"admin": "DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98", "id": "Amulet"}, "amount": "100.0000000000", "lock": null, "meta": {"values": {"amulet.splice.lfdecentralizedtrust.org/created-in-round": "39", "amulet.splice.lfdecentralizedtrust.org/rate-per-round": "0.00380518"}}}
Sep 29, 2025 5:34:15 PM com.example.store.IntegrationStore ingestCreateHoldingEvent
INFO: Credited 100.0000000000 of splice.api.token.holdingv1.InstrumentId(DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98, Amulet) sent by alice::1220da5e10a23997aafe1f0c643119ca6dc337fde58695dec46e98c25d69955d1baa into deposit f806261e-bb56-4e09-a586-cd1aaffacd10

=== State of local store after final transfer ===
IntegrationStore{
treasuryParty='treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57'
lastIngestedOffset=1393
sourceSynchronizerId='global-domain::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98'
lastIngestedRecordTime='2025-09-29T21:34:14.005959Z'
activeHoldings={
  008151517d220a572da243f103f4ab8cb0798bd298c80d8c1f93b695b361659f78ca111220200e86a983df8dfe485d4cadbaaa83e0346982b6eabebc9b09fd690a7da12e08: splice.api.token.holdingv1.HoldingView(treasury::12202ae2194bd85277907d639b19faa0ac9d74fcb0fc70099850a1c706413134ff57, splice.api.token.holdingv1.InstrumentId(DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98, Amulet), 100.0000000000, Optional.empty, splice.api.token.metadatav1.Metadata({amulet.splice.lfdecentralizedtrust.org/created-in-round=39, amulet.splice.lfdecentralizedtrust.org/rate-per-round=0.00380518}))
}
userBalances={
  f806261e-bb56-4e09-a586-cd1aaffacd10: Balances{
  splice.api.token.holdingv1.InstrumentId(DSO::1220f3accf1fe3abb30f9cd32cff379294a2274ec1578efd6e2eccca09be05046a98, Amulet): 100.0000000000
}}
}}
```
