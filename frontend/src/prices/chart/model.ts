import type { Schema } from "../../api/types";
import { sources } from "../../sources/model";
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
    ...new Set(series.flatMap((item) => item.points.map((point) => point.date))),
  ].sort();
  const prices = series.flatMap((item) =>
    item.points.flatMap((point) =>
      point.priceRubPerLiter === null ? [] : [Number(point.priceRubPerLiter)],
    ),
  );
  return {
    groups,
    series,
    dates,
    prices,
    regionNames: context.regions.map((region) => region.name).join(", "),
  };
}
