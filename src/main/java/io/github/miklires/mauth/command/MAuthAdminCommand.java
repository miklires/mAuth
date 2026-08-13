package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.importer.ImportProfile;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class MAuthAdminCommand implements CommandExecutor {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MAuth plugin;

    public MAuthAdminCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        if (args.length == 0) {
            msg.send(sender, "admin.version", MessageUtil.ph("version", plugin.getPluginMeta().getVersion()));
            msg.send(sender, "admin.reload-help");
            msg.send(sender, "admin.forcelogin-help");
            msg.send(sender, "admin.import-help");
            msg.send(sender, "admin.lock-help");
            msg.send(sender, "admin.discord-history-help");
            return true;
        }
        if (!sender.hasPermission("mauth.admin")) {
            msg.send(sender, "admin.no-permission");
            return true;
        }
        if (args[0].equalsIgnoreCase("reload")) {
            plugin.getConfigManager().reload();
            msg.reload();
            plugin.getIpRiskService().reload();
            msg.send(sender, "admin.reloaded");
            return true;
        }
        if (args[0].equalsIgnoreCase("forcelogin")) {
            forceLogin(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("import")) {
            importAccounts(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("lock") || args[0].equalsIgnoreCase("unlock")) {
            changeLock(sender, args);
            return true;
        }
        if (args[0].equalsIgnoreCase("discordhistory")) {
            discordHistory(sender, args);
            return true;
        }
        msg.send(sender, "admin.unknown-subcommand");
        return true;
    }

    private void discordHistory(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mauth.command.discordhistory")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return;
        }
        if (args.length != 2) {
            plugin.getMessageUtil().send(sender, "admin.discord-history-usage");
            return;
        }
        String username = args[1];
        plugin.submit(() -> {
            try {
                return plugin.getDiscordHistoryRepository().list(username, 20);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (entries, error) -> {
            if (error != null) {
                plugin.getMessageUtil().send(sender, "auth.database-error");
                return;
            }
            plugin.getMessageUtil().send(sender, "admin.discord-history-header",
                    MessageUtil.ph("player", username), MessageUtil.ph("count", entries.size()));
            for (var entry : entries) {
                String unlinked = entry.unlinkedAt() == null ? "active"
                        : DATE_FORMAT.format(Instant.ofEpochSecond(entry.unlinkedAt()));
                plugin.getMessageUtil().send(sender, "admin.discord-history-entry",
                        MessageUtil.ph("discord", entry.discordId()),
                        MessageUtil.ph("linked", DATE_FORMAT.format(Instant.ofEpochSecond(entry.linkedAt()))),
                        MessageUtil.ph("unlinked", unlinked));
            }
        });
    }

    private void changeLock(CommandSender sender, String[] args) {
        boolean lock = args[0].equalsIgnoreCase("lock");
        if (!sender.hasPermission("mauth.command.lock")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return;
        }
        if ((lock && args.length != 3) || (!lock && args.length != 2)) {
            plugin.getMessageUtil().send(sender, "admin.lock-usage");
            return;
        }
        int minutes = 0;
        if (lock) {
            try {
                minutes = Math.max(1, Math.min(525_600, Integer.parseInt(args[2])));
            } catch (NumberFormatException e) {
                plugin.getMessageUtil().send(sender, "admin.lock-usage");
                return;
            }
        }
        String username = args[1];
        int duration = minutes;
        plugin.submit(() -> {
            try {
                var account = plugin.getAccountRepository().findByUsername(username).orElse(null);
                if (account == null) return false;
                account.setLockedUntil(lock ? Instant.now().plusSeconds(duration * 60L) : null);
                plugin.getAccountRepository().update(account);
                plugin.getSessionRepository().invalidate(username);
                plugin.getAuditLogger().log(lock ? io.github.miklires.mauth.audit.AuditEvent.ACCOUNT_LOCKED
                                : io.github.miklires.mauth.audit.AuditEvent.ACCOUNT_UNLOCKED,
                        username, null, "actor=" + sender.getName());
                return true;
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (found, error) -> {
            if (error != null) {
                plugin.getMessageUtil().send(sender, "auth.database-error");
                return;
            }
            if (!found) {
                plugin.getMessageUtil().send(sender, "admin.reset-not-found");
                return;
            }
            plugin.getSessionManager().forgetAll(username);
            Player online = plugin.getServer().getPlayerExact(username);
            if (lock && online != null) {
                plugin.getPluginScheduler().player(online, () -> {
                    plugin.getSessionManager().clear(online);
                    online.kick(plugin.getMessageUtil().getPlain(online, "auth.account-locked",
                            MessageUtil.ph("seconds", duration * 60)));
                });
            }
            plugin.getMessageUtil().send(sender, lock ? "admin.locked" : "admin.unlocked",
                    MessageUtil.ph("player", username));
        });
    }

    private void importAccounts(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mauth.command.import")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return;
        }
        if (args.length < 2 || args.length > 3
                || args.length == 3 && !args[2].equalsIgnoreCase("--dry-run")) {
            plugin.getMessageUtil().send(sender, "admin.import-usage");
            return;
        }
        ImportProfile profile;
        try {
            profile = ImportProfile.load(plugin.getConfig().getConfigurationSection("imports"), args[1]);
        } catch (RuntimeException e) {
            plugin.getMessageUtil().send(sender, "admin.import-config-error",
                    MessageUtil.ph("error", e.getMessage()));
            return;
        }
        boolean dryRun = args.length == 3;
        plugin.getMessageUtil().send(sender, "admin.import-started");
        plugin.submit(() -> {
            try {
                return plugin.getAccountImporter().run(profile, dryRun);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (result, error) -> {
            if (error != null) {
                plugin.getLogger().warning("account import failed: " + error.getMessage());
                plugin.getMessageUtil().send(sender, "admin.import-failed");
                return;
            }
            plugin.getMessageUtil().send(sender, dryRun ? "admin.import-dry-result" : "admin.import-result",
                    MessageUtil.ph("scanned", result.scanned()),
                    MessageUtil.ph("imported", result.imported()),
                    MessageUtil.ph("skipped", result.skipped()),
                    MessageUtil.ph("invalid", result.invalid()));
        });
    }

    private void forceLogin(CommandSender sender, String[] args) {
        if (!sender.hasPermission("mauth.command.forcelogin")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return;
        }
        if (args.length != 2) {
            plugin.getMessageUtil().send(sender, "admin.forcelogin-usage");
            return;
        }
        Player player = plugin.getServer().getPlayerExact(args[1]);
        if (player == null) {
            plugin.getMessageUtil().send(sender, "admin.forcelogin-offline");
            return;
        }
        plugin.submit(player, () -> {
            try {
                return plugin.getAccountRepository().findByUsername(player.getName()).orElse(null);
            } catch (SQLException e) {
                throw new IllegalStateException(e);
            }
        }, (account, error) -> {
            if (error != null) {
                plugin.getLogger().warning("forcelogin lookup failed: " + error.getMessage());
                plugin.getMessageUtil().send(sender, "auth.database-error");
                return;
            }
            if (account == null) {
                plugin.getMessageUtil().send(sender, "admin.forcelogin-unregistered");
                return;
            }
            if (!player.isOnline()) {
                plugin.getMessageUtil().send(sender, "admin.forcelogin-offline");
                return;
            }
            plugin.getSessionManager().markAuthenticated(player,
                    io.github.miklires.mauth.api.PlayerAuthenticatedEvent.AuthReason.FORCED);
            var protectedLocation = plugin.getPlayerStateStore().restore(player);
            plugin.getLimboWorldManager().returnFromLimbo(player, protectedLocation != null
                    ? protectedLocation : plugin.getLimboWorldManager().parseLocation(account.getLastLocation()));
            String actor = sender.getName();
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.FORCE_LOGIN,
                    account.getUsername(), null, "actor=" + actor);
            plugin.getMessageUtil().send(sender, "admin.forcelogin-success",
                    MessageUtil.ph("player", player.getName()));
            plugin.getMessageUtil().send(player, "auth.force-logged-in");
        });
    }
}
