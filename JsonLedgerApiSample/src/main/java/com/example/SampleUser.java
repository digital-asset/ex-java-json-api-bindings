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

import java.security.KeyPair;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.Optional;
import java.util.function.BiFunction;

public class SampleUser {

    public final String Name;
    public final String PartyId;
    public final String BearerToken;
    public final Optional<KeyPair> KeyPair;

    // partyId is not provided
    public SampleUser(String name, String bearerToken, BiFunction<String, KeyPair, String> onboardNewParty) throws Exception {
        if (isNullOrBlank(name)) {
            throw new IllegalArgumentException("The field name cannot be blank.");
        }
        this.Name = name;
        this.KeyPair = Optional.of(createKeyPair(null, null));
        this.PartyId = onboardNewParty.apply(name, this.KeyPair.get());
        this.BearerToken = bearerToken;
    }

    // keypair is not provided
    public SampleUser(String name, String bearerToken, String partyId) {
        if (isNullOrBlank(name)) {
            throw new IllegalArgumentException("The field name cannot be blank.");
        }
        this.Name = name;
        this.BearerToken = bearerToken;

        if (isNullOrBlank(partyId)) {
            throw new IllegalArgumentException("The field partyId for " + name + " cannot be blank.");
        }
        this.PartyId = partyId;
        this.KeyPair = Optional.empty();
    }

    // everything is provided
    public SampleUser(String name, String bearerToken, String partyId, String publicKey, String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        if (isNullOrBlank(name)) {
            throw new IllegalArgumentException("The field name cannot be blank.");
        }
        this.Name = name;
        this.BearerToken = bearerToken;

        if (isNullOrBlank(partyId)) {
            throw new IllegalArgumentException("The field partyId for " + name + " cannot be blank.");
        }
        this.PartyId = partyId;

        if (isNullOrBlank(publicKey) || isNullOrBlank(privateKey)) {
            throw new IllegalArgumentException("The public and private keys for " + name + " cannot be unset.");
        }
        this.KeyPair = Optional.of(createKeyPair(publicKey, privateKey));
    }

    private static KeyPair createKeyPair(String publicKey, String privateKey) throws NoSuchAlgorithmException, InvalidKeySpecException {
        return (isNullOrBlank(publicKey) || isNullOrBlank(privateKey)) ?
                Keys.generate() :
                Keys.createAndValidateKeyPair(publicKey, privateKey);
    }

    private static boolean isNullOrBlank(String s) {
        return s == null || s.isBlank();
    }
}
