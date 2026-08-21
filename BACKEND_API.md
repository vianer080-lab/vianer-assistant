# Master Hub Backend API

The Android app never stores Telegram, GitHub, OpenAI or affiliate secrets. It talks to a backend using `EXPO_PUBLIC_MASTER_HUB_API_URL`.

## Initial endpoints

- `GET /health` — backend health check
- `GET /v1/status` — connection/service statuses
- `GET /v1/analytics?days=1|5|10|30` — real analytics
- `GET /v1/publications` — publication history and queue
- `POST /v1/actions/telegram/post` — authenticated manual Telegram publication trigger

## Security requirements

The backend must authenticate the app/user before any write action, rate-limit action endpoints, keep provider tokens only in server-side secrets, and never return provider secrets to the APK.

## Next implementation step

Deploy these endpoints on a server/backend service, set `EXPO_PUBLIC_MASTER_HUB_API_URL` during the Expo build, then replace informational UI values with API responses.
