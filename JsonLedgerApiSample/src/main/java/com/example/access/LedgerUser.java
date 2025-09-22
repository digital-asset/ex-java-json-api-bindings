package com.example.access;

public record LedgerUser(
    String userId,
    String identityProviderId,
    String bearerToken
) {
    public static LedgerUser validateUserToken(String bearerToken, String identityProviderId) throws IllegalArgumentException {

        Jwt bearerJwt = Jwt.fromString(bearerToken);

        String userId = bearerJwt.readSubject();
        if (userId == null) {
            throw new IllegalArgumentException("Provided bearer token did not specify a ledger user ID");
        }

        return new LedgerUser(userId, identityProviderId, bearerToken);
    }
}
