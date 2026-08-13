package io.github.miklires.mauth.discord;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.JDABuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.User;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.events.message.MessageReceivedEvent;
import net.dv8tion.jda.api.events.session.ReadyEvent;
import net.dv8tion.jda.api.hooks.ListenerAdapter;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.utils.MemberCachePolicy;
import net.dv8tion.jda.api.utils.cache.CacheFlag;
import org.jetbrains.annotations.NotNull;
import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.auth.PasswordResetService;
import io.github.miklires.mauth.api.DiscordIntegration;
import io.github.miklires.mauth.model.Account;

import java.awt.Color;
import java.sql.SQLException;
import java.util.EnumSet;
import java.util.Optional;
import java.util.regex.Pattern;

public class DiscordBot extends ListenerAdapter implements DiscordIntegration {

    private static final Pattern CODE_PATTERN = Pattern.compile("^[A-Z0-9]{4}$");

    private static final Color COLOR_SUCCESS = new Color(0x2ECC71);
    private static final Color COLOR_ERROR = new Color(0xE74C3C);
    private static final Color COLOR_INFO = new Color(0x3498DB);

    private final MAuth plugin;
    private final DiscordMessages messages;
    private JDA jda;

    public DiscordBot(MAuth plugin, DiscordMessages messages) {
        this.plugin = plugin;
        this.messages = messages;
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

        plugin.getLogger().info("discord bot connecting");
    }

    @Override
    public void onReady(@NotNull ReadyEvent event) {
        plugin.getLogger().info("discord bot ready: " + event.getJDA().getSelfUser().getName());
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
            plugin.getPluginScheduler().async(() ->
                    handleResetRequest(discordUser, event));
            return;
        }
        if (lower.equals("!reset confirm")) {
            plugin.getPluginScheduler().async(() ->
                    handleResetConfirm(discordUser, event));
            return;
        }

        String code = raw.toUpperCase();
        if (CODE_PATTERN.matcher(code).matches()) {
            plugin.getPluginScheduler().async(() ->
                    processCode(code, discordUser, event));
            return;
        }

        sendEmbed(event, COLOR_INFO, messages.get("help.title"), messages.get("help.body"));
    }

    private void handleResetRequest(User discordUser, MessageReceivedEvent event) {
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByDiscordId(discordUser.getId());
            if (opt.isEmpty()) {
                sendEmbed(event, COLOR_ERROR, messages.get("account-not-found.title"),
                        messages.get("account-not-found.body"));
                return;
            }
            Account a = opt.get();
            if (isPlayerOnline(a.getUsername())) {
                kickPlayerForReset(a.getUsername());
            }
            sendEmbed(event, COLOR_INFO, messages.get("reset.confirm-title"),
                    messages.get("reset.confirm-body", "username", a.getUsername()));
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on reset request: " + e.getMessage());
            sendEmbed(event, COLOR_ERROR, messages.get("server-error.title"),
                    messages.get("server-error.body"));
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
            case SUCCESS -> sendEmbed(event, COLOR_SUCCESS, messages.get("reset.success-title"),
                    messages.get("reset.success-body", "username", result.username,
                            "password", result.newPassword));
            case NOT_FOUND -> sendEmbed(event, COLOR_ERROR, messages.get("account-not-found.title"),
                    messages.get("account-not-found.body"));
            case PLAYER_ONLINE -> sendEmbed(event, COLOR_ERROR, messages.get("reset.online-title"),
                    messages.get("reset.online-body"));
            case RATE_LIMITED -> sendEmbed(event, COLOR_ERROR, messages.get("reset.rate-title"),
                    messages.get("reset.rate-body", "duration", formatDuration(result.retryAfterSeconds)));
            case DB_ERROR -> sendEmbed(event, COLOR_ERROR, messages.get("server-error.title"),
                    messages.get("server-error.body"));
        }
    }

    private boolean isPlayerOnline(String username) {
        return plugin.getServer().getOnlinePlayers().stream()
                .anyMatch(p -> p.getName().equalsIgnoreCase(username));
    }

    private void kickPlayerForReset(String username) {
        plugin.getPluginScheduler().global(() -> {
            plugin.getServer().getOnlinePlayers().stream()
                    .filter(p -> p.getName().equalsIgnoreCase(username))
                    .findFirst()
                    .ifPresent(p -> plugin.getPluginScheduler().player(p,
                            () -> p.kick(plugin.getMessageUtil().getPlain(p, "auth.discord-reset-kick"))));
        });
    }

    private String formatDuration(long seconds) {
        long hours = seconds / 3600;
        long minutes = (seconds % 3600) / 60;
        if (hours > 0) return messages.get("duration.hours",
                "hours", String.valueOf(hours), "minutes", String.valueOf(minutes));
        return messages.get("duration.minutes", "minutes", String.valueOf(minutes));
    }

    private void processCode(String code, User discordUser, MessageReceivedEvent event) {
        String discordId = discordUser.getId();

        try {
            Optional<Account> existingByDiscord = plugin.getAccountRepository().findByDiscordId(discordId);
            if (existingByDiscord.isPresent()) {
                String linkedTo = existingByDiscord.get().getUsername();
                sendEmbed(event, COLOR_ERROR, messages.get("link.discord-used-title"),
                        messages.get("link.discord-used-body", "username", linkedTo));
                return;
            }

            Optional<String> usernameOpt = plugin.getLinkCodeManager().peekCode(code);
            if (usernameOpt.isEmpty()) {
                sendEmbed(event, COLOR_ERROR, messages.get("link.invalid-title"),
                        messages.get("link.invalid-body"));
                return;
            }

            String username = usernameOpt.get();
            Optional<Account> accountOpt = plugin.getAccountRepository().findByUsername(username);
            if (accountOpt.isEmpty()) {
                sendEmbed(event, COLOR_ERROR, messages.get("link.invalid-title"),
                        messages.get("link.invalid-body"));
                return;
            }

            Account account = accountOpt.get();
            if (account.hasDiscordLinked()) {
                sendEmbed(event, COLOR_ERROR, messages.get("link.account-used-title"),
                        messages.get("link.account-used-body", "username", username));
                return;
            }

            account.setDiscordId(discordId);
            plugin.getAccountRepository().update(account);
            plugin.getDiscordHistoryRepository().linked(username, discordId);
            plugin.getLinkCodeManager().consumeCode(code);
            plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.DISCORD_LINKED,
                    username, null, "discord_id=" + discordId);

            assignPlayerRole(discordId, username);

            sendEmbed(event, COLOR_SUCCESS, messages.get("link.success-title"),
                    messages.get("link.success-body", "username", username));

            plugin.getLogger().info("Discord " + discordUser.getName()
                    + " (" + discordId + ") linked to " + username);
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on discord link: " + e.getMessage());
            sendEmbed(event, COLOR_ERROR, messages.get("server-error.title"),
                    messages.get("server-error.body"));
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
                                  DiscordIntegration.LocationInfo info) {
        if (jda == null) return;
        jda.retrieveUserById(discordId).queue(user -> {
            if (user == null) return;
            user.openPrivateChannel().queue(channel -> {
                String ipLine = plugin.getConfigManager().isDiscordShowIp()
                        ? messages.get("notify.ip", "ip", maskIp(ip)) : "";
                String cityLine = info.city() != null
                        ? messages.get("notify.city", "city", info.city()) : "";
                net.dv8tion.jda.api.entities.MessageEmbed embed = new net.dv8tion.jda.api.EmbedBuilder()
                        .setColor(new java.awt.Color(0xF39C12))
                        .setTitle(messages.get("notify.title"))
                        .setDescription(messages.get("notify.body",
                                "username", username, "country", info.name(),
                                "code", info.code(), "city_line", cityLine, "ip_line", ipLine))
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

    private String maskIp(String ip) {
        if (ip == null || ip.isBlank()) return "unknown";
        String[] v4 = ip.split("\\.");
        if (v4.length == 4) return v4[0] + ".xx.xx." + v4[3];
        String[] v6 = ip.split(":");
        if (v6.length > 1) return v6[0] + ":xxxx:...:" + v6[v6.length - 1];
        return "hidden";
    }
}
