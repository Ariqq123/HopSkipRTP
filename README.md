# HopSkipRTP

Proxy-aware RTP for Velocity + Paper, built with Java 21 and the Gradle Wrapper.

## What It Does

- `/rtp` runs on Velocity and picks an allowed backend.
- The backend performs the safe random teleport.
- Shared plugin messaging keeps the proxy and backend in sync.

## Modules

- `proxy`: Velocity command, backend selection, cooldowns, rate limiting
- `backend`: Paper-side teleport execution and safety checks
- `common`: shared RTP protocol and codec

## Install

1. Build with `./gradlew clean build`.
2. Put `proxy/build/libs/HopSkipRTP-velocity-0.1.1.jar` in Velocity.
3. Put `backend/build/libs/HopSkipRTP-paper-0.1.1.jar` in each Paper backend.
4. Set the same `shared-secret` in both configs.
5. Add your backend names to `allowed-backends` and `allowed-worlds`.

## Commands

- `/rtp`
- `/hopskiprtp`
- `/hopskiprtp reload`

## Permissions

- `hopskiprtp.use`
- `hopskiprtp.bypass.cooldown`
- `hopskiprtp.admin.reload`

## Config

- Proxy config: `proxy/src/main/resources/config.yml`
- Backend config: `backend/src/main/resources/config.yml`
- Shared protocol: `common/src/main/java/dev/azreyzaako/hopskiprtp/common`

## Wiki

Wiki source lives in this repo under `docs/wiki/`, and the published GitHub Wiki will mirror it:

- `docs/wiki/`
- https://github.com/Ariqq123/HopSkipRTP/wiki

## Build

Use `./gradlew build`.
