package io.github.miklires.mauth.command;

import net.kyori.adventure.text.Component;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class WhitelistCommand implements CommandExecutor, TabCompleter {

    private final MAuth plugin;

    public WhitelistCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();

        if (args.length == 0) {
            msg.send(sender, "whitelist.usage");
            return true;
        }

        String sub = args[0].toLowerCase();
        switch (sub) {
            case "add" -> {
                if (!sender.hasPermission("mauth.command.whitelist.add")) {
                    msg.send(sender, "admin.no-permission");
                    return true;
                }
                handleAdd(sender, args);
            }
            case "remove", "rm" -> {
                if (!sender.hasPermission("mauth.command.whitelist.remove")) {
                    msg.send(sender, "admin.no-permission");
                    return true;
                }
                handleRemove(sender, args);
            }
            case "list" -> {
                if (!sender.hasPermission("mauth.command.whitelist.list")) {
                    msg.send(sender, "admin.no-permission");
                    return true;
                }
                handleList(sender);
            }
            default -> msg.send(sender, "whitelist.usage");
        }
        return true;
    }

    private void handleAdd(CommandSender sender, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        if (args.length < 2) {
            msg.send(sender, "whitelist.usage");
            return;
        }
        String target = args[1].toLowerCase();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(target);
            if (opt.isEmpty()) {
                msg.send(sender, "whitelist.not-registered");
                return;
            }
            Account a = opt.get();
            if (a.isWhitelisted()) {
                msg.send(sender, "whitelist.already-whitelisted");
                return;
            }
            a.setWhitelisted(true);
            plugin.getAccountRepository().update(a);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.WHITELIST_ADDED,
                    target, null, "by=" + sender.getName());
            msg.send(sender, "whitelist.added", MessageUtil.ph("player", target));
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on whitelist add: " + e.getMessage());
            sender.sendMessage(Component.text("§cОшибка БД."));
        }
    }

    private void handleRemove(CommandSender sender, String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        if (args.length < 2) {
            msg.send(sender, "whitelist.usage");
            return;
        }
        String target = args[1].toLowerCase();
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(target);
            if (opt.isEmpty()) {
                msg.send(sender, "whitelist.not-found");
                return;
            }
            Account a = opt.get();
            a.setWhitelisted(false);
            plugin.getAccountRepository().update(a);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.WHITELIST_REMOVED,
                    target, null, "by=" + sender.getName());
            msg.send(sender, "whitelist.removed", MessageUtil.ph("player", target));
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on whitelist remove: " + e.getMessage());
            sender.sendMessage(Component.text("§cОшибка БД."));
        }
    }

    private void handleList(CommandSender sender) {
        MessageUtil msg = plugin.getMessageUtil();
        try {
            List<String> list = plugin.getAccountRepository().listWhitelisted();
            msg.send(sender, "whitelist.list-header", MessageUtil.ph("count", list.size()));
            for (String name : list) {
                msg.send(sender, "whitelist.list-entry", MessageUtil.ph("player", name));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on whitelist list: " + e.getMessage());
            sender.sendMessage(Component.text("§cОшибка БД."));
        }
    }

    @Nullable
    @Override
    public List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command,
                                      @NotNull String alias, @NotNull String[] args) {
        boolean canAny = sender.hasPermission("mauth.command.whitelist.add")
                || sender.hasPermission("mauth.command.whitelist.remove")
                || sender.hasPermission("mauth.command.whitelist.list");
        if (!canAny) return Collections.emptyList();
        if (args.length == 1) {
            return filter(Arrays.asList("add", "remove", "list"), args[0]);
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("rm"))) {
            if (!sender.hasPermission("mauth.command.whitelist.remove")) return Collections.emptyList();
            try {
                return filter(plugin.getAccountRepository().listWhitelisted(), args[1]);
            } catch (SQLException e) {
                return Collections.emptyList();
            }
        }
        return Collections.emptyList();
    }

    private List<String> filter(List<String> source, String prefix) {
        String p = prefix.toLowerCase();
        List<String> out = new ArrayList<>();
        for (String s : source) {
            if (s.toLowerCase().startsWith(p)) out.add(s);
        }
        return out;
    }
}
