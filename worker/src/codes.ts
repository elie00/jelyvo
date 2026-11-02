/*
 * Adapted from BitChord's party server (https://github.com/kushagrasinghx/BitChord), GPL-3.0.
 * Copyright (C) Kushagra Singh and BitChord contributors.
 */

export const ALPHABET = "0123456789ABCDEFGHJKMNPQRSTUVWXYZ";
export const CODE_LENGTH = 6;

export function newCode(): string {
  // Rejecting values past the last whole multiple keeps every character equally likely.
  const limit = 256 - (256 % ALPHABET.length);
  let code = "";
  while (code.length < CODE_LENGTH) {
    for (const byte of crypto.getRandomValues(new Uint8Array(CODE_LENGTH * 2))) {
      if (byte < limit && code.length < CODE_LENGTH) code += ALPHABET[byte % ALPHABET.length];
    }
  }
  return code;
}

/** Reads a mistyped code charitably: drops separators and maps I and L to 1, O to 0. */
export function normalise(code: string): string {
  let out = "";
  for (const char of code.trim().toUpperCase()) {
    if (!/[\p{L}\p{N}]/u.test(char)) continue;
    if (char === "I" || char === "L") out += "1";
    else if (char === "O") out += "0";
    else out += char;
  }
  return out;
}

export function isValid(code: string): boolean {
  return code.length === CODE_LENGTH && [...code].every((char) => ALPHABET.includes(char));
}
