import type { Schema } from "../api/types";
import { sourceInfo, sources } from "../sources/model";

export function validatePriceRows(page: Schema<"PricePage">) {
  const expected = page.context.regions
    .map((region) => region.okatoCode)
    .sort()
    .join(",");
  for (const source of sources) {
    const value = page.sources[source];
    if (value.frequency !== sourceInfo[source].frequency) {
      throw new Error("Unexpected source frequency");
    }
    for (const rows of [value.trend, value.comparison.data]) {
      if (
        rows
          .map((row) => row.region.okatoCode)
          .sort()
          .join(",") !== expected
      ) {
        throw new Error("Incomplete or duplicate price regions");
      }
    }
    for (const row of value.trend) {
      const dates = row.points.map((point) => point.date);
      if (new Set(dates).size !== dates.length) {
        throw new Error("Duplicate history date");
      }
      for (const point of row.points) {
        if (point.kind === "FORECAST") {
          if (
            point.observationId !== null ||
            (point.priceRubPerLiter !== null && !point.forecast)
          ) {
            throw new Error("Inconsistent forecast point");
          }
        } else if (
          (point.priceRubPerLiter === null) !== (point.observationId === null) ||
          point.forecast
        ) {
          throw new Error("Inconsistent history observation");
        }
      }
    }
  }
}
