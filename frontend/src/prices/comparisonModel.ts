import type { Schema } from "../api/types";
import { sourceInfo, sources } from "../sources/model";
import type { Source } from "../sources/model";

export type ComparisonColumn = { source: Source; title: string; block: Schema<"Comparison"> };
export type ComparisonRegion = {
  region: Schema<"RegionRef">;
  prices: Record<Source, Schema<"ComparisonRow">>;
};
export function comparisonModel(
  official: Schema<"Comparison">,
  market: Schema<"Comparison">,
  ordering: Source,
) {
  const blocks = { OFFICIAL: official, MARKET: market };
  const columns = sources.map((source) => ({
    source,
    title: sourceInfo[source].shortName,
    block: blocks[source],
  }));
  const indices = {
    OFFICIAL: new Map(official.data.map((row) => [row.region.okatoCode, row])),
    MARKET: new Map(market.data.map((row) => [row.region.okatoCode, row])),
  };
  const rows = blocks[ordering].data.map(({ region }) => {
    const officialRow = indices.OFFICIAL.get(region.okatoCode);
    const marketRow = indices.MARKET.get(region.okatoCode);
    if (!officialRow || !marketRow) {
      throw new Error("Incomplete comparison regions");
    }
    return { region, prices: { OFFICIAL: officialRow, MARKET: marketRow } };
  });
  return {
    columns,
    rows,
    sorting: {
      source: ordering,
      sortBy: blocks[ordering].sortBy,
      sortOrder: blocks[ordering].sortOrder,
    },
  };
}
