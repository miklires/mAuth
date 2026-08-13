package io.github.miklires.mauth.auth;

import at.favre.lib.crypto.bcrypt.BCrypt;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PasswordHasherTest {

    private final PasswordHasher hasher = new PasswordHasher("argon2id", 12, 8192, 2, 1);

    @Test
    void argon2RoundTrip() {
        String hash = hasher.hash("tulip boat 49");
        assertTrue(hash.startsWith("$argon2id$v=19$"));
        assertTrue(hasher.verify("tulip boat 49", hash));
        assertFalse(hasher.verify("tulip boat 48", hash));
        assertFalse(hasher.needsRehash(hash));
    }

    @Test
    void oldBcryptStillWorks() {
        String hash = BCrypt.withDefaults().hashToString(4, "old password".toCharArray());
        assertTrue(hasher.verify("old password", hash));
        assertTrue(hasher.needsRehash(hash));
    }

    @Test
    void rejectsBrokenHashes() {
        assertFalse(hasher.verify("x", "$argon2id$v=19$m=no,t=3,p=1$bad$bad"));
        assertFalse(hasher.verify("x", "$argon2i$v=19$m=8192,t=2,p=1$aaaa$bbbb"));
        assertFalse(hasher.verify("x", ""));
    }

    @Test
    void verifiesAuthMeSha256() {
        String hash = "$SHA$3d4b303ad6ee1a8a$11d3f0ba42faa6f1d88b8bda832c89be4374fa54d4804d430df5d355b8a2e254";
        assertTrue(hasher.verify("password to hash", hash));
        assertFalse(hasher.verify("Password to hash", hash));
        assertTrue(hasher.needsRehash(hash));
    }

    @Test
    void verifiesPrefixedDigests() {
        assertTrue(hasher.verify("password", "{MD5}5f4dcc3b5aa765d61d8327deb882cf99"));
        assertTrue(hasher.verify("password",
                "{SHA256}5e884898da28047151d0e56f8dc6292773603d0d6aabbdd62a11ef721d1542d8"));
        assertFalse(hasher.verify("wrong", "{MD5}5f4dcc3b5aa765d61d8327deb882cf99"));
    }

    @Test
    void rejectsExcessiveArgon2Parameters() {
        assertFalse(hasher.verify("x", "$argon2id$v=19$m=1048576,t=3,p=1$YWJjZGVmZ2g$YWJjZGVmZ2hpamtsbW5vcA"));
    }
}
