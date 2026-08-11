package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;

public class LicenseCommand implements CommandExecutor {

    private final MAuth plugin;

    public LicenseCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!(sender instanceof Player player)) return true;
        if (!player.hasPermission("mauth.command.license")) {
            plugin.getMessageUtil().send(player, "admin.no-permission");
            return true;
        }
        MessageUtil msg = plugin.getMessageUtil();

        if (!plugin.getSessionManager().isAuthenticated(player)) {
            msg.send(player, "auth.not-logged-in");
            return true;
        }

        UUID online = getOnlineUuid(player.getName());
        UUID current = player.getUniqueId();
        if (online == null || online.equals(current)) {
            msg.send(player, "license.not-premium-uuid");
            return true;
        }

        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(player.getName());
            if (opt.isEmpty()) {
                msg.send(player, "auth.please-register");
                return true;
            }
            Account a = opt.get();
            if (a.isPremiumEnabled() && current.equals(a.getPremiumUuid())) {
                msg.send(player, "license.already-premium");
                return true;
            }
            a.setPremiumUuid(current);
            a.setPremiumEnabled(true);
            plugin.getAccountRepository().update(a);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.LICENSE_ENABLED,
                    player.getName(), null, "uuid=" + current);
            msg.send(player, "license.enabled");
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on license: " + e.getMessage());
            player.sendMessage("§cОшибка БД.");
        }
        return true;
    }

    private UUID getOnlineUuid(String name) {
        try {
            java.net.URI uri = java.net.URI.create(
                    "https://api.mojang.com/users/profiles/minecraft/" + name);
            java.net.http.HttpClient client = java.net.http.HttpClient.newHttpClient();
            java.net.http.HttpRequest req = java.net.http.HttpRequest.newBuilder()
                    .uri(uri)
                    .timeout(java.time.Duration.ofSeconds(5))
                    .GET()
                    .build();
            java.net.http.HttpResponse<String> resp = client.send(req,
                    java.net.http.HttpResponse.BodyHandlers.ofString());
            if (resp.statusCode() != 200) return null;
            String body = resp.body();
            int idx = body.indexOf("\"id\"");
            if (idx < 0) return null;
            int start = body.indexOf("\"", idx + 5) + 1;
            int end = body.indexOf("\"", start);
            String raw = body.substring(start, end);
            String formatted = raw.replaceFirst(
                    "(\\w{8})(\\w{4})(\\w{4})(\\w{4})(\\w{12})", "$1-$2-$3-$4-$5");
            return UUID.fromString(formatted);
        } catch (Exception e) {
            return null;
        }
    }
}
