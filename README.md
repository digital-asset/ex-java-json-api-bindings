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

* Programmatically retrieve the Synchronizer id
* Programmatically retrieve the DSO party
* Programmatically retrieve the Validator party
* Onboard an external party
* Establish transfer pre-approvals for an external party using the [Validator API](https://docs.dev.sync.global/app_dev/validator_api/index.html#external-signing-api)
* Query for a list of token standard holdings
* Transfer token standard assets to an external party

This sample demonstrates how to:

* Sign transactions as an external party
* Use the Open API specs to generate Java bindings for API endpoints
* Use the `daml codegen` to generate Java bindings for Daml types

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

This sample was last tested with Canton Network APIs `0.4.13`.

## Setup

1. **Start** a DevNet-connected or LocalNet validator.
   For example, use [these instructions](https://docs.dev.sync.global/validator_operator/validator_compose.html)
   to start a Docker Compose-based validator on your workstation.
2. **Navigate** to your validator's built-in wallet app.
3. **Initialize** your validator's wallet with CC using the "Tap" button.
4. If your setup requires an authentication token, **create** a script that generates a token for a given username.
5. **Confirm** that you can access the Validator API.

    Depending on your host and ingress setup, you should be able to do something like:
    
    ```
    curl http://wallet.localhost/api/validator/v0/validator-user
    ```

6. **Confirm** that you can access the JSON Ledger API.

   Depending on your host and ingress setup, you should be able to do something like:
   
   ```
   curl http://wallet.localhost/api/participant/v2/version
   ```

   If using the Docker Compose-based validator, you may need to
   [add a location to the `nginx.conf`](https://docs.dev.sync.global/app_dev/ledger_api/index.html#comments) for the JSON Ledger API:
   
    ```
    server {
      listen 80;
      server_name wallet.localhost;
      location /api/validator {
        rewrite ^\/(.*) /$1 break;
        proxy_pass http://validator:5003/api/validator;
      }
      location / {
        proxy_pass http://wallet-web-ui:8080/;
      }

      # expose the Ledger API
      location /api/participant/ {
        proxy_pass http://participant:7575/;
      }
    }
    ```

## Running

1. **Define** environment variables for the values shown in
   [Env.json](./JsonLedgerApiSample/src/main/java/com/example/Env.java),
   including authentication tokens for the Validator's wallet,
   a sender party, and a receiving party.

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
> mvn exec:java

=== Confirm API connectivity ===
Version: 3.3.0-SNAPSHOT
Synchronizer id: global-domain::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a
Registry Party: DSO::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a
Selecting synchronizer id: global-domain::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a
Exchange party not specified, defaulting to validator node party: da-wallace-1::12206b78020b91dac97ee57eccd91bec29074367be0abd2fd5e99f15eb7675b5ecf3

=== Confirm authentication ===
DSO Party: DSO::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a
Ledger end: 37503

=== Generating keypair and wallet for treasury ===
Allocating new external party with hint: treasury
Allocated party: treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb
treasury public key:  t0e+twb9uAxJN1Xe5Aif+Td/a9ZjuXX0qBOmac+MxaI=
treasury private key: xbIIHe2Zh8e4CQa6XRtdnKzdxeeO0XMLy9RU1Wx/3s23R763Bv24DEk3Vd7kCJ/5N39r1mO5dfSoE6Zpz4zFog==


=== Pre-approving treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb for CC transfers ===
Marking offset: 37514
Awaiting completion of transfer preapproval proposal (Command ID c96b385f-5141-4e97-9ca9-cc86c5f2b7ba
Waiting...
Awaiting auto-acceptance of transfer preapproval proposal
Waiting...

=== Generating keypair and wallet for alice ===
Allocating new external party with hint: alice
Allocated party: alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca
alice public key:  wmaaLpmV6wlmFoUVEGdFtlEbfN9JSRSI+r8LYXehZAw=
alice private key: jlpYidK7gtvDESjv6iU1ODdbLXDi2ALsbAi58ROMkBjCZpoumZXrCWYWhRUQZ0W2URt830lJFIj6vwthd6FkDA==


=== Pre-approving alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca for CC transfers ===
Marking offset: 37531
Awaiting completion of transfer preapproval proposal (Command ID ddd82cbe-bde3-41f3-83a4-79a1930ae72e
Waiting...
Awaiting auto-acceptance of transfer preapproval proposal
Waiting...

=== Print total holdings ===
treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb has 0 Amulet
alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca has 0 Amulet


=== Transfer 105.00 from da-wallace-1::12206b78020b91dac97ee57eccd91bec29074367be0abd2fd5e99f15eb7675b5ecf3 to alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca ===
Found sufficient holdings for transfer: 
- 1352135.0771429757 Amulet
Awaiting completion of transfer from da-wallace-1::12206b78020b91dac97ee57eccd91bec29074367be0abd2fd5e99f15eb7675b5ecf3 to alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca (Command ID 17852475-66a5-4580-b4f1-328b0e65a70f)

Transfer complete

=== Print total holdings ===
treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb has 0 Amulet
alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca has 105.0000000000 Amulet


=== Transfer 100 from alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca to treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb ===
Found sufficient holdings for transfer: 
- 105.0000000000 Amulet
Awaiting completion of transfer from alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca to treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb (Command ID ae426779-0ef3-454f-8590-2123a682510c)

Waiting...
Transfer complete

=== Success! ===

=== Print total holdings ===
treasury::12205ce5def793fca34de8fe7838f746b423c49fd1fdcf2ca863f5bf8f5eb568ebfb has 100.0000000000 Amulet
alice::122079cdac6eb9bdd5b387a5063bfd37748ca6c3d3e0478a1c02c026bf68304d19ca has 3.3333333334 Amulet
```
