# JSON Ledger API Samples with Java

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

* Onboarding an external party

This sample demonstrates how to:

* Sign transactions as an external party
* Use the Open API specs to generate Java bindings for API endpoints
* Use the `daml codegen` to generate Java bindings for Daml types
* Establish transfer pre-approvals for an external party using the [Validator API](https://docs.dev.sync.global/app_dev/validator_api/index.html#external-signing-api)

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
=== Print environment variables ===
LEDGER_API_URL: http://wallet.localhost/api/participant
VALIDATOR_API_URL: http://wallet.localhost/api/validator
VALIDATOR_TOKEN: eyJraWQiOi...5URFjPQSXd
SENDER_TOKEN: eyJraWQiOi...cTP_GNl6KG
RECEIVER_TOKEN: eyJraWQiOi...EKqV6kPNSd
SENDER_PARTY_HINT: alice
RECEIVER_PARTY_HINT: treasury

=== Confirm API connectivity ===
Version: 3.3.0-SNAPSHOT
Party: da-wallace-1::122055a866a25d4829b64f629f37dbde2abecc8832cad66ee85e11e5e268e25e539d

=== Confirm authentication ===
Ledger end: 31913
Validator users: [administrator, test-wallet-1, da-wallace-1, da-wallace-2]
==================== KEY FOR alice ============
Public key algorithm: EdDSA
              format: X.509
      (Java, base64): MCowBQYDK2VwAyEAMl1zNdVIbd5ywjXKMO2mU4mEPewXI1pv7FYDY5sy9l0=
       (raw, base64): Ml1zNdVIbd5ywjXKMO2mU4mEPewXI1pv7FYDY5sy9l0=
          (raw, hex): 325d7335d5486dde72c235ca30eda65389843dec17235a6fec5603639b32f65d
 Private key algorithm: EdDSA
                format: PKCS#8
        (Java, base64): MC4CAQAwBQYDK2VwBCIEIOZTRMZmth6F6DktEEFCgXCMxxxPJwVPL3RsNs76ZnpK
(raw + public, base64): 5lNExma2HoXoOS0QQUKBcIzHHE8nBU8vdGw2zvpmekoyXXM11Uht3nLCNcow7aZTiYQ97BcjWm/sVgNjmzL2XQ==
   (raw + public, hex): e65344c666b61e85e8392d10414281708cc71c4f27054f2f746c36cefa667a4a325d7335d5486dde72c235ca30eda65389843dec17235a6fec5603639b32f65d


=== Onboard alice ===

New party: alice::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475

generate response: {"party_id":"alice::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475","topology_txs":[{"topology_tx":"CowBCAEQARqFAQqCAQpEMTIyMGVmNGQ4YWZjYWQ4NTA5ODk0YTRkMGJkMjYzMDkwYmQ1NDhjYTE2NjRlNmIyNGM1ZmRlMmY3YWE2YWE5Mzc0NzUSOBAEGiwwKjAFBgMrZXADIQAyXXM11Uht3nLCNcow7aZTiYQ97BcjWm/sVgNjmzL2XSoEAQMEBTABIgAQHg\u003d\u003d","hash":"1220bf5802095ddc7ef003c0585fa7a2c9a751d116f8d6aefad2c428119642519aa8"},{"topology_tx":"CrEBCAEQARqqAUqnAQpLYWxpY2U6OjEyMjBlZjRkOGFmY2FkODUwOTg5NGE0ZDBiZDI2MzA5MGJkNTQ4Y2ExNjY0ZTZiMjRjNWZkZTJmN2FhNmFhOTM3NDc1EAEaVgpSZGEtd2FsbGFjZS0xOjoxMjIwNTVhODY2YTI1ZDQ4MjliNjRmNjI5ZjM3ZGJkZTJhYmVjYzg4MzJjYWQ2NmVlODVlMTFlNWUyNjhlMjVlNTM5ZBACEB4\u003d","hash":"12206c37653e6a2a4b18df54fd074433e6a2be201e4625d6ab0681dbb6c7192b7fda"},{"topology_tx":"CpQBCAEQARqNAYIBiQEKS2FsaWNlOjoxMjIwZWY0ZDhhZmNhZDg1MDk4OTRhNGQwYmQyNjMwOTBiZDU0OGNhMTY2NGU2YjI0YzVmZGUyZjdhYTZhYTkzNzQ3NRgBIjgQBBosMCowBQYDK2VwAyEAMl1zNdVIbd5ywjXKMO2mU4mEPewXI1pv7FYDY5sy9l0qBAEDBAUwARAe","hash":"1220a6650f2521992c025c007f373e7881cb39380457f879c9497dfa3a347233cd70"}]}

submit onboarding request: {"public_key":"325d7335d5486dde72c235ca30eda65389843dec17235a6fec5603639b32f65d","signed_topology_txs":[{"topology_tx":"CowBCAEQARqFAQqCAQpEMTIyMGVmNGQ4YWZjYWQ4NTA5ODk0YTRkMGJkMjYzMDkwYmQ1NDhjYTE2NjRlNmIyNGM1ZmRlMmY3YWE2YWE5Mzc0NzUSOBAEGiwwKjAFBgMrZXADIQAyXXM11Uht3nLCNcow7aZTiYQ97BcjWm/sVgNjmzL2XSoEAQMEBTABIgAQHg\u003d\u003d","signed_hash":"2e8ce50d4ddd43c20f1d53c25a2d53909ebdfc3c232995151313d021fe290ce1a973d25323d7a72d9b735e5d2dcb061794b31c2afa42eada76d5f0d931469104"},{"topology_tx":"CrEBCAEQARqqAUqnAQpLYWxpY2U6OjEyMjBlZjRkOGFmY2FkODUwOTg5NGE0ZDBiZDI2MzA5MGJkNTQ4Y2ExNjY0ZTZiMjRjNWZkZTJmN2FhNmFhOTM3NDc1EAEaVgpSZGEtd2FsbGFjZS0xOjoxMjIwNTVhODY2YTI1ZDQ4MjliNjRmNjI5ZjM3ZGJkZTJhYmVjYzg4MzJjYWQ2NmVlODVlMTFlNWUyNjhlMjVlNTM5ZBACEB4\u003d","signed_hash":"5444289e5a81b1ff561f06d35dcbf0146fb96aafdfdc42a3085e81e65540b85673aa5de9224d307c30fcf994f7045f06b17b841966aa6ae1f6d49bf4d44b4304"},{"topology_tx":"CpQBCAEQARqNAYIBiQEKS2FsaWNlOjoxMjIwZWY0ZDhhZmNhZDg1MDk4OTRhNGQwYmQyNjMwOTBiZDU0OGNhMTY2NGU2YjI0YzVmZGUyZjdhYTZhYTkzNzQ3NRgBIjgQBBosMCowBQYDK2VwAyEAMl1zNdVIbd5ywjXKMO2mU4mEPewXI1pv7FYDY5sy9l0qBAEDBAUwARAe","signed_hash":"4644417bb34c6b65bf90c6f30b89a3b5a40fe475cc8afa9594561a2c71d058c55f3cfe93fef0987a76d7299518d0756498103e804ed2a6eadff7b78ad2df4107"}]}


submit onboarding response: {"party_id":"alice::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475"}

New party: alice::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475
==================== KEY FOR treasury ============
Public key algorithm: EdDSA
              format: X.509
      (Java, base64): MCowBQYDK2VwAyEANhM6ap/kLoiCKxg69VzL3hQ0XJjYBZvm00TQhOCZthU=
       (raw, base64): NhM6ap/kLoiCKxg69VzL3hQ0XJjYBZvm00TQhOCZthU=
          (raw, hex): 36133a6a9fe42e88822b183af55ccbde14345c98d8059be6d344d084e099b615
 Private key algorithm: EdDSA
                format: PKCS#8
        (Java, base64): MC4CAQAwBQYDK2VwBCIEILn5gkA8WJM19aQtcYCjFBDg3BTjxCt+0jz6FvkqkNHO
(raw + public, base64): ufmCQDxYkzX1pC1xgKMUEODcFOPEK37SPPoW+SqQ0c42Ezpqn+QuiIIrGDr1XMveFDRcmNgFm+bTRNCE4Jm2FQ==
   (raw + public, hex): b9f982403c589335f5a42d7180a31410e0dc14e3c42b7ed23cfa16f92a90d1ce36133a6a9fe42e88822b183af55ccbde14345c98d8059be6d344d084e099b615


=== Onboard treasury ===

New party: treasury::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475

generate response: {"party_id":"treasury::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475","topology_txs":[{"topology_tx":"CowBCAEQARqFAQqCAQpEMTIyMGVmNGQ4YWZjYWQ4NTA5ODk0YTRkMGJkMjYzMDkwYmQ1NDhjYTE2NjRlNmIyNGM1ZmRlMmY3YWE2YWE5Mzc0NzUSOBAEGiwwKjAFBgMrZXADIQAyXXM11Uht3nLCNcow7aZTiYQ97BcjWm/sVgNjmzL2XSoEAQMEBTABIgAQHg\u003d\u003d","hash":"1220bf5802095ddc7ef003c0585fa7a2c9a751d116f8d6aefad2c428119642519aa8"},{"topology_tx":"CrQBCAEQARqtAUqqAQpOdHJlYXN1cnk6OjEyMjBlZjRkOGFmY2FkODUwOTg5NGE0ZDBiZDI2MzA5MGJkNTQ4Y2ExNjY0ZTZiMjRjNWZkZTJmN2FhNmFhOTM3NDc1EAEaVgpSZGEtd2FsbGFjZS0xOjoxMjIwNTVhODY2YTI1ZDQ4MjliNjRmNjI5ZjM3ZGJkZTJhYmVjYzg4MzJjYWQ2NmVlODVlMTFlNWUyNjhlMjVlNTM5ZBACEB4\u003d","hash":"1220723032210563c7a20800735fa7d0f53563a698faf05520cf0ec52df12f43adce"},{"topology_tx":"CpcBCAEQARqQAYIBjAEKTnRyZWFzdXJ5OjoxMjIwZWY0ZDhhZmNhZDg1MDk4OTRhNGQwYmQyNjMwOTBiZDU0OGNhMTY2NGU2YjI0YzVmZGUyZjdhYTZhYTkzNzQ3NRgBIjgQBBosMCowBQYDK2VwAyEAMl1zNdVIbd5ywjXKMO2mU4mEPewXI1pv7FYDY5sy9l0qBAEDBAUwARAe","hash":"1220aea960b3d0099f72924e197d0ed2d2a251115f218b204f78931deed7be302047"}]}

submit onboarding request: {"public_key":"325d7335d5486dde72c235ca30eda65389843dec17235a6fec5603639b32f65d","signed_topology_txs":[{"topology_tx":"CowBCAEQARqFAQqCAQpEMTIyMGVmNGQ4YWZjYWQ4NTA5ODk0YTRkMGJkMjYzMDkwYmQ1NDhjYTE2NjRlNmIyNGM1ZmRlMmY3YWE2YWE5Mzc0NzUSOBAEGiwwKjAFBgMrZXADIQAyXXM11Uht3nLCNcow7aZTiYQ97BcjWm/sVgNjmzL2XSoEAQMEBTABIgAQHg\u003d\u003d","signed_hash":"2e8ce50d4ddd43c20f1d53c25a2d53909ebdfc3c232995151313d021fe290ce1a973d25323d7a72d9b735e5d2dcb061794b31c2afa42eada76d5f0d931469104"},{"topology_tx":"CrQBCAEQARqtAUqqAQpOdHJlYXN1cnk6OjEyMjBlZjRkOGFmY2FkODUwOTg5NGE0ZDBiZDI2MzA5MGJkNTQ4Y2ExNjY0ZTZiMjRjNWZkZTJmN2FhNmFhOTM3NDc1EAEaVgpSZGEtd2FsbGFjZS0xOjoxMjIwNTVhODY2YTI1ZDQ4MjliNjRmNjI5ZjM3ZGJkZTJhYmVjYzg4MzJjYWQ2NmVlODVlMTFlNWUyNjhlMjVlNTM5ZBACEB4\u003d","signed_hash":"15d3eba447cdcafff61bc7216741215b305d29b625a605266e9e1068f3583f2c55f56eb86de6c63e0a0b6b40007977c29f9a810ac2a5b914920c12e9467a5700"},{"topology_tx":"CpcBCAEQARqQAYIBjAEKTnRyZWFzdXJ5OjoxMjIwZWY0ZDhhZmNhZDg1MDk4OTRhNGQwYmQyNjMwOTBiZDU0OGNhMTY2NGU2YjI0YzVmZGUyZjdhYTZhYTkzNzQ3NRgBIjgQBBosMCowBQYDK2VwAyEAMl1zNdVIbd5ywjXKMO2mU4mEPewXI1pv7FYDY5sy9l0qBAEDBAUwARAe","signed_hash":"bb620ee9e24d1787e34771da5581baeb69ec3ce7392a4b7134c91b531b66d8d3cbfea697bce4cc66519a1024ee21f1706fda77b1cf35169bbd808199c8fabf0e"}]}


submit onboarding response: {"party_id":"treasury::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475"}

New party: treasury::1220ef4d8afcad8509894a4d0bd263090bd548ca1664e6b24c5fde2f7aa6aa937475

```
