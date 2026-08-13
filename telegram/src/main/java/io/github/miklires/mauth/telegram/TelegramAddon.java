package io.github.miklires.mauth.telegram;

import io.github.miklires.mauth.MAuth;
import org.bukkit.plugin.java.JavaPlugin;

public final class TelegramAddon extends JavaPlugin {

    private TelegramBot bot;

    @Override
    public void onEnable() {
        MAuth core = (MAuth) getServer().getPluginManager().getPlugin("mAuth");
        if (core == null || !core.isEnabled()) {
            getLogger().severe("mAuth is not available");
            getServer().getPluginManager().disablePlugin(this);
            return;
        }
        if (!core.getConfigManager().isTelegramEnabled()) {
            getLogger().info("telegram integration disabled in mAuth config");
            return;
        }
        String token = core.getConfigManager().getTelegramToken();
        if (token.isBlank()) {
            getLogger().warning("telegram token is empty");
            return;
        }
        bot = new TelegramBot(core, token);
        bot.start();
    }

    @Override
    public void onDisable() {
        if (bot != null) bot.close();
    }
}
