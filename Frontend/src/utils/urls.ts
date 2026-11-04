export function safeExternalUrl(input: unknown): string | undefined {
  if (typeof input !== 'string') return;
  try {
    const u = new URL(input);
    if (u.protocol === 'https:' || u.protocol === 'http:') return u.href;
  } catch {
    return undefined;
  }
}
