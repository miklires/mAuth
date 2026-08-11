package io.github.miklires.mauth.util;

import net.kyori.adventure.audience.Audience;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.minimessage.tag.resolver.Placeholder;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import io.github.miklires.mauth.MAuth;

import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

public class MessageUtil {

    private final MAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();
    private FileConfiguration messages;
    private String prefix;

    public MessageUtil(MAuth plugin) {
        this.plugin = plugin;
        reload();
    }

    public void reload() {
        File file = new File(plugin.getDataFolder(), "messages.yml");
        if (!file.exists()) {
            plugin.saveResource("messages.yml", false);
        }
        messages = YamlConfiguration.loadConfiguration(file);
        try (InputStreamReader reader = new InputStreamReader(
                plugin.getResource("messages.yml"), StandardCharsets.UTF_8)) {
            messages.setDefaults(YamlConfiguration.loadConfiguration(reader));
        } catch (Exception ignored) {
        }
        prefix = messages.getString("prefix", "");
    }

    public Component get(String path, TagResolver... resolvers) {
        String raw = messages.getString(path, "<red>missing:" + path);
        return mm.deserialize(prefix + raw, resolvers);
    }

    public Component getPlain(String path, TagResolver... resolvers) {
        String raw = messages.getString(path, "<red>missing:" + path);
        return mm.deserialize(raw, resolvers);
    }

    public void send(Audience target, String path, TagResolver... resolvers) {
        target.sendMessage(get(path, resolvers));
    }

    public static TagResolver ph(String key, String value) {
        return Placeholder.parsed(key, value);
    }

    public static TagResolver ph(String key, int value) {
        return Placeholder.parsed(key, String.valueOf(value));
    }
}
