package io.github.miklires.mauth.listener;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.api.PlayerAuthenticatedEvent;
import io.github.miklires.mauth.model.Account;
import io.github.miklires.mauth.util.MessageUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;

import java.sql.SQLException;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class PlayerJoinListener implements Listener {

    private final MAuth plugin;
    private final MiniMessage mm = MiniMessage.miniMessage();

    public PlayerJoinListener(MAuth plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onPreLogin(AsyncPlayerPreLoginEvent event) {
        String enteredName = event.getName();
        plugin.getCaptchaManager().getFloodDetector().recordConnection();
        if (isBlockedUsername(enteredName)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessageUtil().getPlain("auth.username-blocked"));
            return;
        }
        String username = enteredName.toLowerCase();
        if (plugin.getConfigManager().isDuplicateSessionBlocked()
                && plugin.getSessionManager().isOnline(username)) {
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    plugin.getMessageUtil().getPlain("auth.duplicate-session"));
            return;
        }
        try {
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty() && plugin.getConfigManager().isAttackWhitelistMode()
                    && plugin.getCaptchaManager().getFloodDetector().isFloodActive()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessageUtil().getPlain("auth.attack-whitelist"));
                return;
            }
            if (opt.isPresent() && plugin.getConfigManager().isUsernameCaseEnforced()
                    && opt.get().getRegisteredName() != null
                    && !enteredName.equals(opt.get().getRegisteredName())) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessageUtil().getPlain("auth.username-case",
                                MessageUtil.ph("name", opt.get().getRegisteredName())));
                return;
            }
            if (opt.isPresent() && opt.get().isTemporarilyLocked()) {
                long seconds = Math.max(1, opt.get().getLockedUntil().getEpochSecond()
                        - System.currentTimeMillis() / 1_000L);
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED,
                        plugin.getMessageUtil().getPlain("auth.account-locked",
                                MessageUtil.ph("seconds", (int) Math.min(Integer.MAX_VALUE, seconds))));
                return;
            }
            String ip = event.getAddress().getHostAddress();
            if (opt.isPresent() && plugin.getConfigManager().getNewIpPolicy().equals("deny")
                    && !plugin.getKnownIpRepository().isKnown(username, ip)
                    && plugin.getKnownIpRepository().countForAccount(username) > 0) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                        plugin.getMessageUtil().getPlain("auth.new-ip-denied"));
                return;
            }
            if (opt.isEmpty() || !plugin.getConfigManager().isWhitelistEnabled()) return;
            if (!opt.get().isWhitelisted()) {
                event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_WHITELIST,
                        mm.deserialize(plugin.getConfigManager().getKickMessage()));
            }
        } catch (SQLException e) {
            plugin.getLogger().severe("db error on prelogin: " + e.getMessage());
            event.disallow(AsyncPlayerPreLoginEvent.Result.KICK_OTHER,
                    mm.deserialize("<red>Server error. Try again later."));
        }
    }

    private boolean isBlockedUsername(String username) {
        for (String expression : plugin.getConfigManager().getBlockedUsernamePatterns()) {
            try {
                if (Pattern.compile(expression, Pattern.CASE_INSENSITIVE).matcher(username).matches()) {
                    return true;
                }
            } catch (PatternSyntaxException e) {
                plugin.getLogger().warning("invalid blocked username pattern: " + expression);
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        String username = player.getName().toLowerCase();
        UUID uuid = player.getUniqueId();
        String ip = player.getAddress() != null ? player.getAddress().getAddress().getHostAddress() : null;
        String xuid = plugin.getFloodgateBridge().getXuid(uuid).orElse(null);

        plugin.getSessionManager().markOnline(player);
        if (!plugin.getPlayerStateStore().protect(player)) return;
        CompletableFuture<Boolean> limboReady = plugin.getLimboWorldManager().sendToLimbo(player)
                .exceptionally(error -> {
                    plugin.getLogger().warning("cannot send " + player.getName()
                            + " to limbo: " + error.getMessage());
                    return false;
                });

        CompletableFuture.supplyAsync(() -> load(player.getName(), username, uuid, ip, xuid),
                        plugin.getAuthExecutor())
                .thenCombine(limboReady, (result, ignored) -> result)
                .whenComplete((result, error) -> plugin.getPluginScheduler().player(player, () -> {
                    if (!player.isOnline()) return;
                    if (error != null) {
                        plugin.getLogger().severe("join lookup failed: " + error.getMessage());
                        player.kick(mm.deserialize("<red>Server error."));
                        return;
                    }
                    finishJoin(player, result);
                }));
    }

    private JoinResult load(String registeredName, String username, UUID uuid, String ip, String xuid) {
        try {
            if (xuid != null) {
                Optional<Account> linked = plugin.getAccountRepository().findByBedrockXuid(xuid);
                if (linked.isPresent()) {
                    plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.BEDROCK_LOGIN,
                            linked.get().getUsername(), ip);
                    return new JoinResult(JoinState.AUTHENTICATED, linked.get(), false, false,
                            PlayerAuthenticatedEvent.AuthReason.BEDROCK);
                }
            }
            Optional<Account> opt = plugin.getAccountRepository().findByUsername(username);
            if (opt.isEmpty() && xuid != null && plugin.getConfigManager().isFloodgateAutoRegister()) {
                Account account = new Account(registeredName,
                        plugin.getPasswordHasher().hash(UUID.randomUUID().toString()));
                account.setBedrockXuid(xuid);
                account.setLastIp(ip);
                plugin.getAccountRepository().insert(account);
                if (ip != null) plugin.getKnownIpRepository().recordIp(username, ip);
                plugin.getAuditLogger().log(io.github.miklires.mauth.audit.AuditEvent.REGISTER,
                        username, ip, "source=floodgate");
                return new JoinResult(JoinState.AUTHENTICATED, account, false, false,
                        PlayerAuthenticatedEvent.AuthReason.BEDROCK);
            }
            if (opt.isEmpty()) return new JoinResult(JoinState.NEW_ACCOUNT, null, false, false, null);
            Account a = opt.get();
            boolean discordRequired = !a.hasDiscordLinked()
                    && plugin.getConfigManager().getDiscordMode().requiresLink(
                    a.getRegisteredAt(), plugin.getConfigManager().getDiscordRequiredAfter());

            if (!discordRequired && a.isPremiumEnabled() && uuid.equals(a.getPremiumUuid())) {
                return new JoinResult(JoinState.AUTHENTICATED, a, false, false,
                        PlayerAuthenticatedEvent.AuthReason.SESSION);
            }
            boolean sharedIp = ip != null && plugin.getConfigManager().isSharedIpSessionBlocked()
                    && plugin.getKnownIpRepository().countAccounts(ip) > 1;
            if (!discordRequired && !sharedIp && ip != null
                    && plugin.getSessionManager().hasValidIpSession(username, ip, uuid)) {
                return new JoinResult(JoinState.AUTHENTICATED, a, false, false,
                        PlayerAuthenticatedEvent.AuthReason.SESSION);
            }

            boolean captcha = plugin.getCaptchaManager().shouldRequireForLogin(username, ip);
            boolean flood = plugin.getCaptchaManager().getFloodDetector().isFloodActive();
            return new JoinResult(JoinState.LOGIN_REQUIRED, a, captcha, flood, null);
        } catch (SQLException e) {
            throw new IllegalStateException("db error on join", e);
        }
    }

    private void finishJoin(Player player, JoinResult result) {
        if (result.state == JoinState.NEW_ACCOUNT) {
            if (plugin.getCaptchaManager().shouldRequireForRegistration()) {
                String code = plugin.getCaptchaManager().generateFor(player);
                plugin.getMessageUtil().send(player, "captcha.welcome", MessageUtil.ph("code", code));
            } else {
                plugin.getMessageUtil().send(player, "auth.please-register");
            }
            plugin.getAuthTimeoutManager().startRegistration(player);
            return;
        }

        if (result.state == JoinState.AUTHENTICATED) {
            plugin.getAuthManager().completeLogin(player, result.account, result.reason);
            plugin.getMessageUtil().send(player, "auth.logged-in");
            if (result.reason == PlayerAuthenticatedEvent.AuthReason.BEDROCK) {
                plugin.getMessageUtil().send(player, "auth.bedrock-logged-in");
            }
            return;
        }

        if (result.captcha) {
            String code = plugin.getCaptchaManager().generateFor(player);
            plugin.getMessageUtil().send(player, result.flood ? "captcha.flood" : "captcha.new-ip",
                    MessageUtil.ph("code", code));
        } else {
            plugin.getMessageUtil().send(player, "auth.please-login");
        }
        plugin.getAuthTimeoutManager().startLogin(player);
    }

    private enum JoinState {
        NEW_ACCOUNT, LOGIN_REQUIRED, AUTHENTICATED
    }

    private record JoinResult(JoinState state, Account account, boolean captcha, boolean flood,
                              PlayerAuthenticatedEvent.AuthReason reason) {}
}
