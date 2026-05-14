# Troubleshooting

## Common Issues

- `shared-secret` mismatch: proxy and backend must match exactly.
- No backend selected: check `allowed-backends`.
- Request denied: check `allowed-worlds`, biome blacklist, or spawn protection.
- Timeout: backend may be offline or disconnected.

## Debugging

- Enable `debug: true` in both configs.
- Reload with `/hopskiprtp reload`.

## Logs

- Proxy logs and `audit.log` are in the Velocity data folder.
- Backend logs and `audit.log` are in the Paper plugin folder.
