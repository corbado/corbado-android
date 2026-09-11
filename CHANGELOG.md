# Changelog

All notable changes to this project will be documented in this file.

## [1.4.1] - 2026-09-11
- Re-run login-init before login-start (and manage-init before passkey-list append/delete) when the init data has expired. Previously a login attempt on a login screen left open for longer than the process lifetime silently fell back to password login.
- Publish releases from GitHub Actions (`v<semver>` tags), see `RELEASING.md`.

## [1.3.0] - 2025-10-18
- Add support for situational appends.
- Collect device information.

## [0.2.1] - 2025-06-05

### Added
- Initial release of the Corbado Android SDK (`CorbadoConnect`).
- Functionality for passkey-based authentication: one-tap login, identifier-first login, and conditional UI.
- Functionality for passkey creation (append) and management (list, delete).
- `ConnectExample` application to demonstrate the SDK's features.