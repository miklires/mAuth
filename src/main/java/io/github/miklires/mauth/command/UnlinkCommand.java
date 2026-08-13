package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.util.Optional;

public class UnlinkCommand implements CommandExecutor {

    private final MAuth plugin;

    public UnlinkCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        if (!(sender instanceof ConsoleCommandSender)) {
            msg.send(sender, "admin.reset-console-only");
            return true;
        }
        if (!sender.hasPermission("mauth.command.unlink")) {
            msg.send(sender, "admin.no-permission");
            return true;
        }
        if (args.length < 1) {
            msg.send(sender, "admin.unlink-usage");
            return true;
        }

        String target = args[0].toLowerCase();
        plugin.submit(() -> unlink(target), (result, error) -> {
            if (error != null) {
                plugin.getLogger().severe("db error on unlink: " + error.getMessage());
                msg.send(sender, "auth.database-error");
                return;
            }
            if (result == null) {
                msg.send(sender, "admin.reset-not-found");
            } else if (result.isEmpty()) {
                msg.send(sender, "admin.unlink-empty");
            } else {
                msg.send(sender, "admin.unlink-success",
                        MessageUtil.ph("discord", result), MessageUtil.ph("player", target));
            }
        });
        return true;
    }

    private String unlink(String target) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(target);
            if (opt.isEmpty()) return null;
            Account a = opt.get();
            if (!a.hasDiscordLinked()) return "";
            String old = a.getDiscordId();
            a.setDiscordId(null);
            plugin.getAccountRepository().update(a);
            plugin.getDiscordHistoryRepository().unlinked(target, old);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.DISCORD_UNLINKED,
                    target, null, "old_discord_id=" + old);
            return old;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
