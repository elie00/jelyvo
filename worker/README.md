# Jelyvo — Listen Together on Cloudflare

> Adapted from [BitChord](https://github.com/kushagrasinghx/BitChord)'s party server (GPL-3.0),
> Copyright (C) Kushagra Singh and BitChord contributors.

The party server behind **Listen together**, on Cloudflare Workers and Durable Objects. It speaks
the same protocol as the Go server in [`backend/`](../backend/README.md), so that README remains the
reference for how devices stay in time and for every REST route and WebSocket frame. This one covers
only what differs.

```bash
npm install
npm test            # runs in the real Workers runtime, locally
npm run typecheck
npx wrangler login
npm run deploy
```

## How it maps

| | |
|---|---|
| `src/index.ts` | The Worker: routes, validation, the per-address limit. Holds no state. |
| `src/room.ts` | `PartyRoom`: one Durable Object per party, named after its code. Sockets, alarms, storage. |
| `src/budget.ts` | `PartyBudget`: one object for the whole server. Estimates the day's use and admits parties. |
| `src/party.ts`, `src/control.ts` | The sync rule, the queue and members. Plain data and no I/O. |

A room uses the WebSocket Hibernation API, so it sleeps between messages. What must survive that
sits in one storage row (the party) or on the socket itself (rate limits, frame count), which costs
no storage write.

## Staying inside the free plan

**The free plan cannot charge.** Past a limit, operations fail until 00:00 UTC. Nothing here changes
that, as long as the Cloudflare account stays on the free plan: upgrading to Workers Paid is what
would make overages billable. The guards below exist so that people lose as little as possible on
the way to a limit.

The limits that matter are 100,000 requests and 100,000 rows written per day. Each alarm costs a
request and a row, an incoming message costs 1/20 of a request, and an outgoing message costs nothing.

| Share of the day used | What changes |
|---|---|
| 50% | The heartbeat goes from 10 to 30 seconds. |
| 70% | No new parties. |
| 85% | No new devices. A device already in a party can still come back. |
| 95% | Parties end with a `bye` frame, reason `server_quota`, rather than failing mid-song. |

Rooms report what they spent every five minutes, so the estimate lags by up to that much.

Fixed limits, in `src/config.ts`:

- 10 live parties, 100 new parties a day, 2 new parties a minute per address.
- 60 requests a minute per address, enforced before anything reaches a Durable Object. Codes that
  cannot exist are refused at the same point.
- 5 messages a second per device and 2 controls a second, with a small burst. A device that keeps
  flooding after 20 refusals is disconnected, since each message is spent when it arrives.
- A party lasts 8 hours at most.
- Alarms run every 10 seconds only while music is playing to a connected device, and once a minute
  otherwise. Pings, syncs and reports are never written to storage.

`GET /api/budget` shows the current estimate. It is an estimate: **Cloudflare's dashboard has the
real figures**. Requests that never reach a room, such as 404s, are not counted. If those alone used
up the Worker's own 100,000 requests, Cloudflare would answer with error 1027 until 00:00 UTC, which
is still free.

## Differences from the Go server

- The heartbeat is 10 seconds rather than 5, and stops while paused, because each one costs quota.
- A party's ceiling is 8 hours rather than 12, with 10 parties live rather than 50.
- Rate limits are lower, and a flooding device gets disconnected.
- `GET /` no longer reports member and party counts, which would cost a request each time. Use
  `GET /api/budget`.
- Invite links use `PUBLIC_ORIGIN` when set, otherwise the address the request came in on.
- `/api/parties/{code}` and `/leave` also accept the token as a subprotocol, because they share the
  socket's token reader.

## Configuration

Both are optional variables in `wrangler.jsonc`:

| | |
|---|---|
| `PUBLIC_ORIGIN` | Used verbatim in invite links, for example `https://jam.example.com`. |
| `ALLOWED_ORIGINS` | Comma-separated browser origins. Native apps send no `Origin` and are always allowed. |

A custom domain on Workers needs the domain's DNS to be on Cloudflare. Until then the server lives at
its `workers.dev` address, which the app accepts like any other `https://` URL.
