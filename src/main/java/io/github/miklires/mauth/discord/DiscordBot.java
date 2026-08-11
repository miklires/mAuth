package io.github.miklires.mauth.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.PasswordResetService;
import io.github.miklires.mauth.model.Account;

import java.awt.Color;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Pattern;

public class DiscordBot extends ListenerAdapter {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4}$");

    private static final Color COLOR_SUCCESS = new Color(0x2ECC71);
    private static final Color COLOR_ERROR = new Color(0xE74C3C);
    private static final Color COLOR_INFO = new Color(0x3498DB);

    private final MAuth plugin;
    private JDA jda;

    public DiscordBot(MAuth plugin) {
        this.plugin = plugin;
    }

    public void start() throws Exception {
        String token = plugin.getConfigManager().getDiscordToken();
        if (token == null || token.isBlank() || token.equals("YOUR_BOT_TOKEN")) {
            plugin.getLogger().warning("no discord token in config.yml, bot not started");
            return;
        }

        jda = JDABuilder.createLight(token,
                        EnumSet.of(
                                GatewayIntent.DIRECT_MESSAGES,
                                GatewayIntent.MESSAGE_CONTENT
                        ))
                .setMemberCachePolicy(MemberCachePolicy.NONE)
                .disableCache(EnumSet.allOf(CacheFlag.class))
                .addEventListeners(this)
                .build();

        jda.awaitReady();
        plugin.getLogger().info("discord bot ready: " + jda.getSelfUser().getName());
    }

    public void shutdown() {
        if (jda != null) {
            jda.shutdown();
            try {
                if (!jda.awaitShutdown(java.time.Duration.ofSeconds(10))) {
                    jda.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
    }

    @Override
    public void onMessageReceived(@NotNull MessageReceivedEvent event) {
        if (event.getAuthor().isBot()) return;
        if (event.getChannelType() != ChannelType.PRIVATE) return;

        String raw = event.getMessage().getContentRaw().trim();
        String lower = raw.toLowerCase();
        User discordUser = event.getAuthor();

        if (lower.equals("!reset")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    handleResetRequest(discordUser, event));
            return;
        }
        if (lower.equals("!reset confirm")) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    handleResetConfirm(discordUser, event));
            return;
        }

        String code = raw.toUpperCase();
        if (CODE_PATTERN.matcher(code).matches()) {
            plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () ->
                    processCode(code, discordUser, event));
            return;
        }

        sendEmbed(event, COLOR_INFO, "Привет!",
                "Доступные действия:\n\n"
                        + "• Отправь **4-символьный код** из игры для привязки аккаунта\n"
                        + "• `!reset` — сбросить пароль (если забыл)");
    }

    private void handleResetRequest(User discordUser, MessageReceivedEvent event) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByDiscordId(discordUser.getId());
            if (opt.isEmpty()) {
                sendEmbed(event, COLOR_ERROR, "Аккаунт не найден",
                        "К этому Discord не привязан ни один игровой аккаунт.");
                return;
            }
            Account a = opt.get();
            if (isPlayerOnline(a.getUsername())) {
                kickPlayerForReset(a.getUsername());
            }
            sendEmbed(event, COLOR_INFO, "Подтверждение сброса",
                    "Точно сбросить пароль для **" + a.getUsername() + "**?\n\n"
                            + "Отправь `!reset confirm` в течение минуты чтобы подтвердить.\n"
                            + "Лимит: 1 сброс в сутки.");
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on reset request: " + e.getMessage());
            sendEmbed(event, COLOR_ERROR, "Ошибка сервера",
                    "Произошла внутренняя ошибка. Попробуй позже.");
        }
    }

    private void handleResetConfirm(User discordUser, MessageReceivedEvent event) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByDiscordId(discordUser.getId());
            if (opt.isPresent() && isPlayerOnline(opt.get().getUsername())) {
                kickPlayerForReset(opt.get().getUsername());
                try { Thread.sleep(500); } catch (InterruptedException ignored) {}
            }
        } catch (SQLException ignored) {
        }

        PasswordResetService.ResetResult result = plugin.getPasswordResetService()
                .resetByDiscordId(discordUser.getId());

        switch (result.status) {
            case SUCCESS -> sendEmbed(event, COLOR_SUCCESS, "Пароль сброшен",
                    "Аккаунт: **" + result.username + "**\n"
                            + "Новый пароль: `" + result.newPassword + "`\n\n"
                            + "Зайди на сервер и введи `/login " + result.newPassword + "`.\n"
                            + "**Рекомендую сразу сменить пароль через `/changepassword`.**");
            case NOT_FOUND -> sendEmbed(event, COLOR_ERROR, "Аккаунт не найден",
                    "К этому Discord не привязан игровой аккаунт.");
            case PLAYER_ONLINE -> sendEmbed(event, COLOR_ERROR, "Невозможно сбросить",
                    "Игрок сейчас онлайн. Выйди из игры и повторяй.");
            case RATE_LIMITED -> sendEmbed(event, COLOR_ERROR, "Лимит",
                    "Пароль уже был сброшен. Подожди ещё " + formatDuration(result.retryAfterSeconds)
                            + " прежде чем сбрасывать снова.");
            case DB_ERROR -> sendEmbed(event, COLOR_ERROR, "Ошибка сервера",
                    "Произошла внутренняя ошибка. Попробуй позже.");
        }
    }

    private boolean isPlayerOnline(String username) {
        return plugin.getServer().getOnlinePlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(username));
    }

    private void kickPlayerForReset(String username) {
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(username))
                    .findFirst()
                    .ifPresent(p -> p.kick(net.kyori.adventure.text.minimessage.MiniMessage
                            .miniMessage()
                            .deserialize("<yellow>Идёт сброс пароля через Discord. Зайди заново.")));
        });
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return hours + " ч " + minutes + " мин";
        return minutes + " мин";
    }

    private void processCode(String code, User discordUser, MessageReceivedEvent event) {
        String discordId = discordUser.getId();

        try {
            Optional<Account> existingByDiscord = plugin.getAccountRepository().findByDiscordId(discordId);
            if (existingByDiscord.isPresent()) {
                String linkedTo = existingByDiscord.get().getUsername();
                sendEmbed(event, COLOR_ERROR, "Discord уже привязан",
                        "Этот Discord уже привязан к аккаунту **" + linkedTo + "**.\n"
                                + "Один Discord = один игровой аккаунт.");
                return;
            }

            Optional<String> usernameOpt = plugin.getLinkCodeManager().peekCode(code);
            if (usernameOpt.isEmpty()) {
                sendEmbed(event, COLOR_ERROR, "Неверный код",
                        "Неверный или истёкший код. Зайди на сервер и сгенерируй новый.");
                return;
            }

            String username = usernameOpt.get();
            Optional<Account> accountOpt = plugin.getAccountRepository().findByUsername(username);
            if (accountOpt.isEmpty()) {
                sendEmbed(event, COLOR_ERROR, "Неверный код",
                        "Аккаунт не найден. Попробуй сгенерировать новый код.");
                return;
            }

            Account account = accountOpt.get();
            if (account.hasDiscordLinked()) {
                sendEmbed(event, COLOR_ERROR, "Аккаунт уже привязан",
                        "К аккаунту **" + username + "** уже привязан другой Discord.\n"
                                + "Если потерял доступ — обратись к администратору.");
                return;
            }

            account.setDiscordId(discordId);
            plugin.getAccountRepository().update(account);
            plugin.getLinkCodeManager().consumeCode(code);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.DISCORD_LINKED,
                    username, null, "discord_id=" + discordId);

            assignPlayerRole(discordId, username);

            sendEmbed(event, COLOR_SUCCESS, "Аккаунт привязан",
                    "Discord успешно привязан к **" + username + "**.\n"
                            + "Возвращайтесь на сервер — повторный вход не потребуется.");

            plugin.getLogger().info("Discord " + discordUser.getName()
                    + " (" + discordId + ") linked to " + username);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on discord link: " + e.getMessage());
            sendEmbed(event, COLOR_ERROR, "Ошибка сервера",
                    "Произошла внутренняя ошибка. Попробуйте позже.");
        }
    }

    private void assignPlayerRole(String discordId, String username) {
        String guildId = plugin.getConfigManager().getDiscordGuildId();
        String roleId = plugin.getConfigManager().getDiscordPlayerRoleId();
        if (guildId == null || guildId.isBlank() || roleId == null || roleId.isBlank()) return;
        if (jda == null) return;
        try {
            net.dv8tion.jda.api.entities.Guild guild = jda.getGuildById(guildId);
            if (guild == null) {
                plugin.getLogger().warning("guild " + guildId + " not found, cannot grant role");
                return;
            }
            net.dv8tion.jda.api.entities.Role role = guild.getRoleById(roleId);
            if (role == null) {
                plugin.getLogger().warning("role " + roleId + " not found in guild");
                return;
            }
            guild.retrieveMemberById(discordId).queue(member -> {
                if (member == null) return;
                guild.addRoleToMember(member, role).queue(
                        v -> plugin.getLogger().info("role '" + role.getName()
                                + "' granted to " + member.getUser().getName() + " (" + username + ")"),
                        err -> plugin.getLogger().warning("role grant failed: " + err.getMessage())
                );
            }, err -> plugin.getLogger().warning("member " + discordId + " not found: " + err.getMessage()));
        } catch (Exception e) {
            plugin.getLogger().warning("discord role grant failed: " + e.getMessage());
        }
    }

    private void sendEmbed(MessageReceivedEvent event, Color color, String title, String description) {
        MessageEmbed embed = new EmbedBuilder()
                .setColor(color)
                .setTitle(title)
                .setDescription(description)
                .build();
        event.getMessage().replyEmbeds(embed).queue();
    }

    public boolean isReady() {
        return jda != null && jda.getStatus() == JDA.Status.CONNECTED;
    }

    public void notifyNewCountry(String discordId, String username, String ip,
                                  io.github.miklires.mauth.geoip.GeoIpResolver.GeoInfo info) {
        if (jda == null) return;
        jda.retrieveUserById(discordId).queue(user -> {
            if (user == null) return;
            user.openPrivateChannel().queue(channel -> {
                net.dv8tion.jda.api.entities.MessageEmbed embed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setColor(new java.awt.Color(0xF39C12))
                        .setTitle("⚠️ Заход с нового региона")
                        .setDescription("Аккаунт **" + username + "** только что зашёл с нового региона:\n\n"
                                + "🌍 Страна: **" + info.name() + "** (`" + info.code() + "`)\n"
                                + "📡 IP: `" + ip + "`\n\n"
                                + "Если это ты — игнорируй это сообщение.\n"
                                + "Если **не ты** — срочно сбрось пароль командой `!reset`.")
                        .build();
                channel.sendMessageEmbeds(embed).queue(
                        success -> {},
                        error -> plugin.getLogger().warning(
                                "cannot send dm to " + discordId + ": " + error.getMessage())
                );
            }, error -> plugin.getLogger().warning(
                    "cannot open dm with " + discordId + ": " + error.getMessage()));
        }, error -> plugin.getLogger().warning(
                "discord user " + discordId + " not found: " + error.getMessage()));
    }
}
