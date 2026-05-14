# Installation

## Requirements

- Java 21
- Velocity proxy
- Paper backend servers

## Proxy

1. Build the project with `./gradlew clean build`.
2. Copy `proxy/build/libs/HopSkipRTP-velocity-0.1.1.jar` into Velocity.
3. Start the proxy once to generate `proxy/config.yml` and its `shared-secret`.
4. Copy that `shared-secret` into each backend `config.yml`.
5. Set `allowed-backends`.

## Backend

1. Copy `backend/build/libs/HopSkipRTP-paper-0.1.1.jar` into each Paper server.
2. Start the server once to generate `config.yml`.
3. Set the same `shared-secret` copied from the proxy.
4. Add allowed world names to `allowed-worlds`.

## Verification

- Run `/rtp`.
- Confirm the player connects to an allowed backend and teleports successfully.
