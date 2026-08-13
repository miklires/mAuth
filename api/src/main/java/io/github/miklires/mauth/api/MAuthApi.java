package io.github.miklires.mauth.api;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public interface MAuthApi {

    boolean isAuthenticated(UUID playerUuid);

    CompletableFuture<Boolean> isRegistered(String username);

    CompletableFuture<Void> invalidateSessions(String username);
}
