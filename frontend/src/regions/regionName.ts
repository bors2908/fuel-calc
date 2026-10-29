import type { Schema } from "../api/types";

export function regionName(region: Schema<"RegionRef">): string {
  return region.name;
}
