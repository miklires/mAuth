package io.github.miklires.mauth.command;

import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.session.SessionGui;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import io.papermc.paper.command.brigadier.argument.ArgumentTypes;
import io.papermc.paper.command.brigadier.argument.resolvers.selector.PlayerSelectorArgumentResolver;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.util.List;

public class BrigadierCommands {

    private final MAuth plugin;

    public BrigadierCommands(MAuth plugin) {
        this.plugin = plugin;
    }

    public void register(Commands commands, SessionGui sessions) {
        simple(commands, "register", "Register a new account", List.of("reg"),
                new RegisterCommand(plugin), args("password", "confirm"));
        simple(commands, "login", "Log in", List.of("l"), new LoginCommand(plugin), args("password"));
        simple(commands, "captcha", "Submit captcha", List.of(), new CaptchaCommand(plugin), args("code"));
        simple(commands, "logout", "Log out", List.of(), new LogoutCommand(plugin), null);
        simple(commands, "changepassword", "Change password", List.of("cp", "changepass"),
                new ChangePasswordCommand(plugin), args("old", "new"));
        simple(commands, "license", "Enable premium login", List.of("premium"),
                new LicenseCommand(plugin), null);
        simple(commands, "cracked", "Disable premium login", List.of(), new CrackedCommand(plugin), null);
        simple(commands, "sessions", "Manage sessions", List.of(), sessions, null);
        simple(commands, "telegram", "Link Telegram", List.of(), new TelegramCommand(plugin), null);
        registerTotp(commands);
        registerEmail(commands);
        registerRecover(commands);
        registerWhitelist(commands);
        registerAdminSingles(commands);
        registerAdmin(commands);
    }

    private void registerTotp(Commands commands) {
        CommandExecutor executor = new TotpCommand(plugin);
        LiteralArgumentBuilder<CommandSourceStack> root = root("2fa", "mauth.command.2fa", executor)
                .executes(ctx -> invoke(executor, "2fa", ctx));
        root.then(Commands.literal("enable").executes(ctx -> invoke(executor, "2fa", ctx, "enable")))
                .then(Commands.literal("confirm")
                        .executes(ctx -> invoke(executor, "2fa", ctx, "confirm")).then(word("code")
                        .executes(ctx -> invoke(executor, "2fa", ctx, "confirm", string(ctx, "code")))))
                .then(Commands.literal("disable")
                        .executes(ctx -> invoke(executor, "2fa", ctx, "disable")).then(word("password")
                        .executes(ctx -> invoke(executor, "2fa", ctx, "disable", string(ctx, "password")))))
                .then(Commands.literal("recovery")
                        .executes(ctx -> invoke(executor, "2fa", ctx, "recovery")).then(word("password")
                        .executes(ctx -> invoke(executor, "2fa", ctx, "recovery", string(ctx, "password")))))
                .then(word("code").executes(ctx -> invoke(executor, "2fa", ctx, string(ctx, "code"))));
        commands.register(root.build(), "Manage TOTP", List.of("totp"));
    }

    private void registerEmail(Commands commands) {
        CommandExecutor executor = new EmailCommand(plugin);
        LiteralArgumentBuilder<CommandSourceStack> root = root("email", "mauth.command.email", executor)
                .executes(ctx -> invoke(executor, "email", ctx));
        for (String action : List.of("set", "confirm", "remove")) {
            String argument = action.equals("set") ? "address" : action.equals("confirm") ? "code" : "password";
            root.then(Commands.literal(action).executes(ctx -> invoke(executor, "email", ctx, action)).then(word(argument)
                    .executes(ctx -> invoke(executor, "email", ctx, action, string(ctx, argument)))));
        }
        commands.register(root.build(), "Manage recovery email", List.of());
    }

    private void registerRecover(Commands commands) {
        CommandExecutor executor = new EmailCommand(plugin);
        LiteralArgumentBuilder<CommandSourceStack> root = root("recover", "mauth.command.recover", executor)
                .executes(ctx -> invoke(executor, "recover", ctx));
        root.then(word("player").executes(ctx -> invoke(executor, "recover", ctx, string(ctx, "player"))))
                .then(Commands.literal("confirm").executes(ctx -> invoke(executor, "recover", ctx, "confirm"))
                        .then(word("player").executes(ctx -> invoke(executor, "recover", ctx,
                                "confirm", string(ctx, "player"))).then(word("code")
                                .executes(ctx -> invoke(executor, "recover", ctx, "confirm",
                                        string(ctx, "player"), string(ctx, "code"))).then(word("password")
                        .executes(ctx -> invoke(executor, "recover", ctx, "confirm", string(ctx, "player"),
                                string(ctx, "code"), string(ctx, "password")))))));
        commands.register(root.build(), "Recover account", List.of());
    }

    private void registerWhitelist(Commands commands) {
        CommandExecutor executor = new WhitelistCommand(plugin);
        LiteralArgumentBuilder<CommandSourceStack> root = root("whitelist", null, executor)
                .executes(ctx -> invoke(executor, "whitelist", ctx));
        root.then(Commands.literal("list").requires(permission("mauth.command.whitelist.list"))
                        .executes(ctx -> invoke(executor, "whitelist", ctx, "list")))
                .then(Commands.literal("add").requires(permission("mauth.command.whitelist.add"))
                        .executes(ctx -> invoke(executor, "whitelist", ctx, "add"))
                        .then(word("player").executes(ctx -> invoke(executor, "whitelist", ctx,
                                "add", string(ctx, "player")))))
                .then(Commands.literal("remove").requires(permission("mauth.command.whitelist.remove"))
                        .executes(ctx -> invoke(executor, "whitelist", ctx, "remove"))
                        .then(word("player").executes(ctx -> invoke(executor, "whitelist", ctx,
                                "remove", string(ctx, "player")))));
        commands.register(root.build(), "Manage auth whitelist", List.of("wl", "mauthwl"));
    }

    private void registerAdminSingles(Commands commands) {
        adminPlayer(commands, "mauthreset", "mauth.command.reset", new ResetPasswordCommand(plugin));
        adminPlayer(commands, "mauthunlink", "mauth.command.unlink", new UnlinkCommand(plugin));
        adminPlayer(commands, "mauthip", "mauth.command.ip", new IpHistoryCommand(plugin));

        CommandExecutor log = new LogCommand(plugin);
        LiteralArgumentBuilder<CommandSourceStack> root = root("mauthlog", "mauth.command.log", log)
                .executes(ctx -> invoke(log, "mauthlog", ctx));
        root.then(word("player").executes(ctx -> invoke(log, "mauthlog", ctx, string(ctx, "player")))
                .then(Commands.argument("limit", IntegerArgumentType.integer(1, 100))
                        .executes(ctx -> invoke(log, "mauthlog", ctx, string(ctx, "player"),
                                String.valueOf(IntegerArgumentType.getInteger(ctx, "limit"))))));
        commands.register(root.build(), "View audit log", List.of());
    }

    private void registerAdmin(Commands commands) {
        CommandExecutor executor = new MAuthAdminCommand(plugin);
        LiteralArgumentBuilder<CommandSourceStack> root = root("mauth", null, executor)
                .executes(ctx -> invoke(executor, "mauth", ctx));
        root.then(Commands.literal("reload").requires(permission("mauth.admin"))
                        .executes(ctx -> invoke(executor, "mauth", ctx, "reload")))
                .then(Commands.literal("forcelogin").requires(permission("mauth.command.forcelogin"))
                        .executes(ctx -> invoke(executor, "mauth", ctx, "forcelogin"))
                        .then(Commands.argument("player", ArgumentTypes.player()).executes(ctx -> {
                            PlayerSelectorArgumentResolver resolver = ctx.getArgument(
                                    "player", PlayerSelectorArgumentResolver.class);
                            var players = resolver.resolve(ctx.getSource());
                            if (players.size() != 1) return 0;
                            return invoke(executor, "mauth", ctx, "forcelogin", players.getFirst().getName());
                        })))
                .then(Commands.literal("lock").requires(permission("mauth.command.lock"))
                        .executes(ctx -> invoke(executor, "mauth", ctx, "lock"))
                        .then(word("player").executes(ctx -> invoke(executor, "mauth", ctx,
                                "lock", string(ctx, "player"))).then(Commands.argument("minutes", IntegerArgumentType.integer(1, 525600))
                                .executes(ctx -> invoke(executor, "mauth", ctx, "lock", string(ctx, "player"),
                                        String.valueOf(IntegerArgumentType.getInteger(ctx, "minutes")))))))
                .then(Commands.literal("unlock").requires(permission("mauth.command.lock"))
                        .executes(ctx -> invoke(executor, "mauth", ctx, "unlock"))
                        .then(word("player").executes(ctx -> invoke(executor, "mauth", ctx,
                                "unlock", string(ctx, "player")))))
                .then(Commands.literal("discordhistory").requires(permission("mauth.command.discordhistory"))
                        .executes(ctx -> invoke(executor, "mauth", ctx, "discordhistory"))
                        .then(word("player").executes(ctx -> invoke(executor, "mauth", ctx,
                                "discordhistory", string(ctx, "player")))))
                .then(importNode(executor));
        commands.register(root.build(), "Manage mAuth", List.of());
    }

    private LiteralArgumentBuilder<CommandSourceStack> importNode(CommandExecutor executor) {
        var source = Commands.argument("source", StringArgumentType.word()).suggests((ctx, builder) -> {
            for (String value : List.of("authme", "nlogin", "librelogin", "opennlogin")) builder.suggest(value);
            return builder.buildFuture();
        });
        source.executes(ctx -> invoke(executor, "mauth", ctx, "import", string(ctx, "source")))
                .then(Commands.literal("--dry-run").executes(ctx -> invoke(executor, "mauth", ctx,
                        "import", string(ctx, "source"), "--dry-run")));
        return Commands.literal("import").requires(permission("mauth.command.import"))
                .executes(ctx -> invoke(executor, "mauth", ctx, "import")).then(source);
    }

    private void adminPlayer(Commands commands, String name, String permission, CommandExecutor executor) {
        LiteralArgumentBuilder<CommandSourceStack> root = root(name, permission, executor)
                .executes(ctx -> invoke(executor, name, ctx));
        root.then(word("player").executes(ctx -> invoke(executor, name, ctx, string(ctx, "player"))));
        commands.register(root.build(), name, List.of());
    }

    private void simple(Commands commands, String name, String description, List<String> aliases,
                        CommandExecutor executor, String[] arguments) {
        LiteralArgumentBuilder<CommandSourceStack> root = root(name, "mauth.command." + permissionName(name), executor);
        root.executes(ctx -> invoke(executor, name, ctx));
        if (arguments == null) {
        } else {
            com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> first = word(arguments[0]);
            if (arguments.length == 1) {
                first.executes(ctx -> invoke(executor, name, ctx, string(ctx, arguments[0])));
            } else {
                first.executes(ctx -> invoke(executor, name, ctx, string(ctx, arguments[0])));
                first.then(word(arguments[1]).executes(ctx -> invoke(executor, name, ctx,
                        string(ctx, arguments[0]), string(ctx, arguments[1]))));
            }
            root.then(first);
        }
        commands.register(root.build(), description, aliases);
    }

    private String permissionName(String name) {
        return name.equals("license") ? "license" : name;
    }

    private LiteralArgumentBuilder<CommandSourceStack> root(String name, String permission,
                                                             CommandExecutor executor) {
        LiteralArgumentBuilder<CommandSourceStack> root = Commands.literal(name);
        if (permission != null) root.requires(permission(permission));
        return root;
    }

    private java.util.function.Predicate<CommandSourceStack> permission(String permission) {
        return source -> source.getSender().hasPermission(permission);
    }

    private com.mojang.brigadier.builder.RequiredArgumentBuilder<CommandSourceStack, String> word(String name) {
        return Commands.argument(name, StringArgumentType.word());
    }

    private String string(CommandContext<CommandSourceStack> ctx, String name) {
        return StringArgumentType.getString(ctx, name);
    }

    private String[] args(String... values) {
        return values;
    }

    private int invoke(CommandExecutor executor, String name, CommandContext<CommandSourceStack> ctx,
                       String... args) {
        CommandSender sender = ctx.getSource().getSender();
        return executor.onCommand(sender, new BridgeCommand(name), name, args) ? 1 : 0;
    }

    private static class BridgeCommand extends Command {
        BridgeCommand(String name) { super(name); }
        @Override public boolean execute(CommandSender sender, String label, String[] args) { return false; }
    }
}
