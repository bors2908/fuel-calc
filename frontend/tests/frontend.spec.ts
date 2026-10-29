import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import {
  apiError,
  mockApi,
  selection,
  selectionUrl,
  responses,
  price,
  refuelingUrl,
} from "./fixtures";
import { normalizeVolume } from "../src/refueling/model";
import { number } from "../src/ui/format";
import { presetRange } from "../src/selection/model";

async function chooseRegion(page: Page, name: string) {
  await page.getByRole("combobox", { name: "Мой регион", exact: true }).fill(name);
  await page.getByRole("option", { name, exact: true }).click();
}

test("region labels use the backend name", async ({ page }) => {
  await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.getByRole("combobox", { name: "Мой регион", exact: true })).toHaveValue(
    "Краснодарский край",
  );
  await expect(page.locator(".applied-context")).toContainText("Краснодарский край");
  await expect(page.locator(".applied-context")).not.toContainText("официальное название");
  await chooseRegion(page, "Ростовская область");
  await expect(page.locator(".applied-context")).toContainText("Ростовская область");
});

test("decimal inputs and formatting preserve backend precision", () => {
  expect(normalizeVolume("600,50")).toBe("600.50");
  for (const invalid of ["", "0", "0.001", "1000000.01", "1e3", "01", "NaN", "-1"]) {
    expect(normalizeVolume(invalid)).toBeNull();
  }
  expect(normalizeVolume("0.01")).toBe("0.01");
  expect(normalizeVolume("1000000.00")).toBe("1000000.00");
  expect(number("9007199254740993.123456").replaceAll("\u00a0", " ")).toBe(
    "9 007 199 254 740 993,123456",
  );
  expect(presetRange("2026-03-31", 1).dateFrom).toBe("2026-02-28");
});

test("selecting a region loads immediately; revisiting a fuel fetches fresh data", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto("/");
  expect(requests.some((request) => request.path === "overview")).toBe(false);
  await expect(page.getByRole("button", { name: "Показать", exact: true })).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Отменить изменения" })).toHaveCount(0);
  await chooseRegion(page, "Краснодарский край");
  await expect(page.locator(".overview")).toBeVisible();
  const before = requests.filter((request) => request.path === "overview").length;
  const fuel = page.getByRole("group", { name: "Топливо", exact: true });
  await fuel.getByText("АИ-92", { exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("АИ-92");
  await fuel.getByText("Дизель", { exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("Дизель");
  expect(requests.filter((request) => request.path === "overview")).toHaveLength(before + 2);
  expect(
    await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })),
  ).toEqual({ local: 0, session: 0 });
});

test("region search only requests prices after an option is selected", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const before = requests.length;
  const region = page.getByRole("combobox", { name: "Мой регион", exact: true });
  await region.fill("Нет такого региона");
  await expect(page.getByRole("status")).toContainText("Регион не найден");
  await region.press("Escape");
  await expect(region).toHaveValue("Краснодарский край");
  expect(requests.length).toBe(before);
  await region.fill("  ОБЛАСТЬ  ");
  await expect(page.getByRole("listbox", { name: "Регионы" }).getByRole("option")).toHaveCount(1);
  await region.press("ArrowDown");
  await expect(region).toBeFocused();
  await region.press("Enter");
  await expect(region).toHaveValue("Ростовская область");
  await expect(region).toHaveAttribute("aria-expanded", "false");
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
  const selectedRequests = requests.length;
  await region.fill("Ставрополь");
  await region.press("Tab");
  await expect(region).toHaveValue("Ростовская область");
  expect(requests.length).toBe(selectedRequests);
  await page.goBack();
  await expect(region).toHaveValue("Краснодарский край");
  await expect(page.locator(".applied-context strong")).toContainText("Краснодарский край");
});

for (const view of [
  { name: "prices", url: selectionUrl, width: 360 },
  { name: "refueling", url: refuelingUrl, width: 1440 },
]) {
  test(`additional region search filters locally and keeps selections on ${view.name}`, async ({
    page,
  }) => {
    await page.setViewportSize({ width: view.width, height: 1000 });
    const requests = await mockApi(page);
    await page.goto(view.url);
    if (view.name === "prices") {
      await expect(page.locator("#comparison tbody tr")).toHaveCount(3);
      await page.getByRole("button", { name: "Изменить выбор", exact: true }).click();
    } else {
      await expect(page.locator("#calculator")).toBeVisible();
    }
    await page.locator(".extra-regions summary").click();
    const options = page.locator(".region-options");
    const search = page.getByRole("searchbox", { name: "Найти регион", exact: true });
    const before = requests.length;
    const originalUrl = page.url();
    await search.fill("  ОБЛАСТЬ  ");
    await expect(options.getByRole("checkbox")).toHaveCount(1);
    await expect(options.getByRole("checkbox", { name: "Ростовская область" })).toBeChecked();
    await search.fill("Несуществующий регион");
    await expect(options.getByRole("checkbox")).toHaveCount(0);
    await expect(page.locator(".extra-regions").getByRole("status")).toContainText(
      "Регион не найден",
    );
    await search.fill("");
    await expect(options.getByRole("checkbox")).toHaveCount(3);
    await expect(options.getByRole("checkbox", { name: "Ставропольский край" })).toBeChecked();
    expect(requests.length).toBe(before);
    expect(page.url()).toBe(originalUrl);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze())
        .violations,
    ).toEqual([]);
    await page.screenshot({
      path: `/private/tmp/fuel-region-search-${view.name}.png`,
      fullPage: true,
    });
    await search.fill("Ростов");
    await search.press("Tab");
    await expect(options.getByRole("checkbox")).toBeFocused();
    await options.getByRole("checkbox").press("Space");
    await expect(options.getByRole("checkbox")).not.toBeChecked();
    await search.fill("");
    await expect(options.getByRole("checkbox", { name: "Ставропольский край" })).toBeChecked();
    await search.fill("Ростов");
    await page.getByRole("button", { name: "Сбросить фильтры", exact: true }).click();
    await expect(search).toHaveValue("");
    await expect(options.getByRole("checkbox")).toHaveCount(3);
  });
}

test("fuel segments apply immediately when navigated with the keyboard", async ({ page }) => {
  await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const fuel = page.getByRole("group", { name: "Топливо", exact: true });
  await expect(fuel.getByRole("radio")).toHaveCount(3);
  await fuel.getByRole("radio", { name: "Дизель", exact: true }).focus();
  await page.keyboard.press("ArrowRight");
  await expect(fuel.getByRole("radio", { name: "АИ-92", exact: true })).toBeChecked();
  await expect(page.locator(".applied-context")).toContainText("АИ-92");
  await page.keyboard.press("ArrowRight");
  await expect(fuel.getByRole("radio", { name: "АИ-95", exact: true })).toBeChecked();
  await expect(page.locator(".applied-context")).toContainText("АИ-95");
  await expect(page).toHaveURL(/fuel=AI_95/);
});

test("both sources load automatically without source or frequency controls", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator('.chart [data-source="OFFICIAL"] circle')).toHaveCount(6);
  await expect(page.locator('.chart [data-source="MARKET"] circle')).toHaveCount(12);
  await expect(page.locator(".data-parameters")).toHaveCount(0);
  await expect(page.getByText("Покрытие архива", { exact: true })).toHaveCount(0);
  const trends = requests.filter((item) => item.path === "trend");
  expect(trends.map((item) => [item.query.get("source"), item.query.get("frequency")])).toEqual(
    expect.arrayContaining([
      ["OFFICIAL", "WEEKLY"],
      ["MARKET", "DAILY"],
    ]),
  );
  expect(trends.every((item) => item.query.get("dataVersion") === "v1")).toBe(true);
  const official = await page
    .locator('.chart [data-source="OFFICIAL"] circle')
    .evaluateAll((points) => points.map((point) => Number(point.getAttribute("cx"))));
  const market = await page
    .locator('.chart [data-source="MARKET"] circle')
    .evaluateAll((points) => points.map((point) => Number(point.getAttribute("cx"))));
  expect((official[1] ?? 0) - (official[0] ?? 0)).toBeCloseTo(
    14 * ((market[1] ?? 0) - (market[0] ?? 0)),
    3,
  );
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page).toHaveURL(/from=2026-09-29/);
  await page.goBack();
  await expect(page.getByLabel("С даты", { exact: true })).toHaveValue(selection.dateFrom);
});

test("every selected region has both source series, exact values and history columns", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series")).toHaveCount(6);
  const before = requests.length;
  await page
    .getByLabel("Дата на графике — точные значения", { exact: true })
    .selectOption("2026-10-19");
  for (const code of selection.regionCodes) {
    const lines = page.locator(`.chart .series[data-region="${code}"]`);
    await expect(lines).toHaveCount(2);
    const official = page.locator(`.chart .series[data-region="${code}"][data-source="OFFICIAL"]`);
    const market = page.locator(`.chart .series[data-region="${code}"][data-source="MARKET"]`);
    await expect(official).toHaveCount(1);
    await expect(market).toHaveCount(1);
    await expect(official.locator("path")).toHaveCSS("stroke-dasharray", "none");
    await expect(market.locator("path")).toHaveCSS("stroke-dasharray", "6px, 4px");
    expect(await official.evaluate((line) => getComputedStyle(line).stroke)).toBe(
      await market.evaluate((line) => getComputedStyle(line).stroke),
    );
    await expect(page.locator(`.legend-region[data-region="${code}"] .legend-item`)).toHaveCount(2);
    const values = page.locator(`.chart-region[data-region="${code}"]`);
    await expect(values.locator('[data-source="OFFICIAL"] .price-value')).toBeVisible();
    await expect(values.locator('[data-source="MARKET"]')).toContainText("Нет данных за период");
  }
  const regionalColors = await page
    .locator('.chart .series[data-source="OFFICIAL"]')
    .evaluateAll((lines) => lines.map((line) => getComputedStyle(line).stroke));
  expect(new Set(regionalColors).size).toBe(selection.regionCodes.length);
  const rostov = page.locator('.chart-region[data-region="61000000000"]');
  await expect(rostov).toContainText("Ростовская область");
  await expect(rostov).toContainText("71,80");
  await page
    .getByLabel("Дата на графике — точные значения", { exact: true })
    .selectOption("2026-10-20");
  await expect(rostov.locator('[data-source="MARKET"]')).toContainText("72,80");
  await expect(rostov.locator('[data-source="OFFICIAL"]')).toContainText(
    "Нет наблюдения на эту дату",
  );
  await page.getByRole("button", { name: "Таблица", exact: true }).click();
  await expect(page.locator('#trend th[scope="colgroup"]')).toHaveCount(3);
  const row = page.locator("#trend tbody tr").filter({ hasText: "20 окт." });
  for (const code of selection.regionCodes) {
    await expect(row.locator(`td[data-region="${code}"]`)).toHaveCount(2);
  }
  await expect(row.locator('td[data-region="61000000000"][data-source="MARKET"]')).toContainText(
    "72,80",
  );
  expect(requests.length).toBe(before);
});

test("comparison regions keep their history when the base has no observations", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/fuel-prices/trend?**", (route) => {
    const official = new URL(route.request().url()).searchParams.get("source") === "OFFICIAL";
    const data = responses({
      ...selection,
      source: official ? "OFFICIAL" : "MARKET",
      frequency: official ? "WEEKLY" : "DAILY",
    }).trend;
    data.data = data.data.map((row) =>
      row.region.okatoCode === selection.baseRegionCode ||
      (!official && row.region.okatoCode === "61000000000")
        ? { ...row, status: "NO_DATA", points: [] }
        : row,
    );
    return route.fulfill({ json: data });
  });
  await page.goto(selectionUrl);
  await expect(page.locator(".chart circle")).toHaveCount(8);
  await expect(
    page.locator(`.chart [data-region="${selection.baseRegionCode}"] circle`),
  ).toHaveCount(0);
  await expect(
    page.locator('.chart [data-region="61000000000"][data-source="OFFICIAL"] circle'),
  ).toHaveCount(2);
  await expect(
    page.locator('.legend-region[data-region="61000000000"] [data-source="MARKET"]'),
  ).toContainText("Нет наблюдений за период");
  await page
    .getByLabel("Дата на графике — точные значения", { exact: true })
    .selectOption("2026-10-19");
  await expect(page.locator('.chart-region[data-region="61000000000"]')).toContainText("71,80");
  await expect(page.locator("#trend .empty-state")).toHaveCount(0);
});

test("incomplete and invalid dates keep the displayed context and never request prices", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const before = requests.length;
  const originalUrl = page.url();
  await page.getByLabel("С даты", { exact: true }).fill("");
  await expect(page.locator(".total-value").first()).toHaveCount(0);
  await expect(page.locator("#calculator")).toHaveCount(0);
  await page.getByLabel("По дату", { exact: true }).fill("2026-01-01");
  await page.getByLabel("С даты", { exact: true }).fill("2026-02-01");
  await expect(page.getByRole("alert")).toContainText("корректный период");
  expect(requests.length).toBe(before);
  expect(page.url()).toBe(originalUrl);
  await expect(page.locator(".overview")).toBeVisible();
  await page.getByLabel("По дату", { exact: true }).fill("2026-03-01");
  await expect(page).toHaveURL(/from=2026-02-01&to=2026-03-01/);
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.getByRole("link", { name: "Рассчитать заправку" })).toBeVisible();
  expect(requests.length).toBeGreaterThan(before);
});

test("period presets, additional regions and reset apply without confirmation", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page).toHaveURL(/from=2026-09-29/);
  await expect(page.getByLabel("С даты", { exact: true })).toHaveValue("2026-09-29");
  await page.locator(".extra-regions summary").click();
  await page.getByRole("checkbox", { name: /очень длинным/ }).check();
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(4);
  await page.getByRole("button", { name: "Сбросить фильтры" }).click();
  await expect(page.locator("#comparison")).toHaveCount(0);
  await expect(page.getByLabel("С даты", { exact: true })).toHaveValue(selection.dateFrom);
  expect(
    requests
      .filter((item) => item.path === "overview")
      .at(-1)
      ?.query.get("source"),
  ).toBe("MARKET");
  expect(
    requests
      .filter((item) => item.path === "overview")
      .at(-1)
      ?.query.get("frequency"),
  ).toBe("DAILY");
});

test("history keeps gaps, table and source detail need no point-by-point requests", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series path")).toHaveCount(6);
  const path = await page.locator(".chart .series path").first().getAttribute("d");
  expect(path?.match(/M/g)).toHaveLength(2);
  const before = requests.length;
  await page.getByRole("button", { name: "Таблица", exact: true }).click();
  await expect(page.locator("#trend").getByRole("table")).toContainText("12 окт.");
  expect(requests.length).toBe(before);
  const trigger = page
    .locator(".overview")
    .getByRole("button", { name: "Об источнике и дате" })
    .first();
  await trigger.click();
  await expect(page.getByRole("dialog")).toContainText("20 окт.");
  const firstCount = requests.filter((request) => request.path.startsWith("observation-")).length;
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await expect(trigger).toBeFocused();
  await trigger.click();
  await expect
    .poll(() => requests.filter((request) => request.path.startsWith("observation-")).length)
    .toBeGreaterThan(firstCount);
  await page.keyboard.press("Escape");
  await expect(page.getByRole("dialog")).toHaveCount(0);
});

test("sorting only fetches comparison and preserves the selected context", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const overviewCount = requests.filter((request) => request.path === "overview").length;
  const comparisonCount = requests.filter((request) => request.path === "comparison").length;
  await page.getByLabel("Порядок регионов", { exact: true }).selectOption("MARKET:PRICE:DESC");
  await expect
    .poll(() => requests.filter((request) => request.path === "comparison").length)
    .toBe(comparisonCount + 1);
  expect(requests.filter((request) => request.path === "overview").length).toBe(overviewCount);
  expect(requests.at(-1)?.query.get("dataVersion")).toBe("v1");
});

test("calculation submits decimal strings and resets immediately on volume or applied context changes", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(refuelingUrl);
  await page.getByLabel("Объём, л", { exact: true }).fill("0.001");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator("#volume-error")).toContainText("Введите");
  expect(requests.filter((request) => request.path === "calculate")).toHaveLength(0);
  await page.getByLabel("Объём, л", { exact: true }).fill("600,00");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".total-value").first()).toHaveText(/43\s500,00\s₽/);
  expect(requests.find((request) => request.path === "calculate")?.body).toMatchObject({
    selection: {
      baseRegionCode: selection.baseRegionCode,
      regionCodes: selection.regionCodes,
      fuelType: selection.fuelType,
      date: selection.dateTo,
    },
    volumeLiters: "600.00",
  });
  await page.getByLabel("Объём, л", { exact: true }).fill("601");
  await expect(page.locator(".total-value").first()).toHaveCount(0);
  await page.getByLabel("Объём, л", { exact: true }).fill("600");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".total-value").first()).toBeVisible();
  await page
    .getByRole("group", { name: "Топливо", exact: true })
    .getByText("АИ-95", { exact: true })
    .click();
  await expect(page.locator(".total-value").first()).toHaveCount(0);
  await expect(page.getByLabel("Объём, л", { exact: true })).toHaveValue("600");
});

test("refueling renders the POST revision without loading price screens", async ({ page }) => {
  const requests = await mockApi(page, { changedCalculation: true });
  await page.goto(refuelingUrl);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".source-calculation")).toHaveCount(2);
  expect(
    requests.filter((item) =>
      ["overview", "official-overview", "trend", "comparison", "official-comparison"].includes(
        item.path,
      ),
    ),
  ).toHaveLength(0);
});

test("archive revision conflict retries once, then stops", async ({ page }) => {
  const requests = await mockApi(page, { conflicts: 5 });
  await page.goto("/");
  await chooseRegion(page, "Краснодарский край");
  await expect(page.getByRole("alert")).toContainText("Данные обновляются");
  expect(requests.filter((request) => request.path === "overview")).toHaveLength(2);
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeEnabled();
});

test("one archive conflict recovers; individual block failure keeps the overview", async ({
  page,
}) => {
  await mockApi(page, { conflicts: 1 });
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await page.unrouteAll({ behavior: "wait" });
  await mockApi(page, { failTrend: true });
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page.getByRole("alert").first()).toContainText("Не удалось загрузить историю");
  await expect(page.locator(".overview")).toBeVisible();
});

test("late response cannot replace a newer selection", async ({ page }) => {
  await mockApi(page, { delayBase: selection.baseRegionCode });
  await page.goto(selectionUrl);
  await chooseRegion(page, "Ростовская область");
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
  await expect(page.locator("#chart-title")).toContainText("Ростовская область");
});

test("an incomplete date cancels an in-flight selection and keeps the previous screen", async ({
  page,
}) => {
  const requests = await mockApi(page, { delayBase: "61000000000" });
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await chooseRegion(page, "Ростовская область");
  await expect
    .poll(() =>
      requests.some(
        (item) => item.path === "overview" && item.query.get("baseRegionCode") === "61000000000",
      ),
    )
    .toBe(true);
  const aborted = page.waitForEvent(
    "requestfailed",
    (request) => request.url().includes("/overview?") && request.url().includes("61000000000"),
  );
  await page.getByLabel("С даты", { exact: true }).fill("");
  await aborted;
  await expect(page.locator(".applied-context strong")).toContainText("Краснодарский край");
  await expect(page.locator("#calculator")).toHaveCount(0);
  await expect(page.getByText("Обновляем данные.", { exact: false })).toHaveCount(0);
  await page.getByLabel("С даты", { exact: true }).fill(selection.dateFrom);
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
});

test("mobile filters remain open while automatic updates finish", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 900 });
  await mockApi(page);
  await page.goto("/");
  await chooseRegion(page, "Краснодарский край");
  await expect(page.locator(".overview")).toBeVisible();
  await expect(page.getByRole("combobox", { name: "Мой регион", exact: true })).toBeVisible();
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page).toHaveURL(/from=2026-09-29/);
  await expect(page.getByRole("combobox", { name: "Мой регион", exact: true })).toBeVisible();
  await page.getByRole("button", { name: /Изменить выбор/ }).click();
  await expect(page.getByRole("combobox", { name: "Мой регион", exact: true })).toBeHidden();
});

for (const mode of ["empty", "no-common"] as const) {
  test(`${mode}: missing prices never become zero or a partial calculation`, async ({ page }) => {
    await mockApi(page, { mode });
    await page.goto(selectionUrl);
    await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
    await expect(page.locator("#comparison")).toContainText(
      mode === "empty" ? "Нет наблюдений" : "Общего периода нет",
    );
    await page.getByRole("link", { name: "Рассчитать заправку" }).click();
    await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
    await expect(page.locator(".empty-calculation")).toHaveCount(2);
    await expect(page.locator(".total-value").first()).toHaveCount(0);
  });
}

test("invalid market payload is rejected while official data remains visible", async ({ page }) => {
  await mockApi(page);
  await page.route("**/fuel-prices/overview?**", async (route) => {
    if (new URL(route.request().url()).searchParams.get("source") === "MARKET") {
      await route.fulfill({
        json: { ...apiError("InternalError"), latestPrice: { priceRubPerLiter: 72.5 } },
      });
    } else {
      await route.fallback();
    }
  });
  await page.goto(selectionUrl);
  await expect(page.locator('.overview [data-source="MARKET"]')).toContainText(
    "Не удалось загрузить рыночную сводку",
  );
  await expect(page.locator('.overview [data-source="OFFICIAL"] .price-value')).toContainText(
    "72,50",
  );
});

for (const width of [360, 768, 1440]) {
  test(`responsive ${width}px: no page overflow, usable keyboard and reduced motion`, async ({
    page,
  }) => {
    const errors: string[] = [];
    page.on("pageerror", (error) => errors.push(error.message));
    await page.setViewportSize({ width, height: 1000 });
    await page.emulateMedia({ reducedMotion: "reduce" });
    await mockApi(page);
    await page.goto(selectionUrl);
    await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.screenshot({ path: `/private/tmp/fuel-chart-${width}.png`, fullPage: true });
    await page.getByRole("button", { name: "Таблица", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(page.locator("#trend").getByRole("table")).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    expect(errors).toEqual([]);
    await page.screenshot({ path: `/private/tmp/fuel-frontend-${width}.png`, fullPage: true });
  });
}

test("one failed source keeps the other curve and market calculation", async ({ page }) => {
  await mockApi(page);
  await page.route("**/fuel-prices/trend?**", async (route) => {
    const query = new URL(route.request().url()).searchParams;
    if (query.get("source") === "OFFICIAL") {
      await route.fulfill({ status: 503, json: apiError("ServiceUnavailable") });
    } else {
      await route.fallback();
    }
  });
  await page.goto(selectionUrl);
  await expect(page.locator('.chart [data-source="MARKET"] circle')).toHaveCount(12);
  await expect(page.locator("#trend")).toContainText(
    "Не удалось загрузить историю: официальная цена",
  );
  await expect(page.getByRole("link", { name: "Рассчитать заправку" })).toBeVisible();
});

test("an empty region catalog explains that the first import is pending", async ({ page }) => {
  const requests = await mockApi(page);
  await page.route("**/api/v1/regions", (route) =>
    route.fulfill({ json: { meta: responses().overview.meta, data: [] } }),
  );
  await page.goto("/");
  await expect(page.getByRole("status")).toContainText("Цены пока не загружены");
  await expect(page.getByRole("button", { name: "Обновить список" })).toBeVisible();
  expect(requests.some((item) => item.path === "overview")).toBe(false);
});

test("official history remains visible when market history fails", async ({ page }) => {
  await mockApi(page);
  await page.route("**/fuel-prices/trend?**", async (route) => {
    if (new URL(route.request().url()).searchParams.get("source") === "MARKET") {
      await route.fulfill({ status: 503, json: apiError("ServiceUnavailable") });
    } else {
      await route.fallback();
    }
  });
  await page.goto(selectionUrl);
  await expect(page.locator('.chart [data-source="OFFICIAL"] circle')).toHaveCount(6);
  await expect(page.locator("#trend")).toContainText("Не удалось загрузить историю: рыночная цена");
});

test("four regions and one point remain usable with text zoom and accessible semantics", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/fuel-prices/trend?**", (route) => {
    const official = new URL(route.request().url()).searchParams.get("source") === "OFFICIAL";
    const singlePoint = responses({
      ...selection,
      regionCodes: [...selection.regionCodes, "01000000000"],
      source: official ? "OFFICIAL" : "MARKET",
      frequency: official ? "WEEKLY" : "DAILY",
    });
    singlePoint.trend.data = singlePoint.trend.data.map((series) => ({
      ...series,
      points: series.points.slice(-1),
    }));
    return route.fulfill({ json: singlePoint.trend });
  });
  await page.goto(
    selectionUrl.replace(
      "regions=23000000000%2C61000000000%2C26000000000",
      "regions=23000000000%2C61000000000%2C26000000000%2C01000000000",
    ),
  );
  await expect(page.locator(".chart circle")).toHaveCount(8);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(4);
  const audit = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(audit.violations).toEqual([]);
  await page.setViewportSize({ width: 360, height: 900 });
  await page.evaluate(() => {
    document.documentElement.style.fontSize = "200%";
  });
  const overflow = await page.evaluate(() =>
    [...document.querySelectorAll("body *")]
      .filter((element) => {
        const box = element.getBoundingClientRect();
        return (
          box.width &&
          (box.right > innerWidth + 1 || box.left < -1) &&
          getComputedStyle(element).position !== "absolute" &&
          !element.closest(".table-scroll, .comparison-scroll")
        );
      })
      .map((element) => ({
        tag: element.tagName,
        className: element.getAttribute("class"),
        right: element.getBoundingClientRect().right,
      }))
      .slice(0, 15),
  );
  expect(overflow).toEqual([]);
  await page.getByRole("button", { name: /Изменить выбор/ }).click();
  await expect(page.getByLabel("Мой регион", { exact: true })).toBeVisible();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
});

test("make another region primary preserves the group and sends a fresh selection", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await page
    .locator("#comparison .comparison-row")
    .filter({ hasText: "Ростовская область" })
    .getByRole("button", { name: "Сделать основным" })
    .click();
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  expect(
    requests
      .filter((item) => item.path === "overview")
      .at(-1)
      ?.query.get("baseRegionCode"),
  ).toBe("61000000000");
  expect(new URL(page.url()).searchParams.get("regions")?.split(",")).toEqual(
    expect.arrayContaining(selection.regionCodes),
  );
});

test("prices disappearing before POST produce two unavailable results", async ({ page }) => {
  await mockApi(page);
  await page.route("**/refueling/calculate", (route) =>
    route.fulfill({ json: responses(selection, "v1", "empty").calculation }),
  );
  await page.goto(refuelingUrl);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".empty-calculation")).toHaveCount(2);
  await expect(page.locator(".total-value").first()).toHaveCount(0);
});

test("a conflict while sorting performs only one complete refresh", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  const before = requests.filter((item) => item.path === "overview").length;
  await page.route("**/fuel-prices/comparison?**", (route) =>
    route.fulfill({ status: 409, json: apiError("DataVersionChanged") }),
  );
  await page.getByLabel("Порядок регионов", { exact: true }).selectOption("MARKET:PRICE:DESC");
  await expect(page.getByRole("alert")).toContainText("Данные обновляются");
  expect(requests.filter((item) => item.path === "overview").length).toBe(before + 1);
});

test("official price comes first, market second, and both calculations remain distinct", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".overview > div").nth(0)).toContainText("Официальная цена");
  await expect(page.locator(".overview > div").nth(1)).toContainText("Рыночная цена");
  await expect(page.locator('.overview [data-source="OFFICIAL"]')).toContainText("72,50");
  await expect(page.locator('.overview [data-source="MARKET"]')).toContainText("73,50");
  await page.getByRole("link", { name: "Рассчитать заправку" }).click();
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".source-calculation")).toHaveCount(2);
  await expect(page.locator('.source-calculation[data-source="OFFICIAL"] .total-value')).toHaveText(
    /43\s500,00\s₽/,
  );
  await expect(page.locator('.source-calculation[data-source="MARKET"] .total-value')).toHaveText(
    /44\s100,00\s₽/,
  );
  await page.screenshot({ path: "/private/tmp/fuel-dual-calculations.png", fullPage: true });
});

for (const missing of ["MARKET", "OFFICIAL"] as const) {
  test(`missing ${missing} does not prevent the other calculation`, async ({ page }) => {
    await mockApi(page);
    const calculation = responses().calculation;
    const result = calculation.results.find((item) => item.context.source === missing);
    if (!result) {
      throw new Error("Missing fixture result");
    }
    result.baseTotalRub = null;
    result.comparison = null;
    result.status = "NO_DATA";
    await page.route("**/refueling/calculate", (route) => route.fulfill({ json: calculation }));
    await page.goto(refuelingUrl);
    await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
    await expect(page.locator(".source-calculation")).toHaveCount(2);
    await expect(page.locator(".total-value")).toHaveCount(1);
    await expect(page.locator(`.source-calculation[data-source="${missing}"]`)).toContainText(
      "Нет цен для расчёта",
    );
  });
}

test("both sources expose their own change, minimum and comparison date", async ({ page }) => {
  await mockApi(page);
  await page.goto(selectionUrl);
  const official = page.locator('.overview [data-source="OFFICIAL"]');
  const market = page.locator('.overview [data-source="MARKET"]');
  await expect(official).toContainText("+2,10%");
  await expect(market).toContainText("+1,68%");
  await expect(official).toContainText("71,80");
  await expect(market).toContainText("72,80");
  await expect(page.locator("#comparison")).toContainText("19 окт. 2026");
  await expect(page.locator("#comparison")).toContainText("20 окт. 2026");
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
});

test("one table changes row order by either source without separating regions", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison tbody tr")).toHaveCount(3);
  await expect(page.locator("#comparison table")).toHaveCount(1);
  await page
    .locator("#comparison")
    .screenshot({ path: "/private/tmp/fuel-comparison-unified.png" });
  const count = requests.length;
  await page.getByLabel("Порядок регионов", { exact: true }).selectOption("MARKET:PRICE:DESC");
  await expect(page.getByLabel("Порядок регионов", { exact: true })).toHaveValue(
    "MARKET:PRICE:DESC",
  );
  const after = requests.slice(count);
  expect(after).toHaveLength(1);
  expect(after[0]?.query.get("source")).toBe("MARKET");
  expect(after[0]?.query.get("frequency")).toBe("DAILY");
  await expect(page.locator("#comparison tbody tr").first()).toContainText("Ставропольский край");
  await expect(
    page.locator("#comparison tbody tr").first().locator('td[data-source="OFFICIAL"]'),
  ).toContainText("73,10");
  await expect(
    page.locator("#comparison tbody tr").first().locator('td[data-source="MARKET"]'),
  ).toContainText("74,10");
  await page.getByLabel("Порядок регионов", { exact: true }).selectOption("OFFICIAL:PRICE:ASC");
  await expect(page.locator("#comparison tbody tr").first()).toContainText("Ростовская область");
  await expect(page.getByRole("link", { name: "Рассчитать заправку" })).toBeVisible();
  await expect(page.locator("#comparison table")).toHaveCount(1);
  await expect(page.locator('#comparison td[data-source="OFFICIAL"]')).toHaveCount(3);
  await expect(page.locator('#comparison td[data-source="MARKET"]')).toHaveCount(3);
});

for (const failed of ["OFFICIAL", "MARKET"] as const) {
  test(`failed ${failed} comparison leaves the other comparison and calculator available`, async ({
    page,
  }) => {
    await mockApi(page);
    await page.route("**/fuel-prices/comparison?**", async (route) => {
      if (new URL(route.request().url()).searchParams.get("source") === failed) {
        await route.fulfill({ status: 503, json: apiError("ServiceUnavailable") });
      } else {
        await route.fallback();
      }
    });
    await page.goto(selectionUrl);
    const available = failed === "OFFICIAL" ? "MARKET" : "OFFICIAL";
    await expect(page.locator(`#comparison td[data-source="${available}"]`)).toHaveCount(3);
    await expect(page.locator("#comparison")).toContainText("Не удалось загрузить");
    await expect(page.getByRole("link", { name: "Рассчитать заправку" })).toBeVisible();
  });
}

test("official summary and comparison work with an empty market archive", async ({ page }) => {
  await mockApi(page);
  await page.route("**/fuel-prices/**", async (route) => {
    const url = new URL(route.request().url());
    if (url.searchParams.get("source") !== "MARKET") {
      await route.fallback();
      return;
    }
    const data = responses(selection, "v1", "empty");
    if (url.pathname.endsWith("overview")) {
      await route.fulfill({ json: data.overview });
    } else if (url.pathname.endsWith("comparison")) {
      await route.fulfill({ json: data.comparison });
    } else if (url.pathname.endsWith("trend")) {
      await route.fulfill({ json: data.trend });
    } else {
      await route.fallback();
    }
  });
  await page.goto(selectionUrl);
  await expect(page.locator('.overview [data-source="OFFICIAL"]')).toContainText("72,50");
  await expect(page.locator('.overview [data-source="MARKET"]')).toContainText(
    "Наблюдений этого источника пока нет",
  );
  await expect(page.locator("#comparison .comparison-row")).toHaveCount(3);
  await expect(page.locator("#comparison")).toContainText("Нет наблюдений");
  await expect(page.getByRole("link", { name: "Рассчитать заправку" })).toBeVisible();
  await page.screenshot({
    path: "/private/tmp/fuel-official-and-empty-market.png",
    fullPage: true,
  });
});

for (const width of [360, 1440]) {
  test(`summary slots keep their geometry with and without data at ${width}px`, async ({
    page,
  }) => {
    await page.setViewportSize({ width, height: 1000 });
    const options: { mode: "normal" | "empty" } = { mode: "normal" };
    await mockApi(page, options);
    await page.goto(selectionUrl);
    await expect(page.locator('.overview [data-source="OFFICIAL"] .price-value')).toBeVisible();
    const geometry = () =>
      page.locator(".overview").evaluate((root) => {
        const top = root.getBoundingClientRect().top;
        return [
          ...root.querySelectorAll(".overview-price, .overview-stats, .overview-stat-value"),
        ].map((element) => {
          const box = element.getBoundingClientRect();
          return { top: Math.round(box.top - top), height: Math.round(box.height) };
        });
      });
    if (width === 360) {
      await page.getByRole("button", { name: "Изменить выбор", exact: true }).click();
    }
    const filled = await geometry();
    options.mode = "empty";
    await page
      .getByRole("group", { name: "Топливо", exact: true })
      .getByText("АИ-92", { exact: true })
      .click();
    await expect(page.locator('.overview [data-source="OFFICIAL"]')).toContainText(
      "Наблюдений этого источника пока нет",
    );
    await expect(page.locator('.overview [data-source="MARKET"]')).toContainText(
      "Наблюдений этого источника пока нет",
    );
    expect(await geometry()).toEqual(filled);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    await page.screenshot({ path: `/private/tmp/fuel-fixed-summary-${width}.png`, fullPage: true });
  });
}

test("comparison keeps both sources in one accessible row per region on mobile", async ({
  page,
}) => {
  await page.setViewportSize({ width: 360, height: 900 });
  await mockApi(page);
  await page.goto(selectionUrl);
  const table = page.locator("#comparison table");
  await expect(table).toHaveCount(1);
  await expect(table.locator("tbody tr")).toHaveCount(3);
  await expect(table.getByRole("columnheader")).toHaveCount(3);
  await expect(table.getByRole("rowheader")).toHaveCount(3);
  const row = table.locator("tbody tr").filter({ hasText: "Краснодарский край" });
  await expect(row.locator('td[data-source="OFFICIAL"]')).toContainText("72,50");
  await expect(row.locator('td[data-source="MARKET"]')).toContainText("73,50");
  await expect(row.getByRole("rowheader")).toContainText("Мой регион");
  const trigger = row.locator('td[data-source="OFFICIAL"] button');
  await trigger.click();
  await expect(page.getByRole("dialog")).toBeVisible();
  await page.getByRole("button", { name: "Закрыть", exact: true }).click();
  await expect(trigger).toBeFocused();
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(true);
  await page
    .locator("#comparison")
    .screenshot({ path: "/private/tmp/fuel-comparison-unified-360.png" });
});

test("a revision conflict preserves the chosen shared market order", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator("#comparison tbody tr")).toHaveCount(3);
  const before = requests.filter((item) => item.path === "overview").length;
  let conflict = true;
  await page.route("**/fuel-prices/comparison?**", async (route) => {
    const query = new URL(route.request().url()).searchParams;
    if (conflict && query.get("source") === "MARKET" && query.get("sortOrder") === "DESC") {
      conflict = false;
      await route.fulfill({ status: 409, json: apiError("DataVersionChanged") });
    } else {
      await route.fallback();
    }
  });
  await page.getByLabel("Порядок регионов", { exact: true }).selectOption("MARKET:PRICE:DESC");
  await expect(page.getByLabel("Порядок регионов", { exact: true })).toHaveValue(
    "MARKET:PRICE:DESC",
  );
  await expect(page.locator("#comparison tbody tr").first()).toContainText("Ставропольский край");
  expect(requests.filter((item) => item.path === "overview").length).toBe(before + 1);
});

test("equal prices and newer observations remain visible per source", async ({ page }) => {
  await mockApi(page);
  await page.route("**/fuel-prices/comparison?**", async (route) => {
    if (new URL(route.request().url()).searchParams.get("source") !== "OFFICIAL") {
      await route.fallback();
      return;
    }
    const data = responses({ ...selection, source: "OFFICIAL", frequency: "WEEKLY" }).comparison;
    data.comparison.allPricesEqual = true;
    data.comparison.hasNewerObservations = true;
    for (const row of data.comparison.data) {
      row.price = { ...price(row.region.okatoCode, "OFFICIAL"), priceRubPerLiter: "72.50" };
      row.differenceRubPerLiter = row.isBase ? null : "0.00";
      row.isLowest = false;
      row.isHighest = false;
    }
    await route.fulfill({ json: data });
  });
  await page.goto(selectionUrl);
  const header = page.locator('#comparison th[data-source="OFFICIAL"]');
  await expect(header).toContainText("Цены одинаковы");
  await expect(header).toContainText("Есть более свежие индивидуальные цены");
  await expect(
    page.locator('#comparison td[data-source="OFFICIAL"] .comparison-lowest'),
  ).toHaveCount(0);
  await expect(page.locator("#comparison table")).toHaveCount(1);
});

test("comparison appears only after adding another region and disappears when removed", async ({
  page,
}) => {
  const requests = await mockApi(page);
  const query = new URLSearchParams({
    region: selection.baseRegionCode,
    regions: selection.baseRegionCode,
    fuel: "DIESEL",
    from: selection.dateFrom,
    to: selection.dateTo,
  });
  await page.goto(`/?${query}`);
  await expect(page.locator(".overview")).toBeVisible();
  await expect(page.locator("#comparison")).toHaveCount(0);
  await expect(page.locator(".chart .series")).toHaveCount(2);
  await expect(page.getByRole("link", { name: "Сравнение", exact: true })).toHaveCount(0);
  await expect(page.getByText("Ниже среди выбранных", { exact: true })).toHaveCount(0);
  expect(requests.filter((item) => item.path.endsWith("comparison"))).toHaveLength(0);
  await page.locator(".extra-regions summary").click();
  const extra = page.getByRole("checkbox", { name: "Ростовская область", exact: true });
  await extra.check();
  await expect(page.locator("#comparison tbody tr")).toHaveCount(2);
  await expect(page.locator(".chart .series")).toHaveCount(4);
  await expect(page.getByText("Ниже среди выбранных", { exact: true })).toHaveCount(2);
  const comparisons = requests.filter((item) => item.path.endsWith("comparison")).length;
  await extra.uncheck();
  await expect(page.locator("#comparison")).toHaveCount(0);
  await expect(page.getByText("Ниже среди выбранных", { exact: true })).toHaveCount(0);
  await expect(page.locator(".overview")).toBeVisible();
  await expect(page.locator(".chart .series")).toHaveCount(2);
  expect(requests.filter((item) => item.path.endsWith("comparison"))).toHaveLength(comparisons);
  await expect(page.locator("#calculator")).toHaveCount(0);
});

test("refueling links transfer the end date and return with the default range", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".overview")).toBeVisible();
  await page
    .getByRole("group", { name: "Топливо", exact: true })
    .getByText("АИ-95", { exact: true })
    .click();
  await expect(page.locator(".applied-context")).toContainText("АИ-95");
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page).toHaveURL(/from=2026-09-29/);
  await page.getByLabel("По дату", { exact: true }).fill("2026-10-25");
  const current = new URL(page.url()).searchParams.toString();
  const target = new URLSearchParams(current);
  target.delete("from");
  target.delete("to");
  target.set("date", "2026-10-25");
  const headerLink = page
    .getByRole("navigation")
    .getByRole("link", { name: "Заправка", exact: true });
  const bottomLink = page.getByRole("link", { name: "Рассчитать заправку" });
  await expect(headerLink).toHaveAttribute("href", `/refueling?${target}`);
  await expect(bottomLink).toHaveAttribute("href", `/refueling?${target}`);
  const before = requests.length;
  await headerLink.click();
  await expect(page).toHaveURL(`/refueling?${target}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Рассчитайте заправку");
  await expect(page.getByRole("form", { name: "Выбор данных" })).toHaveCount(1);
  await expect(page.getByLabel("Мой регион", { exact: true })).toHaveValue("Краснодарский край");
  await expect(page.getByRole("radio", { name: "АИ-95", exact: true })).toBeChecked();
  await expect(page.getByLabel("Дата заправки", { exact: true })).toHaveValue("2026-10-25");
  await expect(page.locator("#trend, #comparison, .overview")).toHaveCount(0);
  await expect(page.getByLabel("Период", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("С даты", { exact: true })).toHaveCount(0);
  await expect(page.getByLabel("По дату", { exact: true })).toHaveCount(0);
  await expect(page.locator("#calculator .filters")).toHaveCount(1);
  await expect(page.locator("#calculator #volume")).toHaveCount(1);
  expect(
    requests.slice(before).every((item) => item.path === "regions" || item.path === "fuel-types"),
  ).toBe(true);
  const beforeBack = requests.filter((item) => item.path === "overview").length;
  await page.goBack();
  await expect(page).toHaveURL(`/?${current}`);
  await expect(page.locator(".overview")).toBeVisible();
  await expect
    .poll(() => requests.filter((item) => item.path === "overview").length)
    .toBeGreaterThan(beforeBack);
  await page.goForward();
  await expect(page).toHaveURL(`/refueling?${target}`);
  await expect(page.getByRole("heading", { level: 1 })).toHaveText("Рассчитайте заправку");
  await page.reload();
  await expect(page.getByRole("radio", { name: "АИ-95", exact: true })).toBeChecked();
  await expect(page.getByLabel("Дата заправки", { exact: true })).toHaveValue("2026-10-25");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".source-calculation")).toHaveCount(2);
  expect(requests.filter((item) => item.path === "calculate").at(-1)?.body).toMatchObject({
    selection: {
      baseRegionCode: selection.baseRegionCode,
      regionCodes: selection.regionCodes,
      date: "2026-10-25",
      fuelType: "AI_95",
    },
  });
  await page.getByLabel("Дата заправки", { exact: true }).fill("2026-03-31");
  await expect(page.locator(".source-calculation")).toHaveCount(0);
  const back = new URLSearchParams(current);
  back.set("from", "2025-12-31");
  back.set("to", "2026-03-31");
  await expect(
    page.getByRole("navigation").getByRole("link", { name: "Цены и динамика", exact: true }),
  ).toHaveAttribute("href", `/?${back}`);
  await page.getByRole("link", { name: /К ценам и динамике/ }).click();
  await expect(page).toHaveURL(`/?${back}`);
  await expect(page.locator(".overview")).toBeVisible();
  await expect(page.locator("#calculator")).toHaveCount(0);
});

test("refueling can open without a selection and history restores filter changes", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto("/");
  await page.getByRole("navigation").getByRole("link", { name: "Заправка", exact: true }).click();
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeDisabled();
  await chooseRegion(page, "Краснодарский край");
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeEnabled();
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".total-value")).toHaveCount(2);
  await page
    .getByRole("group", { name: "Топливо", exact: true })
    .getByText("АИ-92", { exact: true })
    .click();
  await expect(page.locator(".total-value")).toHaveCount(0);
  await expect(page.getByLabel("Объём, л", { exact: true })).toHaveValue("600");
  await page.goBack();
  await expect(page.getByRole("radio", { name: "Дизель", exact: true })).toBeChecked();
  await page.goForward();
  await expect(page.getByRole("radio", { name: "АИ-92", exact: true })).toBeChecked();
  await page.getByLabel("Дата заправки", { exact: true }).fill("2026-10-01");
  await expect(page).toHaveURL(/date=2026-10-01/);
  await page.goBack();
  await expect(page.getByLabel("Дата заправки", { exact: true })).toHaveValue(selection.dateTo);
  await page.goForward();
  await expect(page.getByLabel("Дата заправки", { exact: true })).toHaveValue("2026-10-01");
  expect(requests.filter((item) => item.path === "calculate")).toHaveLength(1);
  expect(requests.some((item) => item.path === "overview" || item.path === "trend")).toBe(false);
});

test("invalid dates cancel a pending refueling calculation and discard its late result", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/refueling/calculate", async (route) => {
    await new Promise((resolve) => setTimeout(resolve, 200));
    await route.fulfill({ json: responses().calculation });
  });
  await page.goto(refuelingUrl);
  const pending = page.waitForRequest((request) => request.url().endsWith("/refueling/calculate"));
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await pending;
  await page.getByLabel("Дата заправки", { exact: true }).fill("");
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeDisabled();
  await expect(page.locator(".total-value")).toHaveCount(0);
  await expect(page.getByRole("alert")).toContainText("корректную дату");
  await page.getByLabel("Дата заправки", { exact: true }).fill("2099-01-01");
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeDisabled();
  await page.getByLabel("Дата заправки", { exact: true }).fill(selection.dateTo);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".source-calculation")).toHaveCount(2);
});

for (const width of [360, 768, 1440]) {
  test(`refueling page remains accessible and fits at ${width}px`, async ({ page }) => {
    await page.setViewportSize({ width, height: 1000 });
    await mockApi(page);
    await page.goto(refuelingUrl);
    const region = page.getByRole("combobox", { name: "Мой регион", exact: true });
    await region.focus();
    await expect(page.getByRole("listbox", { name: "Регионы" })).toBeVisible();
    expect(
      (await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze())
        .violations,
    ).toEqual([]);
    await region.press("Escape");
    await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
    await expect(page.locator(".source-calculation")).toHaveCount(2);
    expect(await page.evaluate(() => document.documentElement.scrollWidth <= innerWidth)).toBe(
      true,
    );
    expect(
      (await new AxeBuilder({ page }).withTags(["wcag2a", "wcag2aa", "wcag21aa"]).analyze())
        .violations,
    ).toEqual([]);
    await page.screenshot({
      path: `/private/tmp/fuel-refueling-page-${width}.png`,
      fullPage: true,
    });
  });
}
