package io.github.miklires.mauth.command;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.PasswordValidator;
import io.github.miklires.mauth.email.EmailRecoveryService;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Optional;

public class EmailCommand implements TabExecutor {

    private final MAuth plugin;

    public EmailCommand(MAuth plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("recover")) {
            if (!sender.hasPermission("mauth.command.recover")) {
                plugin.getMessageUtil().send(sender, "admin.no-permission");
                return true;
            }
            return recover(sender, args);
        }
        if (!sender.hasPermission("mauth.command.email")) {
            plugin.getMessageUtil().send(sender, "admin.no-permission");
            return true;
        }
        if (!(sender instanceof Player player)) {
            plugin.getMessageUtil().send(sender, "email.player-only");
            return true;
        }
        if (!plugin.getSessionManager().isAuthenticated(player)) {
            plugin.getMessageUtil().send(player, "auth.not-logged-in");
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("set")) {
            load(player, a -> plugin.getEmailRecoveryService().requestVerification(a, args[1]));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("confirm")) {
            load(player, a -> plugin.getEmailRecoveryService().confirmVerification(a, args[1]));
            return true;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String password = args[1];
            plugin.submit(player, () -> {
                try {
                    Optional<Account> opt = plugin.getAccountRepository().findByUsername(player.getName());
                    if (opt.isEmpty()) return EmailRecoveryService.Status.DB_ERROR;
                    Account account = opt.get();
                    if (!plugin.getPasswordHasher().verify(password, account.getPasswordHash())) {
                        return EmailRecoveryService.Status.INVALID_PASSWORD;
                    }
                    return plugin.getEmailRecoveryService().remove(account);
                } catch (Exception e) {
                    plugin.getLogger().severe("email removal failed: " + e.getMessage());
                    return EmailRecoveryService.Status.DB_ERROR;
                }
            }, (status, error) -> sendStatus(player, status));
            return true;
        }
        plugin.getMessageUtil().send(player, "email.usage");
        return true;
    }

    private boolean recover(CommandSender sender, String[] args) {
        if (args.length == 1) {
            plugin.submit(() -> plugin.getEmailRecoveryService().requestRecovery(args[0]),
                    (status, error) -> plugin.getMessageUtil().send(sender,
                            status == EmailRecoveryService.Status.DISABLED
                                    ? "email.disabled" : "email.recovery-requested"));
            return true;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("confirm")) {
            String username = args[1];
            plugin.submit(() -> plugin.getEmailRecoveryService()
                            .confirmRecovery(username, args[2], args[3]),
                    (result, error) -> finishRecovery(sender, username, result));
            return true;
        }
        plugin.getMessageUtil().send(sender, "email.recovery-usage");
        return true;
    }

    private void load(Player player, java.util.function.Function<Account, EmailRecoveryService.Status> task) {
        plugin.submit(player, () -> {
            try {
                Optional<Account> opt = plugin.getAccountRepository().findByUsername(player.getName());
                return opt.map(task).orElse(EmailRecoveryService.Status.DB_ERROR);
            } catch (Exception e) {
                plugin.getLogger().severe("email task failed: " + e.getMessage());
                return EmailRecoveryService.Status.DB_ERROR;
            }
        }, (status, error) -> sendStatus(player, status));
    }

    private void finishRecovery(CommandSender sender, String username,
                                EmailRecoveryService.ResetResult result) {
        if (result == null) {
            plugin.getMessageUtil().send(sender, "auth.database-error");
            return;
        }
        if (result.status() == EmailRecoveryService.Status.RESET) {
            plugin.getMessageUtil().send(sender, "email.recovery-complete");
            Player target = plugin.getServer().getPlayerExact(username);
            if (target != null) {
                plugin.getPluginScheduler().player(target, () -> {
                    plugin.getSessionManager().clear(target);
                    target.kick(plugin.getMessageUtil().getPlain(target, "email.recovery-kick"));
                });
            }
            return;
        }
        if (result.status() == EmailRecoveryService.Status.INVALID_PASSWORD) {
            passwordError(sender, result.passwordResult());
            return;
        }
        sendStatus(sender, result.status());
    }

    private void sendStatus(CommandSender sender, EmailRecoveryService.Status status) {
        if (status == null) {
            plugin.getMessageUtil().send(sender, "auth.database-error");
            return;
        }
        String path = switch (status) {
            case SENT -> "email.verification-sent";
            case CONFIRMED -> "email.verified";
            case REMOVED -> "email.removed";
            case DISABLED -> "email.disabled";
            case INVALID_ADDRESS -> "email.invalid-address";
            case INVALID_CODE -> "email.invalid-code";
            case INVALID_PASSWORD -> "auth.wrong-password";
            case EXPIRED -> "email.expired";
            case RATE_LIMITED -> "email.rate-limited";
            case SEND_FAILED -> "email.send-failed";
            default -> "auth.database-error";
        };
        plugin.getMessageUtil().send(sender, path);
    }

    private void passwordError(CommandSender sender, PasswordValidator.Result result) {
        String path = switch (result) {
            case TOO_SHORT -> "auth.password-too-short";
            case TOO_LONG -> "auth.password-too-long";
            case TOO_WEAK, TOO_SIMPLE -> "auth.password-too-weak";
            default -> "auth.database-error";
        };
        plugin.getMessageUtil().send(sender, path,
                MessageUtil.ph("count", result == PasswordValidator.Result.TOO_LONG
                        ? plugin.getConfigManager().getMaxPasswordLength()
                        : plugin.getConfigManager().getMinPasswordLength()));
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("email") && args.length == 1) {
            return List.of("set", "confirm", "remove").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase())).toList();
        }
        if (command.getName().equalsIgnoreCase("recover") && args.length == 1) {
            return "confirm".startsWith(args[0].toLowerCase()) ? List.of("confirm") : List.of();
        }
        return List.of();
    }
}
