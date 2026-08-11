package io.github.miklires.mauth.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;

public class PasswordHasher {

    private final int cost;

    public PasswordHasher(int cost) {
        if (cost < 4 || cost > 31) {
            throw new IllegalArgumentException("BCrypt cost must be 4-31, got " + cost);
        }
        this.cost = cost;
    }

    public String hash(String plain) {
        return BCrypt.withDefaults().hashToString(cost, plain.toCharArray());
    }

    public boolean verify(String plain, String hash) {
        if (hash == null || hash.isEmpty()) return false;
        return BCrypt.verifyer().verify(plain.toCharArray(), hash).verified;
    }
}
