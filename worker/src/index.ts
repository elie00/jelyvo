/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */
import { CODE_LENGTH, isValid, newCode, normalise } from "./codes";
import { CONFIG, type Env, isAllowedOrigin } from "./config";
import { invitePage } from "./invite";
import { parseJoinRequest, PartyError } from "./party";
import { IpRateLimiter } from "./rateLimit";
import { socketToken } from "./room";

export { PartyBudget } from "./budget";
export { PartyRoom } from "./room";

/**
 * Lives as long as this copy of the Worker. That makes it approximate across Cloudflare's edge, but
 * it runs before anything that spends Durable Object quota and costs nothing itself.
 */
const limiter = new IpRateLimiter(60_000, CONFIG.requestsPerMinutePerIp, CONFIG.rateLimitMaxEntries);

const json = (status: number, body: unknown) => Response.json(body, { status });
const jsonError = (status: number, error: string, message: string) => json(status, { error, message });
const noSuchParty = () => jsonError(404, "no_such_party", "No party with that code.");

/** A code that does not even decode is simply not a code. */
function codeFrom(raw: string): string {
  try {
    return normalise(decodeURIComponent(raw));
  } catch {
    return "";
  }
}

const room = (env: Env, code: string) => env.PARTY_ROOM.get(env.PARTY_ROOM.idFromName(code));
const budget = (env: Env) => env.PARTY_BUDGET.get(env.PARTY_BUDGET.idFromName("global"));

function roomRequest(path: string, init?: RequestInit<CfProperties>): Request {
  return new Request(`https://room${path}`, init);
}

async function readBody(request: Request): Promise<Response | unknown> {
  const text = await request.text();
  if (new TextEncoder().encode(text).byteLength > CONFIG.requestMaxBytes) {
    return jsonError(413, "request_too_large", "Request body is too large.");
  }
  try {
    return JSON.parse(text);
  } catch {
    return jsonError(422, "invalid_json", "Malformed JSON body.");
  }
}

async function identityFrom(request: Request) {
  const body = await readBody(request);
  if (body instanceof Response) return body;
  try {
    return parseJoinRequest(body);
  } catch (error) {
    if (error instanceof PartyError) return jsonError(error.status, error.code, error.message);
    throw error;
  }
}

function publicOrigin(request: Request, env: Env): string {
  return env.PUBLIC_ORIGIN?.replace(/\/+$/, "") || new URL(request.url).origin;
}

async function createParty(request: Request, env: Env): Promise<Response> {
  const identity = await identityFrom(request);
  if (identity instanceof Response) return identity;

  const code = newCode();
  const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
  const admission = await budget(env).admitParty(ip, code);
  if (!admission.ok) return jsonError(admission.status, admission.error, admission.message);

  const response = await room(env, code).fetch(
    roomRequest("/create", { method: "POST", body: JSON.stringify({ ...identity, code }) }),
  );
  if (response.status === 201) return response;
  await budget(env).release(code, { requests: 0, rowsWritten: 0 });
  return response.status === 409
    ? jsonError(503, "code_exhausted", "Could not allocate a party code.")
    : response;
}

async function route(request: Request, env: Env): Promise<Response> {
  const { pathname } = new URL(request.url);
  const method = request.method;

  if (method === "GET" && pathname === "/") {
    return json(200, { service: "jelyvo-listen-together", serverMs: Date.now() });
  }
  if (method === "GET" && pathname === "/healthz") return json(200, { ok: true, serverMs: Date.now() });
  if (method === "GET" && pathname === "/api/time") return json(200, { serverMs: Date.now() });
  if (method === "GET" && pathname === "/api/budget") return json(200, await budget(env).status());
  if (method === "POST" && pathname === "/api/parties") return createParty(request, env);

  const invite = pathname.match(/^\/invite\/([^/]+)$/);
  if (method === "GET" && invite) return inviteLanding(invite[1]!, request, env);

  const socket = pathname.match(/^\/ws\/parties\/([^/]+)$/);
  if (method === "GET" && socket) {
    if (request.headers.get("Upgrade")?.toLowerCase() !== "websocket") {
      return new Response("expected a WebSocket", { status: 426 });
    }
    if (socketToken(request) === "") return new Response("missing token", { status: 401 });
    const code = codeFrom(socket[1]!);
    if (!isValid(code)) return new Response("no such party", { status: 404 });
    return room(env, code).fetch(roomRequest("/ws", { headers: request.headers }));
  }

  const party = pathname.match(/^\/api\/parties\/([^/]+)(\/join|\/leave|\/preview)?$/);
  if (!party) return jsonError(404, "not_found", "Not found.");
  const code = codeFrom(party[1]!);
  // A code that cannot exist never reaches a Durable Object, so guessing spends nothing.
  if (!isValid(code)) return noSuchParty();
  const action = party[2];

  if (method === "POST" && action === "/join") {
    const identity = await identityFrom(request);
    if (identity instanceof Response) return identity;
    return room(env, code).fetch(roomRequest("/join", { method: "POST", body: JSON.stringify(identity) }));
  }
  if (method === "GET" && action === "/preview") return room(env, code).fetch(roomRequest("/preview"));
  if ((method === "GET" && action === undefined) || (method === "POST" && action === "/leave")) {
    if (socketToken(request) === "") return jsonError(401, "unauthorized", "Missing bearer token.");
    return room(env, code).fetch(
      roomRequest(action === "/leave" ? "/leave" : "/snapshot", {
        method: "POST",
        headers: { Authorization: `Bearer ${socketToken(request)}` },
      }),
    );
  }
  return jsonError(405, "method_not_allowed", "Method not allowed.");
}

async function inviteLanding(rawCode: string, request: Request, env: Env): Promise<Response> {
  const code = codeFrom(rawCode);
  const html = { "Content-Type": "text/html; charset=utf-8" };
  if (code.length !== CODE_LENGTH || !isValid(code)) {
    return new Response(invitePage({ code: rawCode, active: false }), { status: 400, headers: html });
  }
  const response = await room(env, code).fetch(roomRequest("/invite"));
  if (!response.ok) {
    return new Response(invitePage({ code, active: false }), { status: 404, headers: html });
  }
  const details = (await response.json()) as { memberCount: number; title: string; artist: string };
  const page = invitePage({ code, active: true, origin: publicOrigin(request, env), ...details });
  return new Response(page, { status: 200, headers: html });
}

function withCors(response: Response, origin: string): Response {
  // A 101 keeps its socket only if it is passed along untouched.
  if (response.status === 101) return response;
  const copy = new Response(response.body, response);
  copy.headers.set("Access-Control-Allow-Origin", origin);
  copy.headers.set("Vary", "Origin");
  copy.headers.set("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
  copy.headers.set("Access-Control-Allow-Headers", "Content-Type, Authorization");
  return copy;
}

export default {
  async fetch(request, env): Promise<Response> {
    const ip = request.headers.get("CF-Connecting-IP") ?? "unknown";
    if (!limiter.allow(ip)) {
      return jsonError(429, "rate_limited", "Too many requests. Please try again shortly.");
    }
    const origin = request.headers.get("Origin");
    const allowed = origin !== null && isAllowedOrigin(env, origin);
    if (request.method === "OPTIONS") {
      if (origin !== null && !allowed) return new Response("origin not allowed", { status: 403 });
      const empty = new Response(null, { status: 204 });
      return allowed ? withCors(empty, origin) : empty;
    }
    const response = await route(request, env);
    return allowed ? withCors(response, origin) : response;
  },
} satisfies ExportedHandler<Env>;
