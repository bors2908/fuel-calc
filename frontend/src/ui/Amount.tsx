import { number } from "./format";

export function Amount({ value, unit }: { value: string | null; unit: "₽" | "₽/л" }) {
  return (
    <>
      {number(value)} <small>{unit}</small>
    </>
  );
}
