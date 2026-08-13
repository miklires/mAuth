package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

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
        MessageUtil msg = plugin.getMessageUtil();
        if (!player.hasPermission("mauth.command.license")) {
            msg.send(player, "admin.no-permission");
            return true;
        }
        if (!plugin.getSessionManager().isAuthenticated(player)) {
            msg.send(player, "auth.not-logged-in");
            return true;
        }

        String username = player.getName();
        UUID current = player.getUniqueId();
        plugin.submit(player, () -> enable(username, current), (result, error) -> {
            if (!player.isOnline()) return;
            if (error != null) {
                plugin.getLogger().severe("license task failed: " + error.getMessage());
                msg.send(player, "auth.database-error");
                return;
            }
            switch (result) {
                case ENABLED -> msg.send(player, "license.enabled");
                case ALREADY_ENABLED -> msg.send(player, "license.already-premium");
                case NOT_PREMIUM -> msg.send(player, "license.not-premium-uuid");
                case RATE_LIMITED -> msg.send(player, "license.rate-limited");
                case UNAVAILABLE -> msg.send(player, "license.unavailable");
                case NOT_REGISTERED -> msg.send(player, "auth.please-register");
            }
        });
        return true;
    }

    private Result enable(String username, UUID current) {
        var lookup = plugin.getPremiumProfileService().lookup(username);
        if (lookup.status() == io.github.miklires.mauth.auth.PremiumProfileService.Status.NOT_FOUND) {
            return Result.NOT_PREMIUM;
        }
        if (lookup.status() == io.github.miklires.mauth.auth.PremiumProfileService.Status.RATE_LIMITED) {
            return Result.RATE_LIMITED;
        }
        if (lookup.status() == io.github.miklires.mauth.auth.PremiumProfileService.Status.UNAVAILABLE) {
            return Result.UNAVAILABLE;
        }
        UUID online = lookup.uuid();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty()) return Result.NOT_REGISTERED;
            Account a = opt.get();
            if (a.isPremiumEnabled() && online.equals(a.getPremiumUuid())) return Result.ALREADY_ENABLED;
            a.setPremiumUuid(online);
            a.setPremiumEnabled(true);
            plugin.getAccountRepository().update(a);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.LICENSE_ENABLED,
                    username, null, "uuid=" + online + ",requested_from=" + current);
            return Result.ENABLED;
        } catch (SQLException e) {
            throw new IllegalStateException("db error on license", e);
        }
    }

    private enum Result {
        ENABLED, ALREADY_ENABLED, NOT_PREMIUM, RATE_LIMITED, UNAVAILABLE, NOT_REGISTERED
    }
}
