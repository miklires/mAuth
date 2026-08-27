# Changelog

This project follows [Semantic Versioning](https://semver.org/).

## Unreleased

- Fixed an async teleport race that could leave session-authenticated players in limbo
- Fixed incomplete Brigadier commands returning generic syntax errors instead of localized usage
- Added separate login and registration deadlines with an optional localized bossbar countdown
- Added configurable duplicate-session and shared-IP session protection
- Kept English as the clean-install default and preserved explicit language choices during config migration
- Updated security-sensitive runtime dependencies

## 1.0.0 - 2026-08-13

- Added asynchronous Argon2id authentication with legacy hash migration
- Added H2, SQLite, MySQL, MariaDB, and PostgreSQL storage
- Added TOTP, recovery codes, email recovery, sessions, account locks, and audit history
- Added premium, Floodgate, and Velocity authentication flows
- Added Discord and Telegram addons, local GeoIP, VPN providers, and a local web panel
- Added Paper Brigadier commands, Folia schedulers, and the standalone mAuth API artifact
