package io.github.miklires.mauth.importer;

import org.bukkit.configuration.ConfigurationSection;

import java.util.Locale;
import java.util.Set;
import java.util.regex.Pattern;

public record ImportProfile(String source, String jdbcUrl, String user, String password,
                            String table, String usernameColumn, String realNameColumn,
                            String passwordColumn, String ipColumn, String registeredColumn,
                            HashFormat hashFormat) {

    private static final Pattern IDENTIFIER = Pattern.compile("[A-Za-z0-9_]+(?:\\.[A-Za-z0-9_]+)?");
    private static final Set<String> SOURCES = Set.of("authme", "nlogin", "librelogin", "opennlogin");

    public static ImportProfile load(ConfigurationSection root, String requested) {
        if (root == null) throw new IllegalArgumentException("imports section is missing");
        String source = requested.toLowerCase(Locale.ROOT);
        if (!SOURCES.contains(source)) throw new IllegalArgumentException("unknown source");
        ConfigurationSection cfg = root.getConfigurationSection(source);
        if (cfg == null) throw new IllegalArgumentException("missing import profile");
        ImportProfile profile = new ImportProfile(
                source,
                cfg.getString("jdbc-url", ""),
                cfg.getString("user", ""),
                cfg.getString("password", ""),
                cfg.getString("table", ""),
                cfg.getString("columns.username", ""),
                cfg.getString("columns.real-name", ""),
                cfg.getString("columns.password", ""),
                cfg.getString("columns.ip", ""),
                cfg.getString("columns.registered-at", ""),
                HashFormat.parse(cfg.getString("hash-format", "preserve")));
        profile.validate();
        return profile;
    }

    private void validate() {
        if (jdbcUrl.isBlank()) throw new IllegalArgumentException("jdbc-url is empty");
        requiredIdentifier(table, "table");
        requiredIdentifier(usernameColumn, "username column");
        requiredIdentifier(passwordColumn, "password column");
        optionalIdentifier(realNameColumn, "real-name column");
        optionalIdentifier(ipColumn, "ip column");
        optionalIdentifier(registeredColumn, "registered-at column");
    }

    private void requiredIdentifier(String value, String label) {
        if (value.isBlank() || !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    private void optionalIdentifier(String value, String label) {
        if (!value.isBlank() && !IDENTIFIER.matcher(value).matches()) {
            throw new IllegalArgumentException("invalid " + label);
        }
    }

    public enum HashFormat {
        PRESERVE, MD5, SHA256, SHA512;

        static HashFormat parse(String value) {
            try {
                return valueOf(value.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("unsupported hash format");
            }
        }

        public String prepare(String hash) {
            return switch (this) {
                case PRESERVE -> hash;
                case MD5 -> "{MD5}" + hash;
                case SHA256 -> "{SHA256}" + hash;
                case SHA512 -> "{SHA512}" + hash;
            };
        }
    }
}
