import type { Schema } from "../api/types";

export function filterRegions(regions: Schema<"RegionRef">[], query: string) {
  const search = query.trim().toLocaleLowerCase("ru");
  return regions.filter((region) => region.name.toLocaleLowerCase("ru").includes(search));
}
