import { expect, test } from "@playwright/test";
import { decode } from "../src/api/client";
import { number } from "../src/ui/format";

test("real saved archive: prices, history, source publication and server calculation", async ({
  page,
  request,
}) => {
  test.skip(
    process.env.FRONTEND_LIVE_TEST !== "true",
    "Opt in with FRONTEND_LIVE_TEST=true and run the backend on port 8080.",
  );
  const catalogResponse = await request.get("http://127.0.0.1:8080/api/v1/regions");
  const raw: unknown = await catalogResponse.json();
  const catalog = decode("RegionList", raw);
  const codes = ["Краснодарский край", "Ростовская область", "Ставропольский край"].map((name) => {
    const region = catalog.data.find((item) => item.name === name);
    if (!region) {
      throw new Error(`Region missing from real catalog: ${name}`);
    }
    return region.code;
  });
  const base = codes[0];
  if (!base) {
    throw new Error("Base region missing.");
  }
  const query = new URLSearchParams({
    region: base,
    regions: codes.join(","),
    fuel: "DIESEL",
    frequency: "WEEKLY",
    source: "ROSSTAT",
    from: "2026-01-01",
    to: catalog.meta.serverDate,
  });
  const errors: string[] = [];
  page.on("pageerror", (error) => errors.push(error.message));
  await page.goto(`/?${query}`);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await expect(page.locator(".overview .price-value > strong")).toContainText("₽/л");
  await page.locator(".overview").getByRole("button", { name: "Об источнике и дате" }).click();
  await expect(page.getByRole("dialog")).toContainText("Загружено в архив");
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  const calculationResponse = page.waitForResponse((response) =>
    response.url().endsWith("/refueling/calculate"),
  );
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  const response = await calculationResponse;
  expect(response.status()).toBe(200);
  const calculationRaw: unknown = await response.json();
  const calculation = decode("RefuelingCalculation", calculationRaw);
  await expect(page.locator(".total-value")).toHaveText(`${number(calculation.baseTotalRub)} ₽`);
  await page.screenshot({ path: "/private/tmp/fuel-frontend-live-1440.png", fullPage: true });
  await page.setViewportSize({ width: 360, height: 900 });
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page.screenshot({ path: "/private/tmp/fuel-frontend-live-360.png", fullPage: true });
  expect(errors).toEqual([]);
});
