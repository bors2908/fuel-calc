import type { Page } from "@playwright/test";
import type { Schema, Selection } from "../src/api/types";

export const regions = [
  { code: "23000000000", name: "Краснодарский край" },
  { code: "61000000000", name: "Ростовская область" },
  { code: "26000000000", name: "Ставропольский край" },
  { code: "01000000000", name: "Республика с очень длинным названием для проверки переноса" },
];
export const fuels: Schema<"FuelTypeRef">[] = [
  { code: "DIESEL", name: "Дизель" },
  { code: "AI_92", name: "АИ-92" },
  { code: "AI_95", name: "АИ-95" },
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
  frequency: "WEEKLY",
  preferredSource: "ROSSTAT",
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
  source: Schema<"PriceSource"> = "ROSSTAT",
): Schema<"AvailablePriceCell"> {
  return {
    priceRubPerLiter: prices[code] ?? "72.50",
    observationId: `observation-${code}`,
    period,
    missingReason: null,
    source: {
      provider: { code: source, name: source === "RUSTATA" ? "rustata" : "Росстат" },
      updateKey: `${source}-WEEKLY`,
      aggregationMethod: "OFFICIAL_STATISTICS",
    },
  };
}
export function responses(
  selected = selection,
  version = "v1",
  mode: "normal" | "empty" | "no-common" = "normal",
) {
  const selectedRegions = selected.regionCodes
    .map((code) => regions.find((region) => region.code === code) ?? regions[0])
    .filter((region) => region !== undefined);
  const base =
    selectedRegions.find((region) => region.code === selected.baseRegionCode) ?? regions[0];
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
    },
    dateFrom: selected.dateFrom,
    dateTo: selected.dateTo,
    frequency: selected.frequency,
    preferredSource: selected.preferredSource,
    fallbackOrder: [
      selected.preferredSource,
      ...(["ROSSTAT", "RUSTATA", "WHEREFUEL"] as const).filter(
        (source) => source !== selected.preferredSource,
      ),
    ],
    basis: "REGIONAL_CONSUMER_AVERAGE",
    unit: "RUB/L",
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
    ...(mode === "normal" ? { period } : {}),
    sortBy: "PRICE",
    sortOrder: "ASC",
    allPricesEqual: false,
    hasNewerObservations: false,
    data: selectedRegions.map((region) => ({
      region,
      isBase: region.code === base.code,
      price:
        mode === "empty"
          ? missing
          : price(region.code, region.code === "61000000000" ? "RUSTATA" : "ROSSTAT"),
      latestAvailableDate: period.referenceDate,
      differenceRubPerLiter: mode !== "normal" || region.code === base.code ? null : "-0.70",
      isLowest: region.code === "61000000000",
      isHighest: region.code === "26000000000",
    })),
  };
  const overview: Schema<"PriceOverview"> = {
    ...common,
    latestPrice: mode === "empty" ? missing : price(base.code),
    latestAvailableDate: period.referenceDate,
    change: {
      status: "AVAILABLE",
      startPrice: price(base.code),
      absoluteRubPerLiter: "1.20",
      percent: "1.68",
    },
    comparison: {
      status: comparison.status,
      snapshotId: comparison.snapshotId,
      ...(mode === "normal" ? { period } : {}),
      basePriceRubPerLiter: mode === "normal" ? "72.50" : null,
      lowestPriceRubPerLiter: selectedRegions.length > 1 && mode === "normal" ? "71.80" : null,
      lowestRegions: selectedRegions.filter((region) => region.code === "61000000000"),
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
          : [
              {
                ...price(region.code),
                period: {
                  ...period,
                  key: "first",
                  referenceDate: "2026-10-05",
                  label: "5 октября 2026",
                },
              },
              {
                priceRubPerLiter: null,
                observationId: null,
                missingReason: "NO_OBSERVATION_FOR_PERIOD",
                period: {
                  ...period,
                  key: "gap",
                  referenceDate: "2026-10-12",
                  label: "12 октября 2026",
                },
              },
              price(region.code, "RUSTATA"),
            ],
    })),
  };
  const totals: Record<string, string> = {
    "23000000000": "43500.00",
    "61000000000": "43080.00",
    "26000000000": "43860.00",
    "01000000000": "43500.00",
  };
  const calculation: Schema<"RefuelingCalculation"> = {
    ...common,
    volumeLiters: "600",
    currency: "RUB",
    baseTotalRub: "43500.00",
    snapshotChanged: false,
    comparison: {
      ...comparison,
      data: selectedRegions.map((region) => ({
        region,
        price: price(region.code),
        latestAvailableDate: period.referenceDate,
        differenceRubPerLiter: null,
        isLowest: false,
        isHighest: false,
        totalRub: totals[region.code] ?? "43500.00",
        differenceRub:
          region.code === base.code ? "0.00" : region.code === "61000000000" ? "-420.00" : "360.00",
        relation:
          region.code === base.code ? "BASE" : region.code === "61000000000" ? "LOWER" : "HIGHER",
      })),
    },
  };
  const coverage: Schema<"CoverageList"> = {
    meta: common.meta,
    basis: context.basis,
    preferredSource: context.preferredSource,
    fallbackOrder: context.fallbackOrder,
    sourceUpdates: {},
    data: selectedRegions.flatMap((region) =>
      (["WEEKLY", "MONTHLY"] as const).map((frequency) => ({
        region,
        fuelType: context.fuelType,
        frequency,
        firstAvailableDate: "2026-01-01",
        lastAvailableDate: period.referenceDate,
        observationCount: 22,
      })),
    ),
  };
  return { overview, trend, comparison: { ...common, comparison }, calculation, coverage };
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
    requests.push({ path, query: url.searchParams, body });
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
            publisher: { code: "ROSSTAT", name: "Росстат", url: "https://rosstat.gov.ru/" },
            providerUrl: "https://rosstat.gov.ru/",
            datasetId: "test-dataset",
            publicationId: null,
            publicationDate: "2026-10-20",
            publishedAt: null,
            loadedAt: meta.generatedAt,
            publicationUrl: null,
            attribution: "Тестовая публикация",
            methodologyUrl: null,
            note: null,
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
    const source = query.get("preferredSource");
    if (source === "ROSSTAT" || source === "RUSTATA" || source === "WHEREFUEL") {
      selected.preferredSource = source;
    }
    const frequency = query.get("frequency");
    if (frequency === "MONTHLY") {
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
      const result = responses(selection, options.changedCalculation ? "v2" : "v1").calculation;
      result.snapshotChanged = options.changedCalculation ?? false;
      await route.fulfill({ json: result });
    } else if (path === "overview") {
      await route.fulfill({ json: data.overview });
    } else if (path === "trend") {
      await route.fulfill({ json: data.trend });
    } else if (path === "coverage") {
      await route.fulfill({ json: data.coverage });
    } else if (path === "comparison") {
      const order = query.get("sortOrder");
      if (order === "DESC") {
        data.comparison.comparison.sortOrder = order;
      }
      await route.fulfill({ json: data.comparison });
    } else {
      await route.fulfill({ status: 404, json: apiError("ResourceNotFound") });
    }
  });
  return requests;
}
export const selectionUrl = `/?${new URLSearchParams({ region: selection.baseRegionCode, regions: selection.regionCodes.join(","), fuel: "DIESEL", frequency: "WEEKLY", source: "ROSSTAT", from: selection.dateFrom, to: selection.dateTo })}`;
