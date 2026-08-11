package io.github.miklires.mauth.command;

import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditLogger;
import io.github.miklires.mauth.util.MessageUtil;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class LogCommand implements CommandExecutor {

    private static final DateTimeFormatter FMT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MAuth plugin;

    public LogCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mauth.command.log")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§e/mauthlog <игрок> [лимит]");
            return true;
        }

        String username = args[0];
        int limit = 20;
        if (args.length >= 2) {
            try {
                limit = Math.min(100, Math.max(1, Integer.parseInt(args[1])));
            } catch (NumberFormatException ignored) {
            }
        }

        try {
            List<AuditLogger.LogEntry> entries = plugin.getAuditLogger().getRecent(username, limit);
            if (entries.isEmpty()) {
                sender.sendMessage("§eДля " + username + " записей не найдено.");
                return true;
            }
            sender.sendMessage("§6История " + username + " (последние " + entries.size() + "):");
            for (AuditLogger.LogEntry e : entries) {
                String ts = FMT.format(Instant.ofEpochSecond(e.ts()));
                StringBuilder line = new StringBuilder("§7[")
                        .append(ts).append("] §f").append(e.event());
                if (e.ip() != null) line.append(" §7ip=").append(e.ip());
                if (e.details() != null) line.append(" §8").append(e.details());
                sender.sendMessage(line.toString());
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on mauthlog: " + e.getMessage());
            sender.sendMessage("§cОшибка БД.");
        }
        return true;
    }
}
