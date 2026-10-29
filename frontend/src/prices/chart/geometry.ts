import type { ChartSeries } from "./model";
import { sourceInfo } from "../../sources/model";

const dayMs = 86_400_000;
export function chartGeometry(
  prices: number[],
  from: string,
  to: string,
  width: number,
  fontSize = 16,
) {
  const left = Math.max(64, fontSize * 3.5);
  const right = Math.max(36, fontSize * 2.75);
  const min = prices.length > 0 ? Math.min(...prices) : 0;
  const max = prices.length > 0 ? Math.max(...prices) : 1;
  const padding = Math.max((max - min) * 0.15, 0.2);
  const lower = Math.max(0, min - padding);
  const upper = max + padding;
  const first = Date.parse(from);
  const last = Date.parse(to);
  const x = (day: string) =>
    first === last
      ? width / 2
      : left + ((Date.parse(day) - first) / (last - first)) * (width - left - right);
  const y = (price: number) => 255 - ((price - lower) / (upper - lower)) * 210;
  const axisDates = [
    ...new Set([
      from,
      new Date(first + Math.floor((last - first) / dayMs / 2) * dayMs).toISOString().slice(0, 10),
      to,
    ]),
  ];
  return { lower, upper, left, right, x, y, axisDates };
}
export function seriesPath(
  series: ChartSeries,
  coordinates: Pick<ReturnType<typeof chartGeometry>, "x" | "y">,
): string {
  let path = "";
  let previous: number | null = null;
  for (const point of series.points) {
    if (point.priceRubPerLiter === null) {
      previous = null;
    } else {
      const time = Date.parse(point.date);
      const connected =
        previous !== null && time - previous <= sourceInfo[series.source].intervalDays * dayMs;
      path += `${connected ? "L" : "M"}${coordinates.x(point.date)},${coordinates.y(Number(point.priceRubPerLiter))} `;
      previous = time;
    }
  }
  return path;
}
