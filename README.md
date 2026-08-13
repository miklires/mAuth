![mAuth banner](docs/assets/mauth-banner.png)

# mAuth

mAuth is an authentication plugin for cracked and mixed-mode Minecraft networks. It runs on Paper, Purpur, and Folia, with optional Velocity, BungeeCord, Discord, and Telegram addons.

## Requirements

- Java 25
- Paper-compatible server 26.2
- Velocity or BungeeCord only when using a proxy addon

## Install

1. Put `mAuth-1.0.0.jar` in the backend server `plugins` directory.
2. Start the server once and edit `plugins/mAuth/config.yml`.
3. Add optional addon jars beside the core jar.
4. For proxy mode, set the same `shared-secret` on the proxy and backend.

H2 is the default storage. SQLite, MySQL, MariaDB, and PostgreSQL are also supported. Discord, Telegram, email, metrics, update checks, VPN lookup, and the web panel stay disabled until configured.

## Configuration

- `security`: password hashing, login limits, TOTP encryption, sessions, captcha policy, and new IP or device handling
- `storage`: H2, SQLite, MySQL, MariaDB, or PostgreSQL connection settings
- `discord`, `telegram`, and `email`: account linking, verification, notifications, and recovery
- `proxy.shared-secret`: signed synchronization with the Velocity or BungeeCord addon
- `vpn` and `geoip`: risk provider, location database, caching, and fail-open or fail-closed behavior
- `web`: bind address, port, and generated Basic authentication token
- `metrics.bstats-id` and `updates.modrinth-project-id`: optional metrics and update discovery

Do not expose the web panel to the public Internet without a trusted reverse proxy and TLS. Keep bot tokens, SMTP credentials, the proxy secret, and `security.data-encryption-key` private.

## Web panel

The protected panel lists accounts and active sessions and lets an administrator terminate every session for an account.

![mAuth web panel](docs/assets/web-panel.png)

## Artifacts

- `mAuth-1.0.0.jar`: Paper, Purpur, and Folia core
- `mAuth-API-1.0.0.jar`: public interfaces and authentication event
- `mAuth-Velocity-1.0.0.jar`: Velocity login routing and mixed-mode support
- `mAuth-Bungee-1.0.0.jar`: BungeeCord login routing
- `mAuth-Discord-1.0.0.jar`: Discord linking and recovery
- `mAuth-Telegram-1.0.0.jar`: Telegram linking and recovery

## Player commands

`/register`, `/login`, `/logout`, `/changepassword`, `/premium`, `/cracked`, `/2fa`, `/sessions`, `/email`, `/recover`, and `/telegram` are registered through the Paper Commands API. Command suggestions and syntax depend on permissions.

## Administration

`/mauth` provides reload, import, force-login, account lock, and Discord history operations. `/mauthreset`, `/mauthunlink`, `/mauthlog`, and `/mauthip` are owner-facing account tools. Full IP addresses are only exposed through the protected IP-history permission.

## PlaceholderAPI

When PlaceholderAPI is installed, mAuth registers `%mauth_authenticated%`, `%mauth_status%`, and `%mauth_version%`.

## Build

```bash
./gradlew clean build :api:build :discord:build :velocity:build :bungee:build :telegram:build
```

The project is licensed under the MIT License.
