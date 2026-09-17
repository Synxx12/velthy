/**
 * Party codes: six characters, read aloud across a room without ambiguity.
 *
 * The alphabet is alphanumeric minus `I`, `L` and `O` — the three letters that
 * get misheard as, or mistyped for, the digits `1` and `0`. Dropping the letter
 * rather than the digit is what makes the ambiguity resolvable instead of merely
 * rarer: with no `O` anywhere in the namespace, "oh" can only have meant zero,
 * so a mistyped code is corrected on arrival by `normalise` rather than bounced
 * back at whoever read it out.
 *
 * That leaves 33 symbols, so a code is 33**6 ≈ 1.29 billion possibilities. The
 * collision check at the call site makes the birthday maths moot; the size is
 * here so that guessing your way into a stranger's party is not a thing that
 * happens.
 *
 * `crypto.randomInt` rather than `Math.random`, for the same reason: the code
 * *is* the invitation, and a predictable generator is the wrong tool for minting
 * one.
 */
import { randomInt } from 'node:crypto';

export const ALPHABET = '0123456789ABCDEFGHJKMNPQRSTUVWXYZ';
export const CODE_LENGTH = 6;

const CONFUSIONS: Record<string, string> = { I: '1', L: '1', O: '0' };

/** Mint a fresh code. */
export function newCode(): string {
  let out = '';
  for (let i = 0; i < CODE_LENGTH; i += 1) {
    out += ALPHABET[randomInt(0, ALPHABET.length)];
  }
  return out;
}

/**
 * A user-typed code in the canonical form parties are keyed by.
 *
 * Spaces and dashes people add for readability are dropped, case is folded up,
 * and the three excluded letters are read as the digit they were meant to be.
 */
export function normalise(code: string): string {
  let out = '';
  for (const char of code.trim().toUpperCase()) {
    if (!/[A-Z0-9]/.test(char)) continue;
    out += CONFUSIONS[char] ?? char;
  }
  return out;
}

/** Whether `code` (already normalised) is a well-formed party code. */
export function isValid(code: string): boolean {
  if (code.length !== CODE_LENGTH) return false;
  for (const char of code) {
    if (!ALPHABET.includes(char)) return false;
  }
  return true;
}
