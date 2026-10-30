import type { PriceCell, Schema } from "../api/types";
import type { Source } from "../sources/model";

export type PresentedPrice =
  | {
      status: "available";
      value: string;
      date: string;
      source: Source;
      observationId: string | null;
      forecast?: Schema<"ForecastBasis">;
    }
  | { status: "missing"; date: string | null; reason: Schema<"MissingPriceReason"> | null };
export function presentPrice(price: PriceCell): PresentedPrice {
  if (
    price.priceRubPerLiter !== null &&
    (price.observationId === null) !== Boolean(price.forecast)
  ) {
    throw new Error("Price identity and forecast basis do not match");
  }
  return price.priceRubPerLiter === null
    ? { status: "missing", date: price.period?.referenceDate ?? null, reason: price.missingReason }
    : {
        status: "available",
        value: price.priceRubPerLiter,
        date: price.period.referenceDate,
        source: price.source,
        observationId: price.observationId,
        ...(price.forecast ? { forecast: price.forecast } : {}),
      };
}
export function presentTrendPoint(point: Schema<"TrendPoint">, source: Source): PresentedPrice {
  if (point.priceRubPerLiter === null) {
    return { status: "missing", date: point.date, reason: point.missingReason };
  }
  if (point.observationId === null && !point.forecast) {
    throw new Error("Price observation identifier is missing");
  }
  return {
    status: "available",
    value: point.priceRubPerLiter,
    date: point.date,
    source,
    observationId: point.observationId,
    ...(point.forecast ? { forecast: point.forecast } : {}),
  };
}
