package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public class WhitelistCommand implements CommandExecutor, TabCompleter {

    private final MAuth plugin;
    private volatile List<String> names = List.of();

    public WhitelistCommand(MAuth plugin) {
        this.plugin = plugin;
        refresh();
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        MessageUtil msg = plugin.getMessageUtil();
        if (args.length == 0) {
            msg.send(sender, "whitelist.usage");
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "add" -> change(sender, args, true);
            case "remove", "rm" -> change(sender, args, false);
            case "list" -> list(sender);
            default -> msg.send(sender, "whitelist.usage");
        }
        return true;
    }

    private void change(CommandSender sender, String[] args, boolean add) {
        MessageUtil msg = plugin.getMessageUtil();
        String permission = add ? "mauth.command.whitelist.add" : "mauth.command.whitelist.remove";
        if (!sender.hasPermission(permission)) {
            msg.send(sender, "admin.no-permission");
            return;
        }
        if (args.length < 2) {
            msg.send(sender, "whitelist.usage");
            return;
        }

        String target = args[1].toLowerCase();
        String actor = sender.getName();
        plugin.submit(() -> update(target, actor, add), (result, error) -> {
            if (error != null) {
                plugin.getLogger().severe("whitelist update failed: " + error.getMessage());
                msg.send(sender, "auth.database-error");
                return;
            }
            switch (result) {
                case NOT_FOUND -> msg.send(sender, add ? "whitelist.not-registered" : "whitelist.not-found");
                case ALREADY_SET -> msg.send(sender, "whitelist.already-whitelisted");
                case CHANGED -> {
                    msg.send(sender, add ? "whitelist.added" : "whitelist.removed",
                            MessageUtil.ph("player", target));
                    refresh();
                }
            }
        });
    }

    private ChangeResult update(String target, String actor, boolean add) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(target);
            if (opt.isEmpty()) return ChangeResult.NOT_FOUND;
            Account a = opt.get();
            if (add && a.isWhitelisted()) return ChangeResult.ALREADY_SET;
            a.setWhitelisted(add);
            plugin.getAccountRepository().update(a);
            plugin.getAuditLogger().log(add ? AuditEvent.WHITELIST_ADDED : AuditEvent.WHITELIST_REMOVED,
                    target, null, "by=" + actor);
            return ChangeResult.CHANGED;
        } catch (SQLException e) {
            throw new IllegalStateException(e);
        }
    }

    private void list(CommandSender sender) {
        MessageUtil msg = plugin.getMessageUtil();
        if (!sender.hasPermission("mauth.command.whitelist.list")) {
            msg.send(sender, "admin.no-permission");
            return;
        }
        plugin.submit(this::loadNames, (list, error) -> {
            if (error != null) {
                plugin.getLogger().severe("whitelist list failed: " + error.getMessage());
                msg.send(sender, "auth.database-error");
                return;
            }
            names = List.copyOf(list);
            msg.send(sender, "whitelist.list-header", MessageUtil.ph("count", list.size()));
            for (String name : list) {
                msg.send(sender, "whitelist.list-entry", MessageUtil.ph("player", name));
            }
        });
    }

    private void refresh() {
        plugin.submit(this::loadNames, (list, error) -> {
            if (error == null) names = List.copyOf(list);
            else plugin.getLogger().warning("whitelist cache refresh failed: " + error.getMessage());
        });
    }

    private List<String> loadNames() {
        try {
            return plugin.getAccountRepository().listWhitelisted();
        } catch (SQLException e) {
            throw new IllegalStateException(e);
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
        if (args.length == 1) return filter(Arrays.asList("add", "remove", "list"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("rm"))) {
            if (!sender.hasPermission("mauth.command.whitelist.remove")) return Collections.emptyList();
            return filter(names, args[1]);
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

    private enum ChangeResult {
        CHANGED, NOT_FOUND, ALREADY_SET
    }
}
