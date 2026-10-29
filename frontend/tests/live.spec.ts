import { expect, test } from "@playwright/test";
import { decode } from "../src/api/client";

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
    await expect(page.locator(`.chart [data-source="${source}"] circle`)).toHaveCount(count);
  }
});
