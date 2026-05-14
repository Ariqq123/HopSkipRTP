# Configuration

## Proxy Config

- `shared-secret`: must match the backend
- `allowed-backends`: Velocity server names allowed for RTP
- `cooldown-seconds`: per-player cooldown after success
- `warmup-seconds`: delay before dispatch
- `request-timeout-seconds`: timeout for backend response
- `rate-limit`: global and per-player burst/refill limits
- `audit-logging`: writes `audit.log`

## Backend Config

- `shared-secret`: must match the proxy
- `allowed-worlds`: worlds allowed for RTP
- `search-radius`: search area around the player
- `search-attempts`: how many safe spots to try
- `allow-nether` / `allow-end`: dimension toggles
- `biome-blacklist`: biomes to avoid
- `spawn-protection-radius`: stay out of spawn protection
- `audit-logging`: writes `audit.log`

## Reload

- Proxy: `/hopskiprtp reload`
- Backend: `/hopskiprtp reload`
