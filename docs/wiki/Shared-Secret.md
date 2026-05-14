# Shared Secret

## How It Works

- Leave `shared-secret` as `CHANGE_ME` on the proxy for first start.
- The proxy generates a random 32-byte hex secret and writes it into `proxy/config.yml`.
- Copy that exact value into every backend `config.yml`.

## Important

- The backend does not generate the secret automatically.
- Both sides must match exactly or RTP requests will be rejected.
