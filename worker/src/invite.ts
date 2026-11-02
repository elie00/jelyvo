/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */

export type InvitePage =
  | { code: string; active: false }
  | { code: string; active: true; origin: string; memberCount: number; title: string; artist: string };

function escapeHtml(text: string): string {
  return text
    .replace(/&/g, "&amp;")
    .replace(/</g, "&lt;")
    .replace(/>/g, "&gt;")
    .replace(/"/g, "&quot;")
    .replace(/'/g, "&#39;");
}

/** JSON is not safe inside a script tag until a closing tag can no longer appear in it. */
const scriptValue = (text: string) => JSON.stringify(text).replace(/</g, "\\u003c");

const STYLE = `
* { box-sizing: border-box; margin: 0; padding: 0; }
body { background: #0b0b0e; color: #f3f3f7; font-family: system-ui, sans-serif; min-height: 100vh;
  display: flex; align-items: center; justify-content: center; padding: 24px; }
.card { background: #16161e; border: 1px solid rgba(255,255,255,.08); border-radius: 28px;
  max-width: 440px; width: 100%; padding: 36px 28px; text-align: center; }
h1 { font-size: 24px; margin-bottom: 8px; }
.subtitle { color: #9e9ea7; font-size: 15px; line-height: 1.5; margin-bottom: 28px; }
.code-box { background: rgba(255,255,255,.04); border: 1px solid rgba(255,255,255,.1);
  border-radius: 18px; padding: 16px 20px; margin-bottom: 28px; }
.code-label { font-size: 12px; color: #71717a; text-transform: uppercase; letter-spacing: 1.5px;
  margin-bottom: 6px; }
.code { font-family: ui-monospace, monospace; font-size: 32px; font-weight: 800; letter-spacing: 6px;
  overflow-wrap: anywhere; }
.playing { font-size: 14px; color: #d1d1d6; margin-top: 10px; overflow: hidden;
  text-overflow: ellipsis; white-space: nowrap; }
.button { display: block; padding: 16px 24px; background: #fff; color: #000; font-size: 16px;
  font-weight: 600; text-decoration: none; border-radius: 16px; }
.note { margin-top: 24px; font-size: 13px; color: #71717a; line-height: 1.5; }
.note a { color: #d1d1d6; }`;

export function invitePage(page: InvitePage): string {
  const code = escapeHtml(page.code);
  let content: string;
  if (page.active) {
    const encoded = encodeURIComponent(page.code);
    const server = encodeURIComponent(page.origin);
    const deepLink = `jelyvo://party/${encoded}?server=${server}`;
    const intent = `intent://party/${encoded}?server=${server}#Intent;scheme=jelyvo;end`;
    const playing = page.title
      ? `<div class="playing">${escapeHtml(page.title)} - ${escapeHtml(page.artist)}</div>`
      : "";
    content = `
      <h1>Join the music party</h1>
      <p class="subtitle">${page.memberCount} listening. Opening Jelyvo to play in time with them.</p>
      <div class="code-box">
        <div class="code-label">Party code</div>
        <div class="code">${code}</div>
        ${playing}
      </div>
      <a class="button" href="${escapeHtml(intent)}">Join in Jelyvo</a>
      <p class="note">Didn't open automatically? Tap the button above.<br>
        Don't have Jelyvo yet?
        <a href="https://github.com/elie00/jelyvo-releases/releases" rel="noopener">Download it here</a>.</p>
      <script>
        setTimeout(function () {
          window.location.href = /Android/i.test(navigator.userAgent)
            ? ${scriptValue(intent)}
            : ${scriptValue(deepLink)};
        }, 100);
      </script>`;
  } else {
    content = `
      <h1>Party not found</h1>
      <p class="subtitle">This party code has expired or does not exist on this server.</p>
      <div class="code-box">
        <div class="code-label">Party code</div>
        <div class="code">${code}</div>
      </div>
      <p class="note">Please ask the host for a new invite link.</p>`;
  }
  return `<!DOCTYPE html>
<html lang="en">
<head>
<meta charset="UTF-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Jelyvo Listen Together - Party ${code}</title>
<style>${STYLE}</style>
</head>
<body><main class="card">${content}</main></body>
</html>`;
}
