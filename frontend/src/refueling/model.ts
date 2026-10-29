export function normalizeVolume(value: string): string | null {
  const normalized = value.trim().replace(",", ".");
  if (!/^(0|[1-9][0-9]{0,6})(\.[0-9]{1,2})?$/.test(normalized)) {
    return null;
  }
  const numeric = Number(normalized);
  return numeric >= 0.01 && numeric <= 1_000_000 ? normalized : null;
}
