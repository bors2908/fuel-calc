import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import { readFile } from "node:fs/promises";
import AxeBuilder from "@axe-core/playwright";
import { calculation, mockApi, selection } from "./fixtures";
import { savedStorageKey } from "../src/refueling/saved/storage";
import { emptyList, parseSavedList, savedTotals, snapshot } from "../src/refueling/saved/model";
import { savedCsv } from "../src/refueling/saved/csv";
import { decode } from "../src/api/client";

const singleSelection = { ...selection, regionCodes: [selection.baseRegionCode] };
const singleUrl = `/refueling?region=${selection.baseRegionCode}&regions=${selection.baseRegionCode}&fuel=DIESEL&date=${selection.dateTo}`;
const list = (page: Page) =>
  page.getByRole("region", { name: "План закупок топлива", exact: true });
const add = (page: Page) => page.getByRole("button", { name: /Добавить в план/ });
async function save(page: Page, volume: string) {
  await page.getByLabel("Объём, л", { exact: true }).fill(volume);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(add(page)).toBeEnabled();
  await add(page).click();
  await expect(add(page)).toBeDisabled();
}

test("saved totals use exact kopecks and preserve missing source coverage", () => {
  const first = snapshot(calculation(singleSelection));
  const second = snapshot(calculation(singleSelection));
  const official = first.quotes.OFFICIAL;
  const next = second.quotes.OFFICIAL;
  if (!official || !next) {
    throw new Error("Missing fixture price");
  }
  official.totalRub = "0.10";
  next.totalRub = "0.20";
  first.quotes.MARKET = null;
  const totals = savedTotals([first, second]);
  expect(totals.OFFICIAL).toEqual({ amount: "0.30", count: 2 });
  expect(totals.MARKET).toEqual({ amount: "44100.00", count: 1 });
  expect(totals.volumeLiters).toBe("1200.00");
});

test("saved entries are independent snapshots of the calculation", () => {
  const result = calculation(singleSelection);
  const entry = snapshot(result);
  const source = result.results[0]?.comparison?.data[0];
  if (!source) {
    throw new Error("Missing fixture price");
  }
  entry.fuel.name = "Сохранённое имя";
  source.price.priceRubPerLiter = "999.00";
  source.totalRub = "999.00";
  expect(result.context.fuelType.name).toBe("Дизельное топливо");
  expect(entry.quotes.OFFICIAL?.price.priceRubPerLiter).toBe("72.50");
  expect(entry.quotes.OFFICIAL?.totalRub).toBe("43500.00");
});

test("stored records are versioned, bounded and validated before rendering", () => {
  const entry = snapshot(calculation(singleSelection));
  const value = {
    version: 1,
    region: calculation(singleSelection).context.baseRegion,
    entries: [entry],
  };
  expect(parseSavedList(value)).toEqual(value);
  expect(() => parseSavedList({ ...value, version: 2 })).toThrow();
  expect(() =>
    parseSavedList({
      ...value,
      entries: Array.from({ length: 11 }, () => ({ ...entry, id: crypto.randomUUID() })),
    }),
  ).toThrow();
  expect(() => parseSavedList({ ...value, entries: [entry, entry] })).toThrow();
  expect(() =>
    parseSavedList({ ...value, entries: [{ ...entry, volumeLiters: "NaN" }] }),
  ).toThrow();
  expect(() =>
    parseSavedList({ ...value, entries: [{ ...entry, quotes: { OFFICIAL: null, MARKET: null } }] }),
  ).toThrow();
  expect(() => snapshot(calculation())).toThrow();
});

test("CSV quotes text, neutralizes formula prefixes and identifies partial totals", () => {
  const entry = snapshot(calculation(singleSelection));
  entry.fuel.name = '@SUM(1;2) "топливо"';
  entry.quotes.MARKET = null;
  const region = { okatoCode: selection.baseRegionCode, name: '\t=HYPERLINK("example")' };
  const csv = savedCsv({ version: 1, region, entries: [entry] });
  expect(csv.startsWith("\uFEFF")).toBe(true);
  expect(csv).toContain('"\'@SUM(1;2) ""топливо"""');
  expect(csv).toContain('"\'\t=HYPERLINK(""example"")"');
  expect(csv).toContain('"Частичный итог: 0/1"');
  expect(csv.split("\r\n")).toHaveLength(4);
  expect(savedTotals(emptyList().entries).OFFICIAL.amount).toBeNull();
});

test("single-region refuelings persist and export the same displayed totals", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(singleUrl);
  await save(page, "600");
  await save(page, "200");
  await expect(list(page).locator("tbody tr")).toHaveCount(2);
  await expect(list(page).locator('tfoot [data-source="OFFICIAL"]')).toContainText("58 000,00");
  await expect(list(page).locator('tfoot [data-source="MARKET"]')).toContainText("58 800,00");
  await expect(list(page).locator("tfoot")).toContainText("800,00");
  const before = requests.filter((item) => item.path === "calculate").length;
  await page.reload();
  await expect(list(page).locator("tbody tr")).toHaveCount(2);
  await expect(list(page).locator('tfoot [data-source="OFFICIAL"]')).toContainText("58 000,00");
  expect(requests.filter((item) => item.path === "calculate")).toHaveLength(before);
  const pending = page.waitForEvent("download");
  await list(page).getByRole("button", { name: "Скачать CSV" }).click();
  const download = await pending;
  expect(download.suggestedFilename()).toBe(`plan-zakupok-topliva-${selection.baseRegionCode}.csv`);
  const path = await download.path();
  if (!path) {
    throw new Error("Missing CSV download");
  }
  const bytes = await readFile(path);
  expect(bytes.subarray(0, 3).toString("hex")).toBe("efbbbf");
  expect(bytes.toString("utf8")).toContain('"58000,00"');
  expect(bytes.toString("utf8")).toContain('"58800,00"');
  expect(bytes.toString("utf8").split("\r\n")).toHaveLength(5);
  expect(requests.filter((item) => item.path === "calculate")).toHaveLength(before);
});

test("changing region or selecting multiple regions retains the list but blocks adding", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto(singleUrl);
  await save(page, "600");
  const region = page.getByRole("combobox", { name: "Мой регион" });
  await region.fill("Ростов");
  await region.press("Enter");
  await expect(page.locator(".save-refueling")).toContainText("Вернитесь к нему или очистите план");
  await expect(add(page)).toBeDisabled();
  await expect(list(page)).toContainText("Краснодарский край");
  await expect(list(page).locator("tbody tr")).toHaveCount(1);
  await region.fill("Краснодар");
  await region.press("Enter");
  await page.locator(".extra-regions summary").click();
  await page.getByRole("checkbox", { name: "Ростовская область" }).check();
  await expect(page.locator(".save-refueling")).toContainText("ровно один регион");
  await expect(add(page)).toBeDisabled();
  await expect(list(page).locator("tbody tr")).toHaveCount(1);
});

test("forecast snapshots and partial source totals survive reload", async ({ page }) => {
  await mockApi(page);
  let future = false;
  await page.route("**/api/v1/refueling/calculate", (route) => {
    const input = decode("RefuelingCalculateRequest", route.request().postDataJSON());
    const value = calculation(
      { ...singleSelection, dateFrom: input.selection.date, dateTo: input.selection.date },
      input.volumeLiters,
    );
    if (future) {
      const official = value.results.find((item) => item.source === "OFFICIAL")?.comparison?.data[0]
        ?.price;
      const market = value.results.find((item) => item.source === "MARKET");
      if (!official || !market) {
        throw new Error("Missing calculation fixture");
      }
      official.observationId = null;
      official.period.frequency = "WEEKLY";
      official.period.referenceDate = input.selection.date;
      official.forecast = {
        asOfDate: "2026-10-29",
        baseDate: "2026-10-19",
        basePriceRubPerLiter: "72.50",
        historicalYears: [2025],
        growthPercent: "0.000000",
        historySource: "OFFICIAL",
        method: "HISTORICAL_RELATIVE_CHANGE",
      };
      market.comparison = null;
      market.baseTotalRub = null;
      market.status = "NO_DATA";
    }
    return route.fulfill({ json: value });
  });
  await page.goto(singleUrl);
  await save(page, "600");
  future = true;
  await page.getByLabel("Дата заправки", { exact: true }).fill("2027-01-04");
  await save(page, "200");
  await expect(list(page).locator('tfoot [data-source="MARKET"]')).toContainText(
    "Частичный итог · 1 из 2",
  );
  await expect(
    list(page).locator("tbody tr").last().locator('[data-source="OFFICIAL"]'),
  ).toContainText("Прогноз на неделю");
  await page.reload();
  await expect(list(page).locator('tfoot [data-source="OFFICIAL"]')).toContainText("58 000,00");
  await expect(list(page).locator('tfoot [data-source="MARKET"]')).toContainText("44 100,00");
  await expect(list(page).locator('tfoot [data-source="MARKET"]')).toContainText(
    "Частичный итог · 1 из 2",
  );
  await expect(
    list(page).locator("tbody tr").last().locator('[data-source="OFFICIAL"]'),
  ).toContainText("Прогноз на неделю");
});

test("ten is a hard limit and deleting or clearing allows more entries", async ({ page }) => {
  await mockApi(page);
  await page.goto(singleUrl);
  for (let index = 1; index <= 10; index++) {
    await save(page, String(index));
  }
  await expect(list(page).locator("tbody tr")).toHaveCount(10);
  await page.getByLabel("Объём, л", { exact: true }).fill("11");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(add(page)).toBeDisabled();
  await expect(page.locator(".save-refueling")).toContainText("Сохранено 10");
  await list(page).getByRole("button", { name: "Удалить заправку 1", exact: true }).click();
  await expect(add(page)).toBeEnabled();
  await add(page).click();
  await expect(list(page).locator("tbody tr")).toHaveCount(10);
  await list(page).getByRole("button", { name: "Очистить план" }).click();
  await list(page).getByRole("button", { name: "Отмена" }).click();
  await expect(list(page).locator("tbody tr")).toHaveCount(10);
  await list(page).getByRole("button", { name: "Очистить план" }).click();
  await list(page).getByRole("button", { name: "Удалить все заправки" }).click();
  await expect(list(page).locator("tbody tr")).toHaveCount(0);
  expect(await page.evaluate((key) => localStorage.getItem(key), savedStorageKey)).toBeNull();
});

test("invalidated volume or date cannot save the previous calculation", async ({ page }) => {
  await mockApi(page);
  await page.goto(singleUrl);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(add(page)).toBeEnabled();
  await page.getByLabel("Объём, л", { exact: true }).fill("200");
  await expect(add(page)).toBeDisabled();
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(add(page)).toBeEnabled();
  await page.getByLabel("Дата заправки", { exact: true }).fill("2026-10-28");
  await expect(add(page)).toBeDisabled();
  await expect(list(page).locator("tbody tr")).toHaveCount(0);
});

test("storage failures keep a usable memory-only list with a warning", async ({ page }) => {
  await page.addInitScript(() => {
    Object.defineProperty(Storage.prototype, "setItem", {
      value() {
        throw new DOMException("Blocked", "SecurityError");
      },
    });
  });
  await mockApi(page);
  await page.goto(singleUrl);
  await save(page, "600");
  await expect(list(page).getByRole("alert")).toContainText("до закрытия страницы");
  await expect(list(page).locator("tbody tr")).toHaveCount(1);
  await expect(list(page).getByRole("button", { name: "Скачать CSV" })).toBeEnabled();
  await save(page, "200");
  await expect(list(page).locator("tbody tr")).toHaveCount(2);
});

test("corrupted storage is retained until the user explicitly clears it", async ({ page }) => {
  await page.addInitScript((key) => localStorage.setItem(key, "broken-json"), savedStorageKey);
  await mockApi(page);
  await page.goto(singleUrl);
  await expect(list(page).getByRole("alert")).toContainText("Не удалось прочитать");
  await save(page, "600");
  expect(await page.evaluate((key) => localStorage.getItem(key), savedStorageKey)).toBe(
    "broken-json",
  );
  await list(page).getByRole("button", { name: "Очистить план" }).click();
  await list(page).getByRole("button", { name: "Удалить все заправки" }).click();
  await expect(list(page).getByRole("alert")).toHaveCount(0);
  await save(page, "200");
  expect(
    await page.evaluate(
      (key) => JSON.parse(localStorage.getItem(key) ?? "{}") as unknown,
      savedStorageKey,
    ),
  ).toHaveProperty("entries");
});

test("saved refuelings remain accessible and fit the page at mobile width", async ({ page }) => {
  await page.setViewportSize({ width: 360, height: 1000 });
  await mockApi(page);
  await page.goto(singleUrl);
  await save(page, "600");
  expect(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth)).toBe(
    true,
  );
  const audit = await new AxeBuilder({ page })
    .withTags(["wcag2a", "wcag2aa", "wcag21aa"])
    .analyze();
  expect(audit.violations).toEqual([]);
  await page.screenshot({ path: "/private/tmp/fuel-saved-refuelings-mobile.png", fullPage: true });
});
