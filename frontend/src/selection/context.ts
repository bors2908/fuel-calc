import type { Schema } from "../api/types";
import type { PriceSelection, RefuelingSelection } from "./types";

export function checkContext(
  selection: PriceSelection | RefuelingSelection,
  context: Schema<"PricePageContext">,
) {
  const from = "date" in selection ? selection.date : selection.dateFrom;
  const to = "date" in selection ? selection.date : selection.dateTo;
  if (
    context.baseRegion.okatoCode !== selection.baseRegionCode ||
    context.fuelType.code !== selection.fuelType ||
    context.dateFrom !== from ||
    context.dateTo !== to ||
    context.regions
      .map((region) => region.okatoCode)
      .sort()
      .join(",") !== [...selection.regionCodes].sort().join(",")
  ) {
    throw new Error("Inconsistent response context.");
  }
}
