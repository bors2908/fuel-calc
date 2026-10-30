import { expect, test } from "@playwright/test";
import { decode } from "../src/api/client";
import { forecastLimit } from "../src/selection/dates";

const backendUrl = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";
test("real saved archive is returned as one page with original dates", async ({
  page,
  request,
}) => {
  test.skip(process.env.FRONTEND_LIVE_TEST !== "true", "Opt in to the real saved archive.");
  const catalog = decode(
    "RegionList",
    await (await request.get(`${backendUrl}/api/v1/regions`)).json(),
  );
  const region = catalog.data[0];
  if (!region) {
    throw new Error("Real catalog is empty");
  }
  const query = new URLSearchParams({
    region: region.okatoCode,
    regions: region.okatoCode,
    fuel: "DIESEL",
    from: "2026-01-01",
    to: catalog.meta.serverDate,
  });
  const pending = page.waitForResponse(
    (response) => response.url().includes("/api/v1/fuel-prices?") && response.status() === 200,
  );
  await page.goto(`/?${query}`);
  const value = decode("PricePage", await (await pending).json());
  for (const source of ["OFFICIAL", "MARKET"] as const) {
    const count = value.sources[source].trend
      .flatMap((series) => series.points)
      .filter((point) => point.priceRubPerLiter !== null).length;
    const circles = page.locator(`.chart [data-source="${source}"] circle`);
    await expect(page.locator(".chart circle")).toHaveCount(0);
    if (count > 0) {
      await page.locator(`.legend-item[data-source="${source}"]`).hover();
      await expect(circles).toHaveCount(count);
      await page.mouse.move(0, 0);
      await expect(circles).toHaveCount(0);
    }
  }
});

test("real yearly forecast and future refueling share the same weekly prices", async ({
  page,
  request,
}) => {
  test.skip(process.env.FRONTEND_LIVE_TEST !== "true", "Opt in to the real saved archive.");
  const catalog = decode(
    "RegionList",
    await (await request.get(`${backendUrl}/api/v1/regions`)).json(),
  );
  const region = catalog.data.find((item) => item.okatoCode === "03000000000") ?? catalog.data[0];
  if (!region) {
    throw new Error("Real catalog is empty");
  }
  const end = forecastLimit(catalog.meta.serverDate);
  const query = new URLSearchParams({
    region: region.okatoCode,
    regions: region.okatoCode,
    fuel: "DIESEL",
    from: catalog.meta.serverDate,
    to: end,
  });
  const pending = page.waitForResponse(
    (response) => response.url().includes("/api/v1/fuel-prices?") && response.status() === 200,
  );
  await page.goto(`/?${query}`);
  const value = decode("PricePage", await (await pending).json());
  const official =
    value.sources.OFFICIAL.trend[0]?.points.filter((point) => point.kind === "FORECAST") ?? [];
  const market =
    value.sources.MARKET.trend[0]?.points.filter((point) => point.kind === "FORECAST") ?? [];
  expect(official.length).toBeGreaterThanOrEqual(52);
  expect(market.map((point) => point.date)).toEqual(official.map((point) => point.date));
  expect(official.some((point) => point.forecast?.historicalYears.length === 3)).toBe(true);
  for (let index = 0; index < official.length; index++) {
    expect(official[index]?.forecast?.growthPercent).toBe(market[index]?.forecast?.growthPercent);
  }
  const lines = page.locator(".chart .forecast-line");
  for (const line of await lines.all()) {
    const path = await line.getAttribute("d");
    expect(path?.match(/M/g)).toHaveLength(1);
    expect(path).toContain("L");
    expect(path).not.toContain("V");
  }
  await page.getByLabel("Дата на графике — точные значения", { exact: true }).selectOption(end);
  await expect(page.locator(".chart-detail")).toContainText("прогноз на неделю");
  await page.screenshot({ path: "/private/tmp/fuel-forecast-live-desktop.png", fullPage: true });

  const refueling = new URLSearchParams({
    region: region.okatoCode,
    regions: region.okatoCode,
    fuel: "DIESEL",
    date: end,
  });
  await page.goto(`/refueling?${refueling}`);
  const calculation = page.waitForResponse(
    (response) =>
      response.url().includes("/api/v1/refueling/calculate") && response.status() === 200,
  );
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  const result = decode("RefuelingCalculation", await (await calculation).json());
  for (const item of result.results) {
    const expected = value.sources[item.source].trend[0]?.points.at(-1);
    expect(item.frequency).toBe("WEEKLY");
    expect(item.comparison?.data[0]?.price.priceRubPerLiter).toBe(expected?.priceRubPerLiter);
    expect(item.comparison?.data[0]?.price.forecast).toBeDefined();
  }
  await expect(page.locator(".calculation-result")).toContainText("Прогноз на неделю");
});
