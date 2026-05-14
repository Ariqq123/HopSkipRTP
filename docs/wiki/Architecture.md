# Architecture

## Flow

1. Player runs `/rtp` on Velocity.
2. Proxy validates permission, cooldown, and rate limits.
3. Proxy picks an allowed backend and sends a signed RTP request.
4. Backend checks world and safety rules.
5. Backend teleports the player and replies with success or failure.

## Shared Code

- `common` holds the protocol version, request/response records, and codec.
- Proxy and backend must use the same `shared-secret`.
