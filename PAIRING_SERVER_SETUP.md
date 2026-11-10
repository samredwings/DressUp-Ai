# Secure notebook pairing service

The app's auto-connection flow requires the Deno pairing service in `server/pairing_server.js`.
The old six-digit/query-string protocol and the Google Apps Script / public-CORS examples are obsolete and must not be deployed.

## Deploy

1. Create a Deno Deploy project.
2. Deploy the current `server/pairing_server.js` as the entry point.
3. Set the app's `PAIRING_SERVER` / `DEFAULT_PAIRING_SERVER` URL to the deployed HTTPS origin if it differs from the default `https://dressup-pairing.deno.dev`.
4. Confirm `GET /health` returns `{"status":"ok", ...}`.

The service uses in-memory storage. A deployment restart clears pending pairings; users can simply start a new connection. A ready pairing is read once and removed immediately.

## Protocol

1. Android generates a cryptographically secure 256-bit random token (64 lowercase hexadecimal characters).
2. The token is embedded in the notebook launch URL fragment, not a query parameter.
3. The notebook starts its Flask server and creates an HTTPS tunnel.
4. The notebook sends `POST /register` with JSON `{ "code": "<token>", "endpoint_url": "<https-tunnel-origin>", "gpu": "<gpu-name>" }`.
5. The pairing service accepts only the token format, one registration per token, and HTTPS origins on the explicit tunnel-provider allowlist.
6. Android polls `GET /status` using `Authorization: Bearer <token>`.
7. The service returns the endpoint once, deletes the record, and the app checks the same host allowlist before configuring the cloud client.

The app never displays the bearer token. Do not print it from the notebook, paste it into issue trackers, or include it in screenshots or logs.

## Security controls

- 256-bit pairing tokens instead of six-digit codes.
- No token in the status query string or server logs.
- No CORS wildcard; the service is called by native Android and notebook server-side code.
- HTTPS-only tunnel endpoint validation and explicit host suffix allowlist.
- Single-use registration and retrieval; ten-minute server-side expiry.
- Request body limit, registration token validation, bounded GPU label, and active-entry cap.
- The pairing service stores only a temporary tunnel origin and GPU label. It does not receive user photos.
- The Flask `/tryon` route requires `Authorization: Bearer <token>`, caps request sizes and image pixels, validates image data URIs, and rate-limits requests.

## Notebook compatibility warning

`AutoColabManager` still launches a GitHub-hosted notebook URL. That remote repository is not part of this ZIP. Update its notebook content with the current `server/colab_auto_backend.py` implementation before deploying the new pairing service. The Kaggle notebook is also hosted externally and was not included here; its registration and server routes must be brought to the same bearer-token protocol. Otherwise those external notebooks may still use the old six-digit or query-string flow and will not connect.
