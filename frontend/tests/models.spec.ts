import { expect, test } from "@playwright/test";
import { fuels, meta, pageResponse, regions, selection } from "./fixtures";
import {
  priceSelectionModel,
  refuelingSelectionModel,
  selectionHref,
} from "../src/selection/model";
import { presentPrice, presentTrendPoint } from "../src/prices/presentation";
import { prepareChart, priceOnDate } from "../src/prices/chart/model";
import { chartGeometry, seriesPath } from "../src/prices/chart/geometry";
import { validatePriceRows } from "../src/prices/validate";

const catalog = { regions, fuels, serverDate: meta.serverDate };

test("selection links round-trip and convert only dates when switching pages", () => {
  const priceQuery = new URL(selectionHref(selection, "/"), "http://localhost").searchParams;
  expect(priceSelectionModel.parse(priceQuery, catalog)).toEqual({
    selection,
    requested: true,
    error: null,
  });
  expect([...priceQuery.keys()].sort()).toEqual(["from", "fuel", "region", "regions", "to"]);
  const refuelingQuery = new URL(selectionHref(selection, "/refueling"), "http://localhost")
    .searchParams;
  const refueling = refuelingSelectionModel.parse(refuelingQuery, catalog);
  expect(refueling.error).toBeNull();
  expect(refueling.selection).toEqual({
    baseRegionCode: selection.baseRegionCode,
    regionCodes: selection.regionCodes,
    fuelType: selection.fuelType,
    date: selection.dateTo,
  });
  expect([...refuelingQuery.keys()].sort()).toEqual(["date", "fuel", "region", "regions"]);
  const backQuery = new URL(selectionHref(refueling.selection, "/"), "http://localhost")
    .searchParams;
  expect(priceSelectionModel.parse(backQuery, catalog).selection).toEqual(selection);
  // Old refueling links keep the end of a from/to range.
  expect(refuelingSelectionModel.parse(priceQuery, catalog).selection.date).toBe(selection.dateTo);
});

test("invalid links are reported rather than silently loading default data", () => {
  const query = new URL(selectionHref(selection, "/"), "http://localhost").searchParams;
  for (const [key, value] of [
    ["fuel", "UNKNOWN"],
    ["regions", "23000000000,23000000000"],
    ["from", "2026-02-30"],
    ["to", "2027-01-01"],
  ]) {
    if (key === undefined || value === undefined) {
      throw new Error("Missing test parameter");
    }
    const invalid = new URLSearchParams(query);
    invalid.set(key, value);
    expect(priceSelectionModel.parse(invalid, catalog).error).not.toBeNull();
  }
});

test("price presentation preserves decimal strings, identities and missing values", () => {
  const value = pageResponse();
  const price = value.sources.OFFICIAL.overview.latestPrice;
  const normalized = presentPrice(price);
  expect(normalized).toMatchObject({
    status: "available",
    value: "72.50",
    date: "2026-10-19",
    source: "OFFICIAL",
    observationId: price.observationId,
  });
  expect(
    presentTrendPoint(
      {
        date: "2026-10-20",
        priceRubPerLiter: null,
        observationId: null,
        missingReason: "NO_DATA_IN_RANGE",
      },
      "MARKET",
    ),
  ).toEqual({ status: "missing", date: "2026-10-20", reason: "NO_DATA_IN_RANGE" });
});

test("extracted chart geometry keeps gaps and a single observation", () => {
  const value = pageResponse();
  const model = prepareChart(
    value.context,
    value.sources.OFFICIAL.trend,
    value.sources.MARKET.trend,
  );
  const series = model.series.find((row) => row.source === "OFFICIAL");
  if (!series) {
    throw new Error("Missing official series");
  }
  const coordinates = chartGeometry(model.prices, selection.dateFrom, selection.dateTo, 800);
  // The fixture has a missing official week between its two available points.
  const path = seriesPath(series, coordinates, selection.dateTo);
  expect(path.match(/M/g)).toHaveLength(2);
  expect(path).not.toContain("L");
  expect(path.endsWith(`H${coordinates.x(selection.dateTo)} `)).toBe(true);
  const point = series.points.find((item) => item.priceRubPerLiter !== null);
  if (!point) {
    throw new Error("Missing history point");
  }
  const single = chartGeometry([Number(point.priceRubPerLiter)], point.date, point.date, 800);
  expect(single.x(point.date)).toBe(400);
  const enlarged = chartGeometry(model.prices, selection.dateFrom, selection.dateTo, 800, 32);
  expect(enlarged.left).toBeGreaterThan(coordinates.left);
  expect(enlarged.right).toBeGreaterThan(coordinates.right);
  expect(Number.isFinite(single.y(Number(point.priceRubPerLiter)))).toBe(true);
  expect(seriesPath({ ...series, points: [point] }, single, point.date)).toMatch(/^M[^LH]+$/);
});

test("incomplete, duplicate and inconsistent rows fail before rendering", () => {
  const missing = pageResponse();
  missing.sources.MARKET.comparison.data.pop();
  expect(() => validatePriceRows(missing)).toThrow();
  const duplicate = pageResponse();
  const row = duplicate.sources.OFFICIAL.trend[0];
  if (!row) {
    throw new Error("Missing trend region");
  }
  duplicate.sources.OFFICIAL.trend.push(row);
  expect(() => validatePriceRows(duplicate)).toThrow();
  const inconsistent = pageResponse();
  const point = inconsistent.sources.MARKET.trend[0]?.points[0];
  if (!point) {
    throw new Error("Missing trend point");
  }
  point.observationId = null;
  expect(() => validatePriceRows(inconsistent)).toThrow();
});

test("weekly exact values cover their week without creating daily observations", () => {
  const selected = { ...selection, dateFrom: "2026-10-19", dateTo: "2026-10-25" };
  const value = pageResponse(selected);
  const model = prepareChart(
    value.context,
    value.sources.OFFICIAL.trend,
    value.sources.MARKET.trend,
  );
  const official = model.series.find((row) => row.source === "OFFICIAL");
  if (!official) {
    throw new Error("Missing official series");
  }
  expect(official.points).toHaveLength(1);
  expect(official.points[0]?.date).toBe("2026-10-19");
  expect(official.byDate.size).toBe(1);
  expect(priceOnDate(official, "2026-10-20")).toMatchObject({
    status: "available",
    value: "72.50",
    date: "2026-10-19",
    source: "OFFICIAL",
  });
  expect(priceOnDate(official, "2026-10-25")?.status).toBe("available");
  expect(priceOnDate(official, "2026-10-29")?.status).toBe("available");
});

test("a missing weekly observation stays missing for the whole week", () => {
  const value = pageResponse();
  const model = prepareChart(
    value.context,
    value.sources.OFFICIAL.trend,
    value.sources.MARKET.trend,
  );
  const official = model.series.find((row) => row.source === "OFFICIAL");
  if (!official) {
    throw new Error("Missing official series");
  }
  expect(priceOnDate(official, "2026-10-11")?.status).toBe("available");
  expect(priceOnDate(official, "2026-10-12")?.status).toBe("missing");
  expect(priceOnDate(official, "2026-10-18")?.status).toBe("missing");
  expect(priceOnDate(official, "2026-10-19")?.status).toBe("available");
});
