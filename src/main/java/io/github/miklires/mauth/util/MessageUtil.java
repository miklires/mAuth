package io.github.miklires.mauth.util;

import io.github.miklires.mauth.MAuth;
import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class MessageUtil {

    private final MAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private final Map<String, FileConfiguration> languages = new HashMap<>();
    private String fallback;

    public MessageUtil(MAuth plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File dir = new File(plugin.getDataFolder(), "lang");
        if (!dir.exists() && !dir.mkdirs()) plugin.getLogger().warning("cannot create lang directory");
        migrateLegacy(dir);
        save("lang/en_US.yml");
        save("lang/ru_RU.yml");

        languages.clear();
        languages.put("en_US", load("en_US"));
        languages.put("ru_RU", load("ru_RU"));
        fallback = normalize(plugin.getConfigManager().getDefaultLanguage());
        if (!languages.containsKey(fallback)) {
            File custom = new File(dir, fallback + ".yml");
            if (custom.exists()) languages.put(fallback, YamlConfiguration.loadConfiguration(custom));
            else fallback = "en_US";
        }
    }

    private void migrateLegacy(File dir) {
        File old = new File(plugin.getDataFolder(), "messages.yml");
        File russian = new File(dir, "ru_RU.yml");
        if (!old.exists() || russian.exists()) return;
        try {
            Files.copy(old.toPath(), russian.toPath());
            plugin.getLogger().info("migrated messages.yml to lang/ru_RU.yml");
        } catch (Exception e) {
            plugin.getLogger().warning("message migration failed: " + e.getMessage());
        }
    }

    private void save(String path) {
        File file = new File(plugin.getDataFolder(), path);
        if (!file.exists()) plugin.saveResource(path, false);
    }

    private FileConfiguration load(String code) {
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(
                new File(plugin.getDataFolder(), "lang/" + code + ".yml"));
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("lang/" + code + ".yml"), StandardCharsets.UTF_8)) {
            cfg.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception ignored) {
        }
        return cfg;
    }

    private FileConfiguration language(Audience target) {
        if (target instanceof Player player && plugin.getConfigManager().isPerPlayerLanguage()) {
            Locale locale = player.locale();
            String code = locale.getLanguage().equalsIgnoreCase("ru") ? "ru_RU" : "en_US";
            return languages.getOrDefault(code, languages.get(fallback));
        }
        return languages.get(fallback);
    }

    private String normalize(String code) {
        if (code == null) return "en_US";
        if (code.equalsIgnoreCase("ru") || code.equalsIgnoreCase("ru_ru")) return "ru_RU";
        if (code.equalsIgnoreCase("en") || code.equalsIgnoreCase("en_us")) return "en_US";
        return code;
    }

    public Component get(String path, TagResolver... resolvers) {
        return get(languages.get(fallback), path, true, resolvers);
    }

    public Component getPlain(String path, TagResolver... resolvers) {
        return get(languages.get(fallback), path, false, resolvers);
    }

    public Component getPlain(Player player, String path, TagResolver... resolvers) {
        return get(language(player), path, false, resolvers);
    }

    private Component get(FileConfiguration cfg, String path, boolean withPrefix, TagResolver... resolvers) {
        String raw = cfg.getString(path, "<red>missing:" + path);
        String prefix = withPrefix ? cfg.getString("prefix", "") : "";
        return mm.deserialize(prefix + raw, resolvers);
    }

    public void send(Audience target, String path, TagResolver... resolvers) {
        target.sendMessage(get(language(target), path, true, resolvers));
    }

    public static TagResolver ph(String key, String value) {
        return Placeholder.unparsed(key, value == null ? "" : value);
    }

    public static TagResolver ph(String key, int value) {
        return Placeholder.unparsed(key, String.valueOf(value));
    }
}
