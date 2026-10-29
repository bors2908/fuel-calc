import type { Schema } from "../api/types";

export type Source = Schema<"PriceSource">;
export const sources = ["OFFICIAL", "MARKET"] as const;
export const sourceInfo = {
  OFFICIAL: {
    name: "Официальная цена",
    shortName: "Официальная",
    provider: "Росстат / rustata",
    caption: "Росстат · по неделям",
    frequency: "WEEKLY",
    intervalDays: 7,
  },
  MARKET: {
    name: "Рыночная цена",
    shortName: "Рыночная",
    provider: "BenzUp",
    caption: "BenzUp · по дням",
    frequency: "DAILY",
    intervalDays: 1,
  },
} as const;
