import type { Schema } from "../../api/types";
import { sourceInfo, sources } from "../../sources/model";
import type { Source } from "../../sources/model";
import type { PresentedPrice } from "../presentation";
import { presentTrendPoint } from "../presentation";

export type ChartSeries = {
  key: string;
  region: Schema<"RegionRef">;
  source: Source;
  index: number;
  points: Schema<"TrendPoint">[];
  byDate: Map<string, PresentedPrice>;
};
export type ChartGroup = { region: Schema<"RegionRef">; index: number; series: ChartSeries[] };
export function prepareChart(
  context: Schema<"PricePageContext">,
  official: Schema<"RegionPriceSeries">[],
  market: Schema<"RegionPriceSeries">[],
) {
  const rows = {
    OFFICIAL: new Map(official.map((row) => [row.region.okatoCode, row])),
    MARKET: new Map(market.map((row) => [row.region.okatoCode, row])),
  };
  const groups: ChartGroup[] = context.regions.map((region, index) => ({
    region,
    index,
    series: sources.map((source) => {
      const row = rows[source].get(region.okatoCode);
      if (!row) {
        throw new Error("History is missing a selected region");
      }
      return {
        key: `${region.okatoCode}:${source}`,
        region,
        source,
        index,
        points: row.points,
        byDate: new Map(row.points.map((point) => [point.date, presentTrendPoint(point, source)])),
      };
    }),
  }));
  const series = groups.flatMap((group) => group.series);
  const dates = [
    ...new Set(
      series.flatMap((item) =>
        item.points
          .filter((point) => point.date >= context.dateFrom && point.date <= context.dateTo)
          .map((point) => point.date),
      ),
    ),
  ].sort();
  const prices = series.flatMap((item) =>
    item.points.flatMap((point) =>
      point.priceRubPerLiter === null ? [] : [Number(point.priceRubPerLiter)],
    ),
  );
  const detailDates = [
    ...new Set([
      ...dates,
      ...[context.dateFrom, context.dateTo].filter((day) =>
        series.some((item) => {
          const point = latestPoint(item.points, day);
          return (
            point?.kind === "FORECAST" && Date.parse(day) - Date.parse(point.date) < 7 * 86_400_000
          );
        }),
      ),
    ]),
  ].sort();
  return {
    groups,
    series,
    dates,
    detailDates,
    prices,
    regionNames: context.regions.map((region) => region.name).join(", "),
  };
}

export function priceOnDate(series: ChartSeries, day: string): PresentedPrice | undefined {
  const exact = series.byDate.get(day);
  if (exact) {
    return exact;
  }
  const selected = Date.parse(`${day}T00:00:00Z`);
  const latest = latestPoint(series.points);
  const point = latestPoint(series.points, day);
  if (!point) {
    return undefined;
  }
  const age = (selected - Date.parse(`${point.date}T00:00:00Z`)) / 86_400_000;
  if (point.kind === "FORECAST") {
    return age < 7 ? presentTrendPoint(point, series.source) : undefined;
  }
  if (series.source !== "OFFICIAL") {
    return undefined;
  }
  const isLastAvailable = point.date === latest?.date && point.priceRubPerLiter !== null;
  return age < sourceInfo.OFFICIAL.intervalDays || isLastAvailable
    ? presentTrendPoint(point, series.source)
    : undefined;
}

function latestPoint(points: Schema<"TrendPoint">[], until?: string) {
  return points.reduce<Schema<"TrendPoint"> | undefined>((latest, point) => {
    return (!until || point.date <= until) && (!latest || point.date > latest.date)
      ? point
      : latest;
  }, undefined);
}
