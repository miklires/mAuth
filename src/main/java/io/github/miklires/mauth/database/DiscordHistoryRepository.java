package io.github.miklires.mauth.database;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

public class DiscordHistoryRepository {

    private final DatabaseManager database;

    public DiscordHistoryRepository(DatabaseManager database) {
        this.database = database;
    }

    public void linked(String username, String discordId) throws SQLException {
        String sql = "INSERT INTO mauth_discord_history "
                + "(username, discord_id, linked_at, unlinked_at) VALUES (?, ?, ?, NULL)";
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            ps.setString(2, discordId);
            ps.setLong(3, System.currentTimeMillis() / 1_000L);
            ps.executeUpdate();
        }
    }

    public void unlinked(String username, String discordId) throws SQLException {
        String sql = "UPDATE mauth_discord_history SET unlinked_at = ? "
                + "WHERE username = ? AND discord_id = ? AND unlinked_at IS NULL";
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, System.currentTimeMillis() / 1_000L);
            ps.setString(2, username.toLowerCase());
            ps.setString(3, discordId);
            ps.executeUpdate();
        }
    }

    public List<Entry> list(String username, int limit) throws SQLException {
        String sql = "SELECT discord_id, linked_at, unlinked_at FROM mauth_discord_history "
                + "WHERE username = ? ORDER BY linked_at DESC";
        List<Entry> result = new ArrayList<>();
        try (Connection c = database.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, username.toLowerCase());
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next() && result.size() < limit) {
                    long unlinked = rs.getLong("unlinked_at");
                    boolean active = rs.wasNull();
                    result.add(new Entry(rs.getString("discord_id"), rs.getLong("linked_at"),
                            active ? null : unlinked));
                }
            }
        }
        return result;
    }

    public record Entry(String discordId, long linkedAt, Long unlinkedAt) {
    }
}
