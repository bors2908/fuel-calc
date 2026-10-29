const integerFormat = new Intl.NumberFormat("ru-RU");
const calendar = new Intl.DateTimeFormat("ru-RU", {
  day: "numeric",
  month: "short",
  year: "numeric",
  timeZone: "UTC",
});
const clock = new Intl.DateTimeFormat("ru-RU", {
  dateStyle: "medium",
  timeStyle: "short",
  timeZone: "Europe/Moscow",
});

// Format digits without rounding backend decimals through a binary floating-point value.
export function number(value: string | null | undefined): string {
  if (value === null || value === undefined) {
    return "—";
  }
  const [integer = "0", fraction = ""] = value.replace(/^-/, "").split(".");
  return `${value.startsWith("-") ? "−" : ""}${integerFormat.format(BigInt(integer))},${fraction.padEnd(2, "0")}`;
}
export function signed(value: string | null): string {
  return value === null
    ? "—"
    : `${value.startsWith("-") ? "−" : "+"}${number(value.replace(/^-/, ""))}`;
}
export function date(value: string | null | undefined): string {
  return value ? calendar.format(new Date(`${value}T00:00:00Z`)) : "Неизвестна";
}
export function timestamp(value: string | null): string {
  return value ? `${clock.format(new Date(value))} мск` : "Неизвестно";
}
export function safeLink(value: string | null): string | undefined {
  if (!value) {
    return undefined;
  }
  try {
    const url = new URL(value);
    return ["http:", "https:"].includes(url.protocol) ? url.href : undefined;
  } catch {
    return undefined;
  }
}
