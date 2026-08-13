package io.github.miklires.mauth.discord;

import io.github.miklires.mauth.MAuth;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

class DiscordMessages {

    private final JavaPlugin addon;
    private final MAuth core;
    private FileConfiguration messages;

    DiscordMessages(JavaPlugin addon, MAuth core) {
        this.addon = addon;
        this.core = core;
        load();
    }

    private void load() {
        String code = core.getConfigManager().getDefaultLanguage();
        if (code.equalsIgnoreCase("ru") || code.equalsIgnoreCase("ru_ru")) code = "ru_RU";
        else code = "en_US";
        String path = "lang/" + code + ".yml";
        File file = new File(addon.getDataFolder(), path);
        if (!file.exists()) addon.saveResource(path, false);
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStreamReader reader = new InputStreamReader(
                addon.getResource(path), StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception ignored) {
        }
    }

    String get(String path, String... values) {
        String text = messages.getString(path, "missing:" + path);
        for (int i = 0; i + 1 < values.length; i += 2) {
            text = text.replace("{" + values[i] + "}", values[i + 1]);
        }
        return text;
    }
}
