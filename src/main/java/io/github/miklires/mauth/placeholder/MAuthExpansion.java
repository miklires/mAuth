package io.github.miklires.mauth.placeholder;

import io.github.miklires.mauth.MAuth;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public final class MAuthExpansion extends PlaceholderExpansion {

    private final MAuth plugin;

    public MAuthExpansion(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "mauth";
    }

    @Override
    public @NotNull String getAuthor() {
        return String.join(", ", plugin.getPluginMeta().getAuthors());
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getPluginMeta().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public @Nullable String onRequest(OfflinePlayer player, @NotNull String parameters) {
        if (parameters.equalsIgnoreCase("version")) return getVersion();
        boolean authenticated = player != null && plugin.isAuthenticated(player.getUniqueId());
        return switch (parameters.toLowerCase(java.util.Locale.ROOT)) {
            case "authenticated" -> Boolean.toString(authenticated);
            case "status" -> authenticated ? "authenticated" : "unauthenticated";
            default -> null;
        };
    }
}
