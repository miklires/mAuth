package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.database.KnownIpRepository;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;

import java.sql.SQLException;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;

public class IpHistoryCommand implements CommandExecutor {

    private static final DateTimeFormatter FORMAT = DateTimeFormatter
            .ofPattern("yyyy-MM-dd HH:mm:ss")
            .withZone(ZoneId.systemDefault());

    private final MAuth plugin;

    public IpHistoryCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        if (!sender.hasPermission("mauth.command.ip")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return true;
        }
        if (args.length != 1) {
            plugin.getMessageUtil().send(sender, "admin.ip-usage");
            return true;
        }

        String username = args[0];
        plugin.submit(() -> load(username), (entries, error) -> {
            if (error != null) {
                plugin.getLogger().warning("cannot read ip history: " + error.getMessage());
                plugin.getMessageUtil().send(sender, "auth.database-error");
                return;
            }
            if (entries.isEmpty()) {
                plugin.getMessageUtil().send(sender, "admin.ip-empty",
                        MessageUtil.ph("player", username));
                return;
            }
            plugin.getMessageUtil().send(sender, "admin.ip-header",
                    MessageUtil.ph("player", username), MessageUtil.ph("count", entries.size()));
            for (KnownIpRepository.KnownIp entry : entries) {
                String country = entry.countryCode() == null ? "?" : entry.countryCode();
                plugin.getMessageUtil().send(sender, "admin.ip-entry",
                        MessageUtil.ph("ip", entry.ip()),
                        MessageUtil.ph("country", country),
                        MessageUtil.ph("last_seen", FORMAT.format(Instant.ofEpochSecond(entry.lastSeen()))));
            }
        });
        return true;
    }

    private List<KnownIpRepository.KnownIp> load(String username) {
        try {
            return plugin.getKnownIpRepository().list(username, 100);
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }
}
