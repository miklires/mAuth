package io.github.miklires.mauth.telegram;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.audit.AuditEvent;
import io.github.miklires.mauth.auth.PasswordResetService;
import io.github.miklires.mauth.model.Account;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TelegramBot implements AutoCloseable {

    private static final Pattern UPDATE = Pattern.compile(
            "\\\"update_id\\\":(\\d+).*?\\\"chat\\\":\\{\\\"id\\\":(-?\\d+).*?\\}.*?\\\"text\\\":\\\"((?:\\\\.|[^\\\"])*)\\\"",
            Pattern.DOTALL);

    private final MAuth plugin;
    private final String baseUrl;
    private final HttpClient http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final Map<String, Long> resetConfirmations = new ConcurrentHashMap<>();
    private volatile boolean running;
    private Thread worker;
    private long offset;

    public TelegramBot(MAuth plugin, String token) {
        this.plugin = plugin;
        baseUrl = "https://api.telegram.org/bot" + token + "/";
    }

    public void start() {
        running = true;
        worker = Thread.ofVirtual().name("mauth-telegram").start(this::poll);
        plugin.getLogger().info("telegram bot connecting");
    }

    private void poll() {
        while (running) {
            try {
                String body = call("getUpdates", "offset=" + offset + "&timeout="
                        + plugin.getConfigManager().getTelegramPollTimeout());
                Matcher matcher = UPDATE.matcher(body);
                while (matcher.find()) {
                    long updateId = Long.parseLong(matcher.group(1));
                    offset = Math.max(offset, updateId + 1);
                    handle(matcher.group(2), unescape(matcher.group(3)).trim());
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (Exception e) {
                if (running) plugin.getLogger().warning("telegram poll failed: " + e.getMessage());
                try {
                    Thread.sleep(3000);
                } catch (InterruptedException ex) {
                    Thread.currentThread().interrupt();
                    return;
                }
            }
        }
    }

    private void handle(String chatId, String text) {
        String[] args = text.split("\\s+");
        String command = args[0].toLowerCase(Locale.ROOT).split("@", 2)[0];
        if ((command.equals("/link") || command.equals("/start")) && args.length == 2) {
            link(chatId, args[1].toUpperCase(Locale.ROOT));
            return;
        }
        if (command.equals("/reset") && args.length == 1) {
            requestReset(chatId);
            return;
        }
        if (command.equals("/reset") && args.length == 2 && args[1].equalsIgnoreCase("confirm")) {
            confirmReset(chatId);
            return;
        }
        send(chatId, "Commands:\n/link CODE\n/reset\n/reset confirm");
    }

    private void link(String chatId, String code) {
        try {
            Optional<Account> existing = plugin.getAccountRepository().findByTelegramId(chatId);
            if (existing.isPresent()) {
                send(chatId, "This Telegram account is already linked to " + existing.get().getRegisteredName());
                return;
            }
            Optional<String> username = plugin.getLinkCodeManager().peekCode(code);
            if (username.isEmpty()) {
                send(chatId, "Invalid or expired code");
                return;
            }
            Optional<Account> found = plugin.getAccountRepository().findByUsername(username.get());
            if (found.isEmpty() || found.get().hasTelegramLinked()) {
                send(chatId, "This Minecraft account is already linked");
                return;
            }
            Account account = found.get();
            account.setTelegramId(chatId);
            plugin.getAccountRepository().update(account);
            plugin.getLinkCodeManager().consumeCode(code);
            plugin.getAuditLogger().log(AuditEvent.TELEGRAM_LINKED, account.getUsername(), null,
                    "telegram_id=" + chatId);
            send(chatId, "Linked to " + account.getRegisteredName());
        } catch (Exception e) {
            plugin.getLogger().severe("telegram link failed: " + e.getMessage());
            send(chatId, "Server error");
        }
    }

    private void requestReset(String chatId) {
        try {
            Optional<Account> account = plugin.getAccountRepository().findByTelegramId(chatId);
            if (account.isEmpty()) {
                send(chatId, "No linked account");
                return;
            }
            resetConfirmations.put(chatId, System.currentTimeMillis() + 300_000);
            String username = account.get().getUsername();
            plugin.getPluginScheduler().global(() -> {
                var player = plugin.getServer().getPlayerExact(account.get().getRegisteredName());
                if (player != null) plugin.getPluginScheduler().player(player,
                        () -> player.kick(plugin.getMessageUtil().getPlain(player, "auth.discord-reset-kick")));
            });
            send(chatId, "Reset password for " + username + "? Send /reset confirm within 5 minutes");
        } catch (Exception e) {
            plugin.getLogger().severe("telegram reset request failed: " + e.getMessage());
            send(chatId, "Server error");
        }
    }

    private void confirmReset(String chatId) {
        Long expiry = resetConfirmations.remove(chatId);
        if (expiry == null || expiry < System.currentTimeMillis()) {
            send(chatId, "Reset confirmation expired");
            return;
        }
        PasswordResetService.ResetResult result = plugin.getPasswordResetService().resetByTelegramId(chatId);
        switch (result.status) {
            case SUCCESS -> send(chatId, "Password reset for " + result.username + ": " + result.newPassword);
            case PLAYER_ONLINE -> send(chatId, "Player is still online. Try again");
            case RATE_LIMITED -> send(chatId, "Password was reset recently. Try again later");
            case NOT_FOUND -> send(chatId, "No linked account");
            case DB_ERROR -> send(chatId, "Server error");
        }
    }

    private void send(String chatId, String text) {
        try {
            call("sendMessage", "chat_id=" + encode(chatId) + "&text=" + encode(text));
        } catch (Exception e) {
            plugin.getLogger().warning("telegram message failed: " + e.getMessage());
        }
    }

    private String call(String method, String form) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl + method))
                .timeout(Duration.ofSeconds(plugin.getConfigManager().getTelegramPollTimeout() + 10L))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form)).build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200 || !response.body().contains("\"ok\":true")) {
            throw new IllegalStateException("bot api returned " + response.statusCode());
        }
        return response.body();
    }

    private String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String unescape(String value) {
        return value.replace("\\n", "\n").replace("\\\"", "\"").replace("\\\\", "\\");
    }

    @Override
    public void close() {
        running = false;
        if (worker != null) worker.interrupt();
    }
}
