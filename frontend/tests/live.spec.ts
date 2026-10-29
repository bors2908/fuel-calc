import { expect, test } from "@playwright/test";
import { decode } from "../src/api/client";
import { number } from "../src/ui/format";

const backendUrl = process.env.BACKEND_URL ?? "http://127.0.0.1:8080";

test("real BenzUp daily prices match the market series", async ({ page, request }) => {
  test.skip(process.env.FRONTEND_LIVE_TEST !== "true", "Opt in to the real saved archive.");
  const raw: unknown = await (await request.get(`${backendUrl}/api/v1/regions`)).json();
  const catalog = decode("RegionList", raw);
  const region = catalog.data.find((item) => item.okatoCode === "03000000000");
  if (!region) {
    throw new Error("Region missing from real catalog.");
  }
  const query = new URLSearchParams({
    region: region.okatoCode,
    regions: region.okatoCode,
    fuel: "DIESEL",
    from: catalog.meta.serverDate,
    to: catalog.meta.serverDate,
  });
  const pending = page.waitForResponse(
    (response) => response.url().includes("/fuel-prices/overview?") && response.status() === 200,
  );
  await page.goto(`/?${query}`);
  const response = await pending;
  const overviewRaw: unknown = await response.json();
  const overview = decode("PriceOverview", overviewRaw);
  test.skip(
    overview.latestPrice.priceRubPerLiter === null,
    "Requires a saved MARKET snapshot for today; BenzUp has not populated this archive yet.",
  );
  if (overview.latestPrice.priceRubPerLiter === null) {
    throw new Error("The real BenzUp fetch has not populated the daily archive.");
  }
  expect(overview.latestPrice.source).toBe("MARKET");
  await expect(page.locator(".overview")).toContainText("Рыночная цена");
  await expect(page.locator(".chart [data-source=MARKET] circle")).not.toHaveCount(0);
});

test("real saved archive: prices, history, source publication and server calculation", async ({
  page,
  request,
}) => {
  test.skip(
    process.env.FRONTEND_LIVE_TEST !== "true",
    "Opt in with FRONTEND_LIVE_TEST=true and run the backend on port 8080.",
  );
  const catalogResponse = await request.get(`${backendUrl}/api/v1/regions`);
  const raw: unknown = await catalogResponse.json();
  const catalog = decode("RegionList", raw);
  const codes = ["03000000000", "60000000000", "07000000000"].map((okatoCode) => {
    const region = catalog.data.find((item) => item.okatoCode === okatoCode);
    if (!region) {
      throw new Error(`Region missing from real catalog: ${okatoCode}`);
    }
    return region.okatoCode;
  });
  const base = codes[0];
  if (!base) {
    throw new Error("Base region missing.");
  }
  const query = new URLSearchParams({
    region: base,
    regions: codes.join(","),
    fuel: "DIESEL",
    from: "2026-01-01",
    to: catalog.meta.serverDate,
  });
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`/?${query}`);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await expect(page.locator(".overview .price-value > strong").first()).toContainText("₽/л");
  await expect(page.locator("#comparison table")).toHaveCount(1);
  await expect(page.locator('#comparison td[data-source="OFFICIAL"]')).toHaveCount(3);
  await expect(page.locator('#comparison td[data-source="MARKET"]')).toHaveCount(3);
  await page
    .locator(".overview")
    .getByRole("button", { name: "Об источнике и дате" })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toContainText("Загружено в архив");
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("link", { name: "Рассчитать заправку" }).click();
  const calculationResponse = page.waitForResponse((response) =>
    response.url().endsWith("/refueling/calculate"),
  );
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  const response = await calculationResponse;
  expect(response.status()).toBe(200);
  const calculationRaw: unknown = await response.json();
  const calculation = decode("RefuelingCalculation", calculationRaw);
  for (const item of calculation.results) {
    if (item.baseTotalRub !== null) {
      await expect(
        page.locator(`.source-calculation[data-source="${item.context.source}"] .total-value`),
      ).toHaveText(`${number(item.baseTotalRub)} ₽`);
    }
  }
  await page.screenshot({ path: "/private/tmp/fuel-frontend-live-1440.png", fullPage: true });
  await page.setViewportSize({ width: 360, height: 900 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: "/private/tmp/fuel-frontend-live-360.png", fullPage: true });
  expect(errors).toEqual([]);
});

test("real official weekly history renders on a daily axis with source details", async ({
  page,
  request,
}) => {
  test.skip(process.env.FRONTEND_LIVE_TEST !== "true", "Opt in to the real saved archive.");
  const raw: unknown = await (await request.get(`${backendUrl}/api/v1/regions`)).json();
  const catalog = decode("RegionList", raw);
  const code = "03000000000";
  const query = new URLSearchParams({
    region: code,
    regions: code,
    fuel: "DIESEL",
    from: "2026-07-29",
    to: catalog.meta.serverDate,
  });
  const pending = page.waitForResponse(
    (response) =>
      response.url().includes("/trend?") &&
      new URL(response.url()).searchParams.get("source") === "OFFICIAL" &&
      response.ok(),
  );
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`/?${query}`);
  const trendRaw: unknown = await (await pending).json();
  const trend = decode("PriceTrend", trendRaw);
  const count =
    trend.data
      .find((row) => row.region.okatoCode === code)
      ?.points.filter((point) => point.priceRubPerLiter !== null).length ?? 0;
  test.skip(count === 0, "Requires saved official history.");
  await expect(page.locator('.chart [data-source="OFFICIAL"] circle')).toHaveCount(count);
  await page.screenshot({ path: "/private/tmp/fuel-ui-real-1440.png", fullPage: true });
  await page.getByRole("button", { name: "Таблица", exact: true }).click();
  await page
    .getByRole("table")
    .getByRole("button", { name: "Об источнике и дате" })
    .first()
    .click();
  await expect(page.getByRole("dialog")).toContainText("Поставщик данных");
  await expect(page.getByRole("dialog")).toContainText(/Росстат|rustata/);
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await page.getByRole("button", { name: "График", exact: true }).click();
  await page.setViewportSize({ width: 360, height: 900 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: "/private/tmp/fuel-ui-real-360.png", fullPage: true });
  expect(errors).toEqual([]);
});
