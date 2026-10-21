import Sqids from 'sqids';

/**
 * Obfuscates the numeric report id that appears in the URL, so a user cannot walk through
 * other people's reports by editing /incident-reports/291 into /292.
 *
 * This is obfuscation, NOT authorisation. The API still speaks numeric ids and still lets any
 * authenticated user fetch any report, so anyone with the dev tools open can enumerate exactly
 * as before. Anything that must actually be restricted belongs behind a check on the server.
 *
 * Encoding is reversible and stable: the same id always yields the same string, so links can be
 * bookmarked and shared.
 */
const sqids = new Sqids({ minLength: 8 });

export function encodeReportId(id: number | string): string {
  const n = Number(id);
  return Number.isFinite(n) && n > 0 ? sqids.encode([n]) : String(id);
}

/**
 * Accepts either an encoded id or a bare number.
 *
 * The plain-number path is not a leftover: notification emails and in-app alerts are built on the
 * server, which knows nothing about this encoding and links straight to the numeric id. Those
 * links must keep working.
 *
 * Returns null when the value decodes to nothing, so the caller can show "not found" instead of
 * firing a request for NaN.
 */
export function decodeReportId(value: string | undefined): number | null {
  if (!value) return null;

  if (/^\d+$/.test(value)) return Number(value);

  const [id] = sqids.decode(value);
  return id ?? null;
}
