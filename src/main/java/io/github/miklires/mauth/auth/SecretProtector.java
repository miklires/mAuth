package io.github.miklires.mauth.auth;

import io.github.miklires.mauth.MAuth;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class SecretProtector {

    private final SecureRandom random = new SecureRandom();
    private final SecretKeySpec key;

    public SecretProtector(MAuth plugin) {
        String encoded = plugin.getConfig().getString("security.data-encryption-key", "");
        if (encoded == null || encoded.isBlank()) {
            byte[] generated = new byte[32];
            random.nextBytes(generated);
            encoded = Base64.getEncoder().encodeToString(generated);
            plugin.getConfig().set("security.data-encryption-key", encoded);
            plugin.saveConfig();
        }
        byte[] raw;
        try {
            raw = Base64.getDecoder().decode(encoded);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("security.data-encryption-key is not base64", e);
        }
        if (raw.length != 32) throw new IllegalArgumentException("security.data-encryption-key must be 32 bytes");
        key = new SecretKeySpec(raw, "AES");
    }

    public String encrypt(String value) {
        try {
            byte[] iv = new byte[12];
            random.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(128, iv));
            byte[] encrypted = cipher.doFinal(value.getBytes(StandardCharsets.UTF_8));
            byte[] result = new byte[iv.length + encrypted.length];
            System.arraycopy(iv, 0, result, 0, iv.length);
            System.arraycopy(encrypted, 0, result, iv.length, encrypted.length);
            return Base64.getEncoder().encodeToString(result);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    public String decrypt(String value) {
        try {
            byte[] raw = Base64.getDecoder().decode(value);
            if (raw.length < 29) throw new IllegalArgumentException("encrypted value is too short");
            byte[] iv = java.util.Arrays.copyOfRange(raw, 0, 12);
            byte[] encrypted = java.util.Arrays.copyOfRange(raw, 12, raw.length);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(128, iv));
            return new String(cipher.doFinal(encrypted), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            throw new IllegalStateException("cannot decrypt protected value", e);
        }
    }
}
