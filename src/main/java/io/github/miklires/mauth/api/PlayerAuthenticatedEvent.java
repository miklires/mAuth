package io.github.miklires.mauth.api;

import org.bukkit.entity.Player;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.jetbrains.annotations.NotNull;

public class PlayerAuthenticatedEvent extends Event {

    private static final HandlerList HANDLERS = new HandlerList();

    private final Player player;
    private final AuthReason reason;

    public PlayerAuthenticatedEvent(Player player, AuthReason reason) {
        this.player = player;
        this.reason = reason;
    }

    public Player getPlayer() {
        return player;
    }

    public AuthReason getReason() {
        return reason;
    }

    @NotNull
    @Override
    public HandlerList getHandlers() {
        return HANDLERS;
    }

    public static HandlerList getHandlerList() {
        return HANDLERS;
    }

    public enum AuthReason {
        LOGIN,
        SESSION,
        REGISTER
    }
}
