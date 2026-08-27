package io.github.miklires.mauth.config;

import org.bukkit.configuration.file.YamlConfiguration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DefaultResourcesTest {

    @Test
    void cleanInstallUsesSafeEnglishDefaults() {
        YamlConfiguration config = load("config.yml");

        assertEquals("en_US", config.getString("language.default"));
        assertEquals(60, config.getInt("security.login-timeout-seconds"));
        assertEquals(120, config.getInt("security.registration-timeout-seconds"));
        assertTrue(config.getBoolean("security.auth-bossbar.enabled"));
        assertTrue(config.getBoolean("security.block-duplicate-sessions"));
        assertTrue(config.getBoolean("security.block-shared-ip-sessions"));
    }

    @Test
    void bundledLanguagesExposeTheSameMessageKeys() {
        YamlConfiguration english = load("lang/en_US.yml");
        YamlConfiguration russian = load("lang/ru_RU.yml");

        assertEquals(english.getKeys(true), russian.getKeys(true));
        assertNotNull(english.getString("auth.bossbar-login"));
        assertNotNull(russian.getString("auth.bossbar-register"));
    }

    private YamlConfiguration load(String name) {
        InputStream stream = getClass().getClassLoader().getResourceAsStream(name);
        assertNotNull(stream, "missing test resource " + name);
        return YamlConfiguration.loadConfiguration(
                new InputStreamReader(stream, StandardCharsets.UTF_8));
    }
}
