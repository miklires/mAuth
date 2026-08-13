package io.github.miklires.mauth.proxy;

import io.github.miklires.mauth.MAuth;
import io.github.miklires.mauth.api.PlayerAuthenticatedEvent;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

public class ProxyAuthMessenger implements Listener {

    public static final String CHANNEL = "mauth:auth";

    private final MAuth plugin;
    private final byte[] secret;
    private final SecureRandom random = new SecureRandom();

    public ProxyAuthMessenger(MAuth plugin, String secret) {
        this.plugin = plugin;
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
    }

    @EventHandler
    public void onAuthenticated(PlayerAuthenticatedEvent event) {
        Player player = event.getPlayer();
        long timestamp = System.currentTimeMillis() / 1_000L;
        byte[] nonceBytes = new byte[16];
        random.nextBytes(nonceBytes);
        String nonce = Base64.getUrlEncoder().withoutPadding().encodeToString(nonceBytes);
        String body = "1|" + player.getUniqueId() + "|" + timestamp + "|" + nonce;
        String payload = body + "|" + sign(body);
        player.sendPluginMessage(plugin, CHANNEL, payload.getBytes(StandardCharsets.UTF_8));
    }

    private String sign(String body) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret, "HmacSHA256"));
            return Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }
}
