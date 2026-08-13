package io.github.miklires.mauth.discord;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.api.DiscordIntegration;
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

public final class DiscordAddon extends JavaPlugin {

    private DiscordBot bot;

    @Override
    public void onEnable() {
        MAuth core = (MAuth) getServer().getPluginManager().getPlugin("mAuth");
        if (core == null || !core.isEnabled()) {
            getLogger().severe("mAuth is not available");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!core.getConfigManager().isDiscordEnabled()) {
            getLogger().info("discord integration disabled in mAuth config");
            return;
        }

        bot = new DiscordBot(core, new DiscordMessages(this, core));
        getServer().getServicesManager().register(DiscordIntegration.class, bot, this, ServicePriority.Normal);
        try {
            bot.start();
        } catch (Exception e) {
            getLogger().severe("discord bot failed to start: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        getServer().getServicesManager().unregisterAll(this);
        if (bot != null) bot.shutdown();
    }
}
