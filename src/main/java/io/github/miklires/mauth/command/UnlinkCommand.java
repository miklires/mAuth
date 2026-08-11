package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;

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
            sender.sendMessage("§e/mauthunlink <игрок>");
            return true;
        }

        String target = args[0].toLowerCase();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(target);
            if (opt.isEmpty()) {
                msg.send(sender, "admin.reset-not-found");
                return true;
            }
            Account a = opt.get();
            if (!a.hasDiscordLinked()) {
                sender.sendMessage("§eУ этого игрока нет привязки Discord.");
                return true;
            }
            String oldDiscord = a.getDiscordId();
            a.setDiscordId(null);
            plugin.getAccountRepository().update(a);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.DISCORD_UNLINKED,
                    target, null, "old_discord_id=" + oldDiscord);
            sender.sendMessage("§aDiscord (" + oldDiscord + ") отвязан от " + target + ".");
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on unlink: " + e.getMessage());
            sender.sendMessage("§cОшибка БД.");
        }
        return true;
    }
}
