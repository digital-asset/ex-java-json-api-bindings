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

A minimal sample of using Java to call the JSON Ledger API endpoints. The sample illustrates the endpoints, the request payloads, and their responses for the following:

* use the validator API to create the external parties
* external signing
* token standard

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

1. **Start** a DevNet-connected validator.
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
   [add a location to the `nginx.conf`](https://docs.dev.sync.global/app_dev/ledger_api/index.html#comments):
   
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

**Run** the sample with either:

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
Version: 3.3.0-SNAPSHOT
Party: da-wallace-1::122055a866a25d4829b64f629f37dbde2abecc8832cad66ee85e11e5e268e25e539d
```