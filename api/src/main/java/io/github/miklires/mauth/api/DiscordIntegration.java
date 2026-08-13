package io.github.miklires.mauth.api;

public interface DiscordIntegration {

    boolean isReady();

    void notifyNewCountry(String discordId, String username, String ip, LocationInfo info);

    record LocationInfo(String code, String name, String city) {}
}
