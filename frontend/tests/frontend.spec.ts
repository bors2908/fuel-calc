import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import {
  apiError,
  mockApi,
  pageResponse,
  selection,
  selectionUrl,
  refuelingUrl,
  meta,
} from "./fixtures";
import { normalizeVolume } from "../src/refueling/model";
import { presetRange } from "../src/selection/dates";

test("page makes one request for both sources without revision preconditions", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  const prices = requests.filter((item) => item.path === "fuel-prices");
  // React StrictMode may start one cancelled mount request in development.
  expect(prices.length).toBeGreaterThan(0);
  expect(prices.length).toBeLessThanOrEqual(2);
  expect(prices[0]?.query.has("dataVersion")).toBe(false);
  expect(prices[0]?.query.has("frequency")).toBe(false);
  expect(requests.some((item) => ["overview", "trend", "comparison"].includes(item.path))).toBe(
    false,
  );
  await expect(page.locator(".overview")).toContainText("72,50");
  await expect(page.locator(".overview")).toContainText("73,50");
  await expect(
    page.getByRole("heading", { name: "Актуальность данных", exact: true }),
  ).toBeVisible();
  await expect(page.locator("main > :last-child")).toHaveClass("archive-info");
  await expect(page.locator(".archive-info > p")).toHaveCount(0);
  await expect(page.getByText("Два взгляда на цену", { exact: true })).toHaveCount(0);
});

test("weekly forecasts are translucent, labelled and have no observation links", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/api/v1/fuel-prices?**", (route) => {
    const value = pageResponse({ ...selection, dateTo: "2026-11-09" });
    for (const source of ["OFFICIAL", "MARKET"] as const) {
      for (const row of value.sources[source].trend) {
        for (const date of ["2026-11-02", "2026-11-09"]) {
          row.points.push({
            kind: "FORECAST",
            date,
            priceRubPerLiter: "80.00",
            observationId: null,
            missingReason: null,
            forecast: {
              asOfDate: meta.serverDate,
              baseDate: "2026-10-19",
              basePriceRubPerLiter: "72.50",
              historicalYears: [2025, 2024],
              growthPercent: "10.000000",
              historySource: "OFFICIAL",
              method: "HISTORICAL_RELATIVE_CHANGE",
            },
          });
        }
      }
    }
    return route.fulfill({ json: value });
  });
  await page.goto(selectionUrl.replace("to=2026-10-29", "to=2026-11-09"));
  const line = page.locator(
    '.chart .series[data-region="23000000000"][data-source="MARKET"] .forecast-line',
  );
  await expect(line).toHaveAttribute("d", /L/);
  await expect(line).toHaveCSS("opacity", "0.5");
  await expect(page.locator(".forecast-boundary")).toContainText("Прогноз");
  await page
    .getByLabel("Дата на графике — точные значения", { exact: true })
    .selectOption("2026-11-09");
  await expect(page.locator(".chart-detail")).toContainText("прогноз на неделю");
  await expect(page.locator(".chart-detail button")).toHaveCount(0);
  await page.getByRole("button", { name: "Таблица", exact: true }).click();
  await expect(page.locator("#trend tbody")).toContainText("Прогноз");
  await expect(page.locator(".chart-note").last()).toContainText(
    "фактическая цена может отличаться",
  );
});

test("a date beyond the forecast year never requests prices", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  const before = requests.length;
  await page.getByLabel("По дату", { exact: true }).fill("2027-10-30");
  await expect(page.getByRole("alert")).toContainText("максимум на год");
  expect(requests.length).toBe(before);
});

test("all regions have both source styles and exact original dates", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  const count = requests.length;
  for (const code of selection.regionCodes) {
    const official = page.locator(`.chart .series[data-region="${code}"][data-source="OFFICIAL"]`);
    const market = page.locator(`.chart .series[data-region="${code}"][data-source="MARKET"]`);
    await page
      .locator(`.legend-region[data-region="${code}"] .legend-item[data-source="OFFICIAL"]`)
      .hover();
    await expect(official.locator("circle")).toHaveCount(2);
    await expect(market.locator("circle")).toHaveCount(0);
    await page
      .locator(`.legend-region[data-region="${code}"] .legend-item[data-source="MARKET"]`)
      .hover();
    await expect(official.locator("circle")).toHaveCount(0);
    await expect(market.locator("circle")).toHaveCount(4);
    await expect(official.locator(".series-line")).toHaveCSS("stroke-dasharray", "none");
    await expect(market.locator(".series-line")).toHaveCSS("stroke-dasharray", "6px, 4px");
    expect(await official.evaluate((el) => getComputedStyle(el).stroke)).toBe(
      await market.evaluate((el) => getComputedStyle(el).stroke),
    );
  }
  await page
    .getByLabel("Дата на графике — точные значения", { exact: true })
    .selectOption("2026-10-20");
  const rostov = page.locator('.chart-region[data-region="61000000000"]');
  await expect(rostov.locator('[data-source="MARKET"]')).toContainText("72,80");
  await expect(rostov.locator('[data-source="OFFICIAL"]')).toContainText("71,80");
  await expect(rostov.locator('[data-source="OFFICIAL"]')).toContainText("19 окт.");
  await page.getByRole("button", { name: "Таблица", exact: true }).click();
  await expect(page.locator('#trend th[scope="colgroup"]')).toHaveCount(3);
  const rows = page.locator("#trend tbody tr");
  await expect(rows).toHaveCount(7);
  const dailyRow = rows.filter({ has: page.getByRole("rowheader", { name: /20 окт\./ }) });
  const weeklyPrice = dailyRow.locator('td[data-region="61000000000"][data-source="OFFICIAL"]');
  await expect(weeklyPrice).toContainText("71,80");
  await expect(weeklyPrice).toContainText("19 окт.");
  const missingWeek = rows.filter({ has: page.getByRole("rowheader", { name: /17 окт\./ }) });
  await expect(
    missingWeek.locator('td[data-region="61000000000"][data-source="OFFICIAL"]'),
  ).toHaveText("—");
  const missingDay = rows.filter({ has: page.getByRole("rowheader", { name: /19 окт\./ }) });
  await expect(
    missingDay.locator('td[data-region="61000000000"][data-source="MARKET"]'),
  ).toHaveText("—");
  expect(requests.length).toBe(count);
});

test("missing base history never hides comparison regions", async ({ page }) => {
  await mockApi(page);
  await page.route("**/api/v1/fuel-prices?**", (route) => {
    const value = pageResponse();
    for (const source of ["OFFICIAL", "MARKET"] as const) {
      value.sources[source].trend = value.sources[source].trend.map((row) =>
        row.region.okatoCode === selection.baseRegionCode
          ? { ...row, points: [], status: "NO_DATA" }
          : row,
      );
    }
    return route.fulfill({ json: value });
  });
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  await page
    .locator('.legend-region[data-region="61000000000"] .legend-item[data-source="MARKET"]')
    .hover();
  await expect(page.locator(".chart circle")).toHaveCount(4);
  await expect(
    page.locator(`.chart [data-region="${selection.baseRegionCode}"] circle`),
  ).toHaveCount(0);
});

for (const absent of ["OFFICIAL", "MARKET"] as const) {
  test(`empty ${absent} preserves the other source`, async ({ page }) => {
    await mockApi(page);
    await page.route("**/api/v1/fuel-prices?**", (route) => {
      const value = pageResponse();
      value.sources[absent] = pageResponse(selection, "empty").sources[absent];
      value.sourceUpdates[absent] = {
        source: absent,
        frequency: absent === "OFFICIAL" ? "WEEKLY" : "DAILY",
        status: "UNAVAILABLE",
        lastSuccessAt: null,
      };
      return route.fulfill({ json: value });
    });
    await page.goto(selectionUrl);
    await expect(page.locator(`.chart [data-source="${absent}"] circle`)).toHaveCount(0);
    await page
      .locator(
        `.legend-region[data-region="${selection.baseRegionCode}"] .legend-item[data-source="${absent === "OFFICIAL" ? "MARKET" : "OFFICIAL"}"]`,
      )
      .hover();
    await expect(
      page.locator(
        `.chart [data-source="${absent === "OFFICIAL" ? "MARKET" : "OFFICIAL"}"] circle`,
      ),
    ).not.toHaveCount(0);
    await expect(page.locator(".archive-info")).toContainText(
      "Последняя успешная загрузка: Неизвестно",
    );
    await expect(page.locator(".archive-info")).not.toContainText("Источник временно недоступен");
  });
}

test("empty history is a successful response with explicit missing prices", async ({ page }) => {
  await mockApi(page, { mode: "empty" });
  await page.goto(selectionUrl);
  await expect(page.locator("#trend")).toContainText("В выбранном диапазоне нет истории");
  await expect(page.locator(".overview")).toContainText("Нет данных за период");
});

test("sorting requests one new coherent page and preserves source ordering", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const before = requests.filter((item) => item.path === "fuel-prices").length;
  await page.getByLabel("Порядок регионов").selectOption("MARKET:PRICE:DESC");
  await expect(page.locator("#comparison .comparison-region-name").first()).toHaveText(
    "Ставропольский край",
  );
  const calls = requests.filter((item) => item.path === "fuel-prices");
  expect(calls).toHaveLength(before + 1);
  expect(calls.at(-1)?.query.get("sortSource")).toBe("MARKET");
  expect(calls.at(-1)?.query.get("sortOrder")).toBe("DESC");
});

test("changing fuel automatically requests a fresh page", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  await page.locator(".fuel-selector").getByText("АИ-92", { exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("АИ-92");
  expect(
    requests.filter(
      (item) => item.path === "fuel-prices" && item.query.get("filterFuelType") === "AI_92",
    ),
  ).toHaveLength(1);
});

test("invalid date input does not fetch and keeps previous result labelled", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  const count = requests.length;
  await page.getByLabel("С даты", { exact: true }).fill("2026-11-01");
  await expect(page.locator(".applied-context")).toContainText("29 июл.");
  expect(requests.length).toBe(count);
});

test("single region has both lines and no comparison table", async ({ page }) => {
  await mockApi(page);
  await page.goto(
    selectionUrl.replace("regions=23000000000%2C61000000000%2C26000000000", "regions=23000000000"),
  );
  await expect(page.locator(".chart .series")).toHaveCount(2);
  await expect(page.locator("#comparison")).toHaveCount(0);
});

test("failed page can be retried without cached responses", async ({ page }) => {
  const requests = await mockApi(page);
  let fail = true;
  await page.route("**/api/v1/fuel-prices?**", (route) =>
    fail ? route.fulfill({ status: 503, json: apiError("ServiceUnavailable") }) : route.fallback(),
  );
  await page.goto(selectionUrl);
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeVisible();
  fail = false;
  await page.getByRole("button", { name: "Повторить", exact: true }).click();
  await expect(page.locator(".chart .series")).toHaveCount(6);
  expect(requests.filter((item) => item.path === "fuel-prices")).toHaveLength(1);
});

test("late results from a cancelled selection cannot overwrite the current page", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/api/v1/fuel-prices?**", async (route) => {
    const query = new URL(route.request().url()).searchParams;
    if (query.get("filterFuelType") === "AI_92") {
      await new Promise((resolve) => setTimeout(resolve, 250));
    }
    await route.fallback();
  });
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  await page.locator(".fuel-selector").getByText("АИ-92", { exact: true }).click();
  await page.locator(".fuel-selector").getByText("АИ-95", { exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("АИ-95");
  await expect(page.locator(".chart .series")).toHaveCount(6);
});

test("observation provenance is fetched on demand and focus returns on close", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  const open = page
    .locator(".overview")
    .getByRole("button", { name: "Об источнике и дате" })
    .first();
  await open.click();
  await expect(page.getByRole("dialog")).toContainText("Росстат");
  await expect(page.getByRole("dialog")).toContainText("Дата наблюдения");
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await expect(open).toBeFocused();
  expect(requests.some((item) => item.path === "observation-OFFICIAL-23000000000")).toBe(true);
});

test("refueling fetches only explicit calculations and sends no snapshot identifiers", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(refuelingUrl);
  expect(requests.some((item) => item.path === "fuel-prices")).toBe(false);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator('.source-calculation[data-source="OFFICIAL"]')).toContainText("43 500");
  await expect(page.locator('.source-calculation[data-source="MARKET"]')).toContainText("44 100");
  const calls = requests.filter((item) => item.path === "calculate");
  expect(calls).toHaveLength(1);
  expect(calls[0]?.body).not.toHaveProperty("expectedSnapshotIds");
});

test("volume validation keeps calculations precise and bounded", () => {
  expect(normalizeVolume("600,25")).toBe("600.25");
  expect(normalizeVolume("0")).toBeNull();
  expect(normalizeVolume("0.001")).toBeNull();
  expect(presetRange("2026-03-31", 1).dateFrom).toBe("2026-02-28");
});

for (const width of [360, 768, 1440]) {
  test(`four regions, table and chart remain accessible at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    await mockApi(page);
    await page.goto(
      selectionUrl.replace(
        "regions=23000000000%2C61000000000%2C26000000000",
        "regions=23000000000%2C61000000000%2C26000000000%2C01000000000",
      ),
    );
    await expect(page.locator(".chart .series")).toHaveCount(8);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    const audit = await new AxeBuilder({ page })
      .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
      .analyze();
    expect(audit.violations).toEqual([]);
    await page.screenshot({ path: `/private/tmp/fuel-chart-${width}.png`, fullPage: true });
    await page.getByRole("button", { name: "Таблица", exact: true }).click();
    await expect(page.locator('#trend th[scope="colgroup"]')).toHaveCount(4);
    const dateColumn = await page.locator('#trend th[scope="row"]').first().boundingBox();
    expect(dateColumn?.width).toBeGreaterThanOrEqual(140);
    await page.screenshot({ path: `/private/tmp/fuel-api-chart-${width}.png`, fullPage: true });
  });
}

test("mismatching page context is rejected instead of relabelling prices", async ({ page }) => {
  await mockApi(page);
  await page.route("**/api/v1/fuel-prices?**", (route) => {
    const value = pageResponse();
    value.context.fuelType = { code: "AI_95", name: "АИ-95" };
    return route.fulfill({ json: value });
  });
  await page.goto(selectionUrl);
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeVisible();
  await expect(page.locator(".chart")).toHaveCount(0);
});

test("unknown response enum is rejected at the API boundary", async ({ page }) => {
  await mockApi(page);
  await page.route("**/api/v1/fuel-prices?**", (route) => {
    const value = pageResponse();
    return route.fulfill({ json: { ...value, comparisonOrder: "UNKNOWN" } });
  });
  await page.goto(selectionUrl);
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeVisible();
  await expect(page.locator(".chart")).toHaveCount(0);
});

test("history navigation fetches fresh pages without a query cache", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  await page.locator(".fuel-selector").getByText("АИ-92", { exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("АИ-92");
  const count = requests.filter((item) => item.path === "fuel-prices").length;
  await page.goBack();
  await expect(page.locator(".applied-context")).toContainText("Дизельное топливо");
  expect(requests.filter((item) => item.path === "fuel-prices")).toHaveLength(count + 1);
});

test("refueling clears the previous result when input selection changes", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(refuelingUrl);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".source-calculation")).toHaveCount(2);
  const count = requests.length;
  await page.locator(".fuel-selector").getByText("АИ-92", { exact: true }).click();
  await expect(page.locator(".source-calculation")).toHaveCount(0);
  expect(requests.length).toBe(count);
});

test("a failed observation request can be retried independently", async ({ page }) => {
  await mockApi(page);
  let failed = true;
  await page.route("**/api/v1/fuel-prices/observations/**", (route) =>
    failed
      ? route.fulfill({ status: 503, json: apiError("ServiceUnavailable") })
      : route.fallback(),
  );
  await page.goto(selectionUrl);
  await page
    .locator(".overview")
    .getByRole("button", { name: "Об источнике и дате" })
    .first()
    .click();
  await expect(
    page.getByRole("dialog").getByRole("button", { name: "Повторить", exact: true }),
  ).toBeVisible();
  failed = false;
  await page.getByRole("dialog").getByRole("button", { name: "Повторить", exact: true }).click();
  await expect(page.getByRole("dialog")).toContainText("Росстат");
});

test("server sorting survives a filter change and a failed refresh retry", async ({ page }) => {
  const requests = await mockApi(page);
  let fail = false;
  await page.route("**/api/v1/fuel-prices?**", (route) =>
    fail ? route.fulfill({ status: 503, json: apiError("ServiceUnavailable") }) : route.fallback(),
  );
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const sorting = page.getByLabel("Порядок регионов");
  await sorting.selectOption("MARKET:PRICE:DESC");
  await expect(sorting).toHaveValue("MARKET:PRICE:DESC");
  await expect(sorting).toBeEnabled();
  fail = true;
  await page.locator(".fuel-selector").getByText("АИ-92", { exact: true }).click();
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeVisible();
  await expect(page.locator(".applied-context")).toContainText("Дизельное топливо");
  fail = false;
  await page.getByRole("button", { name: "Повторить", exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("АИ-92");
  await expect(sorting).toHaveValue("MARKET:PRICE:DESC");
  const last = requests.filter((item) => item.path === "fuel-prices").at(-1);
  expect(last?.query.get("filterFuelType")).toBe("AI_92");
  expect(last?.query.get("sortSource")).toBe("MARKET");
  expect(last?.query.get("sortBy")).toBe("PRICE");
  expect(last?.query.get("sortOrder")).toBe("DESC");
  await expect(page.locator("#comparison .comparison-region-name").first()).toHaveText(
    "Ставропольский край",
  );
});

test("region search stays local and commits a keyboard selection once", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  const count = requests.filter((item) => item.path === "fuel-prices").length;
  const region = page.getByRole("combobox", { name: "Мой регион" });
  await region.fill("ростов");
  await expect(page.getByRole("option", { name: "Ростовская область" })).toBeVisible();
  expect(requests.filter((item) => item.path === "fuel-prices")).toHaveLength(count);
  await region.press("ArrowDown");
  await region.press("Enter");
  await expect(region).toHaveValue("Ростовская область");
  await expect(page.locator(".applied-context")).toContainText("Ростовская область");
  expect(requests.filter((item) => item.path === "fuel-prices")).toHaveLength(count + 1);
  await expect(page.getByRole("listbox")).toHaveCount(0);
});

test("incomplete source rows produce a retryable error before chart rendering", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/api/v1/fuel-prices?**", (route) => {
    const value = pageResponse();
    value.sources.MARKET.comparison.data.pop();
    return route.fulfill({ json: value });
  });
  await page.goto(selectionUrl);
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeVisible();
  await expect(page.locator(".chart")).toHaveCount(0);
});

test("a late calculation cannot reappear after invalidating its inputs", async ({ page }) => {
  await mockApi(page);
  let release = () => {};
  const pending = new Promise<void>((resolve) => {
    release = resolve;
  });
  await page.route("**/api/v1/refueling/calculate", async (route) => {
    await pending;
    await route.fallback();
  });
  await page.goto(refuelingUrl);
  const sent = page.waitForRequest("**/api/v1/refueling/calculate");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await sent;
  await page.locator(".fuel-selector").getByText("АИ-92", { exact: true }).click();
  release();
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeEnabled();
  await expect(page.locator(".calculation-placeholder")).toBeVisible();
  await expect(page.locator(".source-calculation")).toHaveCount(0);
});

test("prices without a common period show dates without comparable differences", async ({
  page,
}) => {
  await mockApi(page, { mode: "no-common" });
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await expect(page.locator("#comparison")).toContainText("Общего периода нет");
  await expect(page.locator("#comparison .comparison-price")).toHaveCount(6);
  await expect(page.locator("#comparison .comparison-lowest")).toHaveCount(0);
  await expect(page.locator("#comparison .comparison-date")).toHaveCount(6);
  await expect(page.locator("#comparison")).toContainText("Нет сопоставимой разницы");
});

test("enlarged text keeps chart and forms within the page", async ({ page }) => {
  await page.setViewportSize({ width: 1440, height: 1000 });
  await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "200%";
  });
  await expect(page.getByRole("combobox", { name: "Мой регион" })).toBeVisible();
  await page.getByRole("combobox", { name: "Мой регион" }).focus();
  await expect(page.getByRole("combobox", { name: "Мой регион" })).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  await expect
    .poll(() =>
      page.locator(".chart svg").evaluate((svg) => {
        const labels = [...svg.querySelectorAll("text")];
        const bounds = svg.getBoundingClientRect();
        return labels.every((label) => {
          const box = label.getBoundingClientRect();
          return box.left >= bounds.left && box.right <= bounds.right;
        });
      }),
    )
    .toBe(true);
  await page.screenshot({ path: "/private/tmp/fuel-chart-enlarged-text.png", fullPage: true });
});

test("markers appear only for the hovered curve or its legend and support keyboard focus", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  await expect(page.locator(".chart circle")).toHaveCount(0);
  const before = requests.length;
  const region = selection.baseRegionCode;
  const legend = page.locator(
    `.legend-region[data-region="${region}"] .legend-item[data-source="OFFICIAL"]`,
  );
  await legend.hover();
  await expect(page.locator(".chart circle")).toHaveCount(2);
  await expect(page.locator('.chart [data-source="MARKET"] circle')).toHaveCount(0);
  await page.mouse.move(0, 0);
  await expect(page.locator(".chart circle")).toHaveCount(0);
  await legend.focus();
  await expect(page.locator(".chart circle")).toHaveCount(2);
  await legend.press("Tab");
  await expect(page.locator('.chart [data-source="OFFICIAL"] circle')).toHaveCount(0);
  await expect(page.locator(".chart circle")).toHaveCount(4);
  await page.getByRole("button", { name: "Таблица", exact: true }).focus();
  const target = page.locator(
    '.chart .series[data-region="26000000000"][data-source="MARKET"] .series-line',
  );
  await target.scrollIntoViewIfNeeded();
  const position = await target.evaluate((element) => {
    if (!(element instanceof SVGPathElement)) {
      throw new Error("Missing curve path");
    }
    const point = element.getPointAtLength(element.getTotalLength() / 3);
    const matrix = element.getScreenCTM();
    if (!matrix) {
      throw new Error("Missing SVG transform");
    }
    const screen = point.matrixTransform(matrix);
    return { x: screen.x, y: screen.y };
  });
  await page.mouse.move(position.x, position.y);
  await expect(
    page.locator('.chart .series[data-region="26000000000"][data-source="MARKET"] circle'),
  ).toHaveCount(4);
  await expect(page.locator(".chart circle")).toHaveCount(4);
  await page.mouse.move(0, 0);
  await expect(page.locator(".chart circle")).toHaveCount(0);
  expect(requests.length).toBe(before);
});
