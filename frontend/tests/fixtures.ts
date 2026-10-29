import { decode } from "../src/api/client";
import type { Page } from "@playwright/test";
import type { Schema, Selection } from "../src/api/types";

export const regions: Schema<"RegionRef">[] = [
  { okatoCode: "23000000000", name: "Краснодарский край" },
  { okatoCode: "61000000000", name: "Ростовская область" },
  { okatoCode: "26000000000", name: "Ставропольский край" },
  { okatoCode: "01000000000", name: "Республика с очень длинным названием для проверки переноса" },
];
export const fuels: Schema<"FuelTypeRef">[] = [
  { code: "DIESEL", name: "Дизельное топливо", rosstatCode: "7804" },
  { code: "AI_92", name: "АИ-92", rosstatCode: "7802" },
  { code: "AI_95", name: "АИ-95", rosstatCode: "7803" },
];
export const meta = {
  requestId: "test-request",
  generatedAt: "2026-10-29T10:00:00Z",
  serverDate: "2026-10-29",
  dataVersion: "v1",
};
export const selection: Selection = {
  baseRegionCode: "23000000000",
  regionCodes: ["23000000000", "61000000000", "26000000000"],
  fuelType: "DIESEL",
  frequency: "DAILY",
  source: "MARKET",
  dateFrom: "2026-07-29",
  dateTo: "2026-10-29",
};
export const period: Schema<"PricePeriod"> = {
  key: "2026-10-19",
  frequency: "WEEKLY",
  referenceDate: "2026-10-19",
  label: "19 октября 2026",
  startDate: null,
  endDate: null,
};
const prices: Record<string, string> = {
  "23000000000": "72.50",
  "61000000000": "71.80",
  "26000000000": "73.10",
  "01000000000": "72.50",
};
export function price(
  code: string,
  source: Schema<"PriceSource"> = "MARKET",
): Schema<"AvailablePriceCell"> {
  return {
    priceRubPerLiter: (Number(prices[code] ?? "72.50") + (source === "MARKET" ? 1 : 0)).toFixed(2),
    observationId: `observation-${source}-${code}`,
    period: {
      ...period,
      frequency: source === "MARKET" ? "DAILY" : "WEEKLY",
      referenceDate: source === "MARKET" ? "2026-10-20" : "2026-10-19",
    },
    missingReason: null,
    source,
  };
}
export function responses(
  selected = selection,
  version = "v1",
  mode: "normal" | "empty" | "no-common" = "normal",
) {
  const selectedRegions = selected.regionCodes
    .map((code) => regions.find((region) => region.okatoCode === code) ?? regions[0])
    .filter((region) => region !== undefined);
  const base =
    selectedRegions.find((region) => region.okatoCode === selected.baseRegionCode) ?? regions[0];
  if (!base) {
    throw new Error("Fixture base region missing.");
  }
  const context: Schema<"PriceContext"> = {
    key: JSON.stringify(selected),
    baseRegion: base,
    regions: selectedRegions,
    fuelType: fuels.find((fuel) => fuel.code === selected.fuelType) ?? {
      code: "DIESEL",
      name: "Дизель",
      rosstatCode: "7804",
    },
    dateFrom: selected.dateFrom,
    dateTo: selected.dateTo,
    frequency: selected.frequency,
    source: selected.source,
    basis: "REGIONAL_CONSUMER_AVERAGE",
    unit: "RUB/L",
  };
  const selectedPeriod: Schema<"PricePeriod"> = {
    ...period,
    frequency: selected.frequency,
    referenceDate: selected.source === "OFFICIAL" ? "2026-10-19" : "2026-10-20",
    label: selected.source === "OFFICIAL" ? "19 октября 2026" : "20 октября 2026",
  };
  const common = { meta: { ...meta, dataVersion: version }, context, sourceUpdates: {} };
  const missing: Schema<"MissingPriceCell"> = {
    priceRubPerLiter: null,
    observationId: null,
    missingReason: "NO_DATA_IN_RANGE",
  };
  const comparison: Schema<"Comparison"> = {
    status:
      mode === "empty"
        ? "NO_DATA"
        : mode === "no-common"
          ? "NO_COMMON_PERIOD"
          : selectedRegions.length === 1
            ? "SINGLE_REGION"
            : "AVAILABLE",
    snapshotId: mode === "normal" ? `snapshot-${version}` : null,
    ...(mode === "normal" ? { period: selectedPeriod } : {}),
    sortBy: mode === "normal" ? "PRICE" : "REGION_NAME",
    sortOrder: "ASC",
    allPricesEqual: false,
    hasNewerObservations: false,
    data: selectedRegions.map((region) => ({
      region,
      isBase: region.okatoCode === base.okatoCode,
      price: mode === "empty" ? missing : price(region.okatoCode, selected.source),
      latestAvailableDate: mode === "empty" ? null : period.referenceDate,
      differenceRubPerLiter:
        mode !== "normal" || region.okatoCode === base.okatoCode
          ? null
          : (
              Number(prices[region.okatoCode] ?? "72.50") -
              Number(prices[base.okatoCode] ?? "72.50")
            ).toFixed(2),
      isLowest: region.okatoCode === "61000000000",
      isHighest: region.okatoCode === "26000000000",
    })),
  };
  const overview: Schema<"PriceOverview"> = {
    ...common,
    latestPrice: mode === "empty" ? missing : price(base.okatoCode, selected.source),
    latestAvailableDate: mode === "empty" ? null : period.referenceDate,
    change: {
      status: mode === "empty" ? "INSUFFICIENT_DATA" : "AVAILABLE",
      startPrice: mode === "empty" ? missing : price(base.okatoCode, selected.source),
      absoluteRubPerLiter: mode === "empty" ? null : "1.20",
      percent: mode === "empty" ? null : selected.source === "OFFICIAL" ? "2.10" : "1.68",
    },
    comparison: {
      status: comparison.status,
      snapshotId: comparison.snapshotId,
      ...(mode === "normal" ? { period: selectedPeriod } : {}),
      basePriceRubPerLiter: mode === "normal" ? "72.50" : null,
      lowestPriceRubPerLiter:
        selectedRegions.length > 1 && mode === "normal"
          ? selected.source === "OFFICIAL"
            ? "71.80"
            : "72.80"
          : null,
      lowestRegions: selectedRegions.filter((region) => region.okatoCode === "61000000000"),
      lowestDifferenceRubPerLiter: "-0.70",
      hasNewerObservations: false,
    },
  };
  const trend: Schema<"PriceTrend"> = {
    ...common,
    data: selectedRegions.map((region) => ({
      region,
      status: mode === "empty" ? "NO_DATA" : "PARTIAL",
      firstAvailableDate: "2026-01-01",
      lastAvailableDate: period.referenceDate,
      points:
        mode === "empty"
          ? []
          : (selected.source === "OFFICIAL"
              ? ["2026-10-05", "2026-10-12", "2026-10-19"]
              : ["2026-10-17", "2026-10-18", "2026-10-19", "2026-10-20", "2026-10-21"]
            ).map((day, index) => ({
              ...(index === (selected.source === "OFFICIAL" ? 1 : 2)
                ? missing
                : price(region.okatoCode, selected.source)),
              period: {
                ...selectedPeriod,
                key: `${selected.source}:${day}`,
                referenceDate: day,
                label: day,
              },
            })),
    })),
  };
  const totals: Record<string, string> = {
    "23000000000": "43500.00",
    "61000000000": "43080.00",
    "26000000000": "43860.00",
    "01000000000": "43500.00",
  };
  const calculation: Schema<"RefuelingCalculation"> = {
    meta: common.meta,
    volumeLiters: "600",
    currency: "RUB",
    sourceUpdates: {},
    results: (["OFFICIAL", "MARKET"] as const).map((source) => ({
      context: {
        ...context,
        dateFrom: selected.dateTo,
        dateTo: selected.dateTo,
        source,
        frequency: source === "OFFICIAL" ? "WEEKLY" : "DAILY",
      },
      status: comparison.status,
      baseTotalRub:
        mode === "normal"
          ? (
              Number(totals[base.okatoCode] ?? "43500.00") + (source === "MARKET" ? 600 : 0)
            ).toFixed(2)
          : null,
      snapshotChanged: null,
      comparison:
        mode === "normal"
          ? {
              ...comparison,
              period: { ...period, frequency: source === "OFFICIAL" ? "WEEKLY" : "DAILY" },
              data: selectedRegions.map((region) => ({
                region,
                price: price(region.okatoCode, source),
                latestAvailableDate: period.referenceDate,
                differenceRubPerLiter: null,
                isLowest: false,
                isHighest: false,
                totalRub: (
                  Number(totals[region.okatoCode] ?? "43500.00") + (source === "MARKET" ? 600 : 0)
                ).toFixed(2),
                differenceRub:
                  region.okatoCode === base.okatoCode
                    ? "0.00"
                    : region.okatoCode === "61000000000"
                      ? "-420.00"
                      : "360.00",
                relation:
                  region.okatoCode === base.okatoCode
                    ? "BASE"
                    : region.okatoCode === "61000000000"
                      ? "LOWER"
                      : "HIGHER",
              })),
            }
          : null,
    })),
  };

  return { overview, trend, comparison: { ...common, comparison }, calculation };
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
  options: {
    mode?: "normal" | "empty" | "no-common";
    conflicts?: number;
    delayBase?: string;
    failTrend?: boolean;
    changedCalculation?: boolean;
  } = {},
) {
  const requests: RecordedRequest[] = [];
  let conflicts = options.conflicts ?? 0;
  await page.route("**/api/v1/**", async (route) => {
    const request = route.request();
    const url = new URL(request.url());
    const path = url.pathname.split("/").at(-1) ?? "";
    const body: unknown = request.postDataJSON();
    requests.push({
      path:
        (path === "overview" || path === "comparison") &&
        url.searchParams.get("source") === "OFFICIAL"
          ? `official-${path}`
          : path,
      query: url.searchParams,
      body,
    });
    if (path === "regions" || path === "fuel-types") {
      await route.fulfill({ json: { meta, data: path === "regions" ? regions : fuels } });
      return;
    }
    if (path.startsWith("observation-")) {
      await route.fulfill({
        json: {
          meta,
          sourceUpdates: {},
          observation: {
            region: regions[0],
            fuelType: fuels[0],
            basis: "REGIONAL_CONSUMER_AVERAGE",
            unit: "RUB/L",
            price: price(selection.baseRegionCode),
            latestAvailableDate: period.referenceDate,
            provider: {
              code: "BENZUP",
              name: "BenzUp",
              url: "https://api.omt-consult.ru/index/regional",
            },
            publicationDate: "2026-10-20",
            loadedAt: meta.generatedAt,
          },
        },
      });
      return;
    }
    if (path === "trend" && conflicts > 0) {
      conflicts--;
      await route.fulfill({ status: 409, json: apiError("DataVersionChanged") });
      return;
    }
    if (path === "trend" && options.failTrend) {
      await route.fulfill({ status: 503, json: apiError("ServiceUnavailable") });
      return;
    }
    const query = url.searchParams;
    const selected = {
      ...selection,
      baseRegionCode: query.get("baseRegionCode") ?? selection.baseRegionCode,
      regionCodes: query.get("filterRegionCode")?.split(",") ?? selection.regionCodes,
      dateFrom: query.get("filterDateFrom") ?? selection.dateFrom,
      dateTo: query.get("filterDateTo") ?? selection.dateTo,
    };
    const source = query.get("source");
    if (source === "OFFICIAL" || source === "MARKET") {
      selected.source = source;
    }
    const frequency = query.get("frequency");
    if (frequency === "DAILY" || frequency === "WEEKLY") {
      selected.frequency = frequency;
    }
    const fuel = query.get("filterFuelType");
    if (fuel === "AI_92" || fuel === "AI_95") {
      selected.fuelType = fuel;
    }
    if (options.delayBase === selected.baseRegionCode) {
      await new Promise((resolve) => setTimeout(resolve, 300));
    }
    const version = query.get("dataVersion") ?? "v1";
    const data = responses(selected, version, options.mode);
    if (path === "calculate") {
      const input = decode("RefuelingCalculateRequest", body);
      const result = responses(
        {
          ...selection,
          ...input.selection,
          dateFrom: input.selection.date,
          dateTo: input.selection.date,
        },
        options.changedCalculation ? "v2" : "v1",
        options.mode,
      ).calculation;
      result.results.forEach((item) => {
        item.snapshotChanged = input.expectedSnapshotIds?.[item.context.source]
          ? (options.changedCalculation ?? false)
          : null;
      });
      await route.fulfill({ json: result });
    } else if (path === "overview") {
      await route.fulfill({ json: data.overview });
    } else if (path === "trend") {
      await route.fulfill({ json: data.trend });
    } else if (path === "comparison") {
      const info = data.comparison.comparison;
      const order = query.get("sortOrder");
      const field = query.get("sortBy");
      if (options.mode !== "empty" && options.mode !== "no-common") {
        if (order === "DESC" || order === "ASC") {
          info.sortOrder = order;
        }
        if (field === "PRICE" || field === "REGION_NAME") {
          info.sortBy = field;
        }
      }
      info.data.sort((a, b) => {
        const primary =
          info.sortBy === "PRICE"
            ? Number(a.price.priceRubPerLiter) - Number(b.price.priceRubPerLiter)
            : a.region.name.localeCompare(b.region.name, "ru");
        return (
          (info.sortOrder === "DESC" ? -primary : primary) ||
          a.region.name.localeCompare(b.region.name, "ru") ||
          a.region.okatoCode.localeCompare(b.region.okatoCode)
        );
      });
      await route.fulfill({ json: data.comparison });
    } else {
      await route.fulfill({ status: 404, json: apiError("ResourceNotFound") });
    }
  });
  return requests;
}
export const selectionUrl = `/?${new URLSearchParams({ region: selection.baseRegionCode, regions: selection.regionCodes.join(","), fuel: "DIESEL", from: selection.dateFrom, to: selection.dateTo })}`;

export const refuelingUrl = `/refueling?${new URLSearchParams({ region: selection.baseRegionCode, regions: selection.regionCodes.join(","), fuel: "DIESEL", date: selection.dateTo })}`;
