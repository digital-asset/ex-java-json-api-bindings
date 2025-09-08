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

=== Print environment variables ===
LEDGER_API_URL: http://wallet.localhost/api/participant
VALIDATOR_API_URL: http://wallet.localhost/api/validator
SCAN_PROXY_API_URL: http://wallet.localhost/api/validator

LEDGER_USER_ID: ledger-api-user
VALIDATOR_TOKEN: eyJraWQiOi..._gvb4rgebD

TREASURY_PARTY_HINT: treasury
TREASURY_PARTY: treasury::1220c384522ff5434e5f9107b0c39a7198f8522ba69bc510fe27f3f8b62af2bb2558
TREASURY_TOKEN: eyJraWQiOi...JvBPOg0Orn

SENDER_PARTY_HINT: alice
SENDER_PARTY: alice::122087d28d81c050f15a2270c4fa16f3de7a516338829319931663d385da993b1c0c
SENDER_TOKEN: eyJraWQiOi...Or66kgN9l0
SENDER_PUBLIC_KEY: h8hnZ+2d9kWz5G8RsHe+6JGIyhzGp1guYwNLvL+g//M=
SENDER_PRIVATE_KEY: Dk+Ncdk7PfzUJ3gj6Ch42V/QloS/eiHOJqen6v2p7/qHyGdn7Z32RbPkbxGwd77okYjKHManWC5jA0u8v6D/8w==

=== Confirm API connectivity ===
Version: 3.3.0-SNAPSHOT
Validator Party: da-wallace-1::122055a866a25d4829b64f629f37dbde2abecc8832cad66ee85e11e5e268e25e539d
Synchronizer id: global-domain::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a
DSO Party: DSO::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a
Registry Party: DSO::1220be58c29e65de40bf273be1dc2b266d43a9a002ea5b18955aeef7aac881bb471a

=== Confirm authentication ===
Ledger end: 56278
Participant users: [administrator, alice, da-wallace-1, da-wallace-2, ledger-api-user, participant_admin, test-wallet-1]
Validator users: [administrator, test-wallet-1, da-wallace-1, da-wallace-2]
ledger-api-user has 1999954850.3266628858 Amulet
alice has 11.3000000000 Amulet
treasury has 880.0000000000 Amulet

=== Selecting holdings ===
Selecting holdings for a 9900.0000000000000499600361081320443190634250640869140625000 unit transfer from da-wallace-1::122055a866a25d4829b64f629f37dbde2abecc8832cad66ee85e11e5e268e25e539d
Found sufficient holdings for transfer: 
- 1999953605.1525355098 Amulet

=== Get transfer factory for da-wallace-1::122055a866a25d4829b64f629f37dbde2abecc8832cad66ee85e11e5e268e25e539d ===
Transfer factory: : 009f00e5bf...2418207238

=== Transfer from da-wallace-1::122055a866a25d4829b64f629f37dbde2abecc8832cad66ee85e11e5e268e25e539d to alice::122087d28d81c050f15a2270c4fa16f3de7a516338829319931663d385da993b1c0c ===
Transferring from local party
Transfer complete
ledger-api-user has 1999944921.2266628858 Amulet
alice has 9911.3000000000 Amulet
treasury has 880.0000000000 Amulet

=== Selecting holdings ===
Selecting holdings for a 9000 unit transfer from alice::122087d28d81c050f15a2270c4fa16f3de7a516338829319931663d385da993b1c0c
Found sufficient holdings for transfer: 
- 11.3000000000 Amulet
- 9900.0000000000 Amulet

=== Get transfer factory for alice::122087d28d81c050f15a2270c4fa16f3de7a516338829319931663d385da993b1c0c ===
Transfer factory: : 009f00e5bf...2418207238

=== Transfer from alice::122087d28d81c050f15a2270c4fa16f3de7a516338829319931663d385da993b1c0c to treasury::1220c384522ff5434e5f9107b0c39a7198f8522ba69bc510fe27f3f8b62af2bb2558 ===
Transferring from external party
Transfer complete

=== Waiting for holdings transfer to complete ===
Waiting...
Waiting...

=== Success! ===
ledger-api-user has 1999944921.2266628858 Amulet
alice has 883.1000000000 Amulet
treasury has 9880.0000000000 Amulet
```
