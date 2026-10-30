import { decode } from "../src/api/client";
import type { Page } from "@playwright/test";
import type { Schema } from "../src/api/types";
import type { PriceSelection } from "../src/selection/types";

export const regions: Schema<"RegionRef">[] = [
  { okatoCode: "23000000000", name: "Краснодарский край" },
  { okatoCode: "61000000000", name: "Ростовская область" },
  { okatoCode: "26000000000", name: "Ставропольский край" },
  { okatoCode: "01000000000", name: "Республика с очень длинным названием для проверки переноса" },
];
export const fuels: Schema<"FuelTypeRef">[] = [
  { code: "DIESEL", name: "Дизельное топливо" },
  { code: "AI_92", name: "АИ-92" },
  { code: "AI_95", name: "АИ-95" },
];
export const meta = {
  requestId: "test-request",
  generatedAt: "2026-10-29T10:00:00Z",
  serverDate: "2026-10-29",
  dataVersion: "v1",
};
export const selection: PriceSelection = {
  baseRegionCode: "23000000000",
  regionCodes: ["23000000000", "61000000000", "26000000000"],
  fuelType: "DIESEL",
  dateFrom: "2026-07-29",
  dateTo: "2026-10-29",
};
export const period: Schema<"PricePeriod"> = {
  key: "WEEKLY:2026-10-19",
  frequency: "WEEKLY",
  referenceDate: "2026-10-19",
};
const values: Record<string, string> = {
  "23000000000": "72.50",
  "61000000000": "71.80",
  "26000000000": "73.10",
  "01000000000": "72.50",
};
type Source = Schema<"PriceSource">;
export function price(code: string, source: Source = "MARKET"): Schema<"AvailablePriceCell"> {
  const day = source === "OFFICIAL" ? "2026-10-19" : "2026-10-20";
  return {
    priceRubPerLiter: (Number(values[code] ?? "72.50") + (source === "MARKET" ? 1 : 0)).toFixed(2),
    observationId: `observation-${source}-${code}`,
    source,
    missingReason: null,
    period: {
      key: `${source}:${day}`,
      frequency: source === "OFFICIAL" ? "WEEKLY" : "DAILY",
      referenceDate: day,
    },
  };
}
export function context(selected = selection): Schema<"PricePageContext"> {
  const selectedRegions = selected.regionCodes
    .map((code) => regions.find((region) => region.okatoCode === code))
    .filter((region) => region !== undefined);
  const base = selectedRegions.find((region) => region.okatoCode === selected.baseRegionCode);
  const fuel = fuels.find((item) => item.code === selected.fuelType);
  if (!base || !fuel) {
    throw new Error("Invalid fixture selection");
  }
  return {
    baseRegion: base,
    regions: selectedRegions,
    fuelType: fuel,
    dateFrom: selected.dateFrom,
    dateTo: selected.dateTo,
  };
}
export function response(
  selected = selection,
  source: Source = "OFFICIAL",
  mode: "normal" | "empty" | "no-common" = "normal",
): Schema<"PriceSourcePage"> {
  const ctx = context(selected);
  const missing: Schema<"MissingPriceCell"> = {
    priceRubPerLiter: null,
    observationId: null,
    missingReason: "NO_DATA_IN_RANGE",
  };
  const sample = price(selected.baseRegionCode, source);
  const frequency = source === "OFFICIAL" ? "WEEKLY" : "DAILY";
  const comparison: Schema<"Comparison"> = {
    status:
      mode === "empty"
        ? "NO_DATA"
        : mode === "no-common"
          ? "NO_COMMON_PERIOD"
          : ctx.regions.length === 1
            ? "SINGLE_REGION"
            : "AVAILABLE",
    ...(mode === "normal" ? { period: sample.period } : {}),
    sortBy: "PRICE",
    sortOrder: "ASC",
    allPricesEqual: mode === "normal" ? false : null,
    hasNewerObservations: false,
    data: ctx.regions.map((region) => ({
      region,
      isBase: region.okatoCode === selected.baseRegionCode,
      price: mode === "empty" ? missing : price(region.okatoCode, source),
      latestAvailableDate: mode === "empty" ? null : sample.period.referenceDate,
      differenceRubPerLiter:
        mode === "normal" && region.okatoCode !== selected.baseRegionCode
          ? (Number(values[region.okatoCode]) - Number(values[selected.baseRegionCode])).toFixed(2)
          : null,
      isLowest: mode === "normal" && region.okatoCode === "61000000000",
    })),
  };
  const trend: Schema<"RegionPriceSeries">[] = ctx.regions.map((region) => ({
    region,
    status: mode === "empty" ? "NO_DATA" : "PARTIAL",
    points:
      mode === "empty"
        ? []
        : (source === "OFFICIAL"
            ? ["2026-10-05", "2026-10-12", "2026-10-19"]
            : ["2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20", "2026-10-21"]
          )
            .filter(
              (day) =>
                day <= selected.dateTo &&
                (source === "OFFICIAL"
                  ? Date.parse(`${day}T00:00:00Z`) + 6 * 86_400_000 >=
                    Date.parse(`${selected.dateFrom}T00:00:00Z`)
                  : day >= selected.dateFrom),
            )
            .map((day) => {
              const absent = day === (source === "OFFICIAL" ? "2026-10-12" : "2026-10-19");
              const p = price(region.okatoCode, source);
              return {
                kind: "OBSERVED",
                date: day,
                priceRubPerLiter: absent ? null : p.priceRubPerLiter,
                observationId: absent ? null : p.observationId,
                missingReason: absent ? "NO_OBSERVATION_FOR_PERIOD" : null,
              };
            }),
  }));
  return {
    frequency,
    comparison,
    trend,
    overview: {
      latestPrice: mode === "empty" ? missing : sample,
      latestAvailableDate: mode === "empty" ? null : sample.period.referenceDate,
      change: {
        status: mode === "empty" ? "INSUFFICIENT_DATA" : "AVAILABLE",
        startPrice:
          mode === "empty"
            ? missing
            : { ...sample, period: { ...sample.period, referenceDate: "2026-10-05" } },
        absoluteRubPerLiter: mode === "empty" ? null : "1.20",
        percent: mode === "empty" ? null : source === "OFFICIAL" ? "2.10" : "1.68",
      },
      comparison: {
        status: comparison.status,
        ...(comparison.period ? { period: comparison.period } : {}),
        lowestPriceRubPerLiter:
          mode === "normal" && ctx.regions.length > 1
            ? price("61000000000", source).priceRubPerLiter
            : null,
        lowestRegions: ctx.regions.filter((region) => region.okatoCode === "61000000000"),
        hasNewerObservations: false,
      },
    },
  };
}
export function pageResponse(
  selected = selection,
  mode: "normal" | "empty" | "no-common" = "normal",
): Schema<"PricePage"> {
  return decode("PricePage", {
    meta,
    context: context(selected),
    sources: {
      OFFICIAL: response(selected, "OFFICIAL", mode),
      MARKET: response(selected, "MARKET", mode),
    },
    comparisonOrder: "OFFICIAL",
    sourceUpdates: {},
  });
}
export function calculation(selected = selection): Schema<"RefuelingCalculation"> {
  const ctx = context({ ...selected, dateFrom: selected.dateTo });
  return decode("RefuelingCalculation", {
    meta,
    context: ctx,
    volumeLiters: "600.00",
    currency: "RUB",
    sourceUpdates: {},
    results: (["OFFICIAL", "MARKET"] as const).map((source) => {
      const result = response(selected, source);
      return {
        source,
        frequency: result.frequency,
        status: result.comparison.status,
        baseTotalRub: (
          Number(price(selected.baseRegionCode, source).priceRubPerLiter) * 600
        ).toFixed(2),
        comparison: {
          ...result.comparison,
          data: ctx.regions.map((region) => ({
            region,
            price: price(region.okatoCode, source),
            totalRub: (Number(price(region.okatoCode, source).priceRubPerLiter) * 600).toFixed(2),
            differenceRub: "0.00",
            relation: region.okatoCode === selected.baseRegionCode ? "BASE" : "EQUAL",
          })),
        },
      };
    }),
  });
}
export function apiError(type: Schema<"ErrorResponse">["type"]): Schema<"ErrorResponse"> {
  return {
    type,
    message: "Test service error",
    timestamp: meta.generatedAt,
    requestId: meta.requestId,
    details: [],
  };
}
export type RecordedRequest = { path: string; query: URLSearchParams; body: unknown };
export async function mockApi(
  page: Page,
  options: { mode?: "normal" | "empty" | "no-common" } = {},
) {
  const requests: RecordedRequest[] = [];
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.split("/").at(-1) ?? "";
    const body: unknown = request.postDataJSON();
    requests.push({ path, query: url.searchParams, body });
    if (path === "regions" || path === "fuel-types") {
      await route.fulfill({ json: { meta, data: path === "regions" ? regions : fuels } });
      return;
    }
    const selected: PriceSelection = {
      ...selection,
      baseRegionCode: url.searchParams.get("baseRegionCode") ?? selection.baseRegionCode,
      regionCodes: url.searchParams.get("filterRegionCode")?.split(",") ?? selection.regionCodes,
      dateFrom: url.searchParams.get("filterDateFrom") ?? selection.dateFrom,
      dateTo: url.searchParams.get("filterDateTo") ?? selection.dateTo,
    };
    const fuel = url.searchParams.get("filterFuelType");
    if (fuel === "AI_92" || fuel === "AI_95") {
      selected.fuelType = fuel;
    }
    if (path === "fuel-prices") {
      const value = pageResponse(selected, options.mode);
      const source = url.searchParams.get("sortSource") === "MARKET" ? "MARKET" : "OFFICIAL";
      value.comparisonOrder = source;
      const comp = value.sources[source].comparison;
      comp.sortBy = url.searchParams.get("sortBy") === "REGION_NAME" ? "REGION_NAME" : "PRICE";
      comp.sortOrder = url.searchParams.get("sortOrder") === "DESC" ? "DESC" : "ASC";
      comp.data.sort((a, b) => {
        const diff =
          comp.sortBy === "PRICE"
            ? Number(a.price.priceRubPerLiter) - Number(b.price.priceRubPerLiter)
            : a.region.name.localeCompare(b.region.name);
        return (
          (comp.sortOrder === "ASC" ? diff : -diff) ||
          a.region.okatoCode.localeCompare(b.region.okatoCode)
        );
      });
      await route.fulfill({ json: value });
      return;
    }
    if (path === "calculate") {
      const input = decode("RefuelingCalculateRequest", body);
      const inputSelection: PriceSelection = {
        ...selection,
        ...input.selection,
        dateFrom: input.selection.date,
        dateTo: input.selection.date,
      };
      await route.fulfill({ json: calculation(inputSelection) });
      return;
    }
    if (path.startsWith("observation-")) {
      const source = path.includes("OFFICIAL") ? "OFFICIAL" : "MARKET";
      const region = regions.find((item) => path.endsWith(item.okatoCode)) ?? regions[0];
      if (!region) {
        throw new Error("Missing region");
      }
      await route.fulfill({
        json: {
          meta,
          sourceUpdates: {},
          observation: {
            region,
            fuelType: fuels[0],
            price: price(region.okatoCode, source),
            latestAvailableDate: "2026-10-20",
            provider: {
              code: source === "OFFICIAL" ? "ROSSTAT" : "BENZUP",
              name: source === "OFFICIAL" ? "Росстат" : "BenzUp",
              url: "https://example.test",
            },
            publicationDate: null,
            loadedAt: meta.generatedAt,
          },
        },
      });
      return;
    }
    await route.fulfill({ status: 404, json: apiError("ResourceNotFound") });
  });
  return requests;
}
export const selectionUrl = `/?${new URLSearchParams({ region: selection.baseRegionCode, regions: selection.regionCodes.join(","), fuel: selection.fuelType, from: selection.dateFrom, to: selection.dateTo })}`;
export const refuelingUrl = `/refueling?${new URLSearchParams({ region: selection.baseRegionCode, regions: selection.regionCodes.join(","), fuel: selection.fuelType, date: selection.dateTo })}`;
