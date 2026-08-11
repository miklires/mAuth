package io.github.miklires.mauth.command;

import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;

public class MAuthAdminCommand implements CommandExecutor {

    private final MAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public MAuthAdminCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command,
                             @NotNull String label, @NotNull String[] args) {
        String prefix = "<gradient:#7CB342:#558B2F><b>mAuth</b></gradient> <dark_gray>»</dark_gray> ";

        if (args.length == 0) {
            sender.sendMessage(mm.deserialize(prefix
                    + "<gray>Версия: <white>" + plugin.getDescription().getVersion()));
            sender.sendMessage(mm.deserialize(prefix
                    + "<yellow>/mauth reload</yellow> <gray>— перезагрузить конфиги"));
            return true;
        }

        if (!sender.hasPermission("mauth.admin")) {
            sender.sendMessage(mm.deserialize(prefix + "<red>Нет прав."));
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            plugin.getMessageUtil().reload();
            sender.sendMessage(mm.deserialize(prefix + "<color:#7CB342>Конфиги перезагружены."));
            return true;
        }

        sender.sendMessage(mm.deserialize(prefix + "<yellow>Неизвестная подкоманда. Используйте <white>/mauth reload"));
        return true;
    }
}
