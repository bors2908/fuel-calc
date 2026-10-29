import { expect, test } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { apiError, mockApi, selection, selectionUrl, responses, price } from "./fixtures";
import { normalizeVolume } from "../src/refueling/model";
import { number } from "../src/ui/format";
import { presetRange } from "../src/selection/model";

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

test("landing makes no price calls; apply uses current backend data every time", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto("/");
  await expect(page.getByLabel("Мой регион", { exact: true })).toBeVisible();
  expect(requests.some((request) => request.path === "overview")).toBe(false);
  await page.getByLabel("Найти регион").fill("Краснодар");
  await page.getByLabel("Мой регион", { exact: true }).selectOption(selection.baseRegionCode);
  await page.getByRole("button", { name: "Показать", exact: true }).click();
  await expect(page.locator(".overview")).toBeVisible();
  const before = requests.filter((request) => request.path === "overview").length;
  await page.getByRole("button", { name: "Показать", exact: true }).click();
  await expect
    .poll(() => requests.filter((request) => request.path === "overview").length)
    .toBe(before + 1);
  expect(
    await page.evaluate(() => ({ local: localStorage.length, session: sessionStorage.length })),
  ).toEqual({ local: 0, session: 0 });
});

test("source and frequency are drafts until apply; cancel and browser back restore selection", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  const before = requests.length;
  await page.locator(".data-parameters summary").click();
  await page.getByRole("radio", { name: "ГдеТопливо", exact: true }).check();
  await page.getByRole("radio", { name: "Месяцы", exact: true }).check();
  await expect(page.getByText("Есть изменения в фильтрах")).toBeVisible();
  expect(requests.length).toBe(before);
  await expect(page.locator(".applied-context")).toContainText("Росстат");
  await page.getByRole("button", { name: "Отменить изменения" }).click();
  await expect(page.getByRole("radio", { name: "Росстат", exact: true })).toBeChecked();
  await page.getByRole("radio", { name: "ГдеТопливо", exact: true }).check();
  await page.getByRole("button", { name: "Показать", exact: true }).click();
  await expect(page.locator(".applied-context")).toContainText("ГдеТопливо");
  await expect(page).toHaveURL(/source=WHEREFUEL/);
  await page.goBack();
  await expect(page.locator(".applied-context")).toContainText("Росстат");
  await expect(page.getByRole("radio", { name: "Росстат", exact: true })).toBeChecked();
  await page.goForward();
  await expect(page.locator(".applied-context")).toContainText("ГдеТопливо");
});

test("history keeps gaps, table and source detail need no point-by-point requests", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".chart .series path")).toHaveCount(3);
  const path = await page.locator(".chart .series path").first().getAttribute("d");
  expect(path?.match(/M/g)).toHaveLength(2);
  const before = requests.length;
  await page.getByRole("button", { name: "Таблица", exact: true }).click();
  await expect(page.getByRole("table")).toContainText("12 октября 2026");
  expect(requests.length).toBe(before);
  const trigger = page.locator(".overview").getByRole("button", { name: "Об источнике и дате" });
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
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  const overviewCount = requests.filter((request) => request.path === "overview").length;
  const comparisonCount = requests.filter((request) => request.path === "comparison").length;
  await page.getByLabel("Порядок строк").selectOption("PRICE:DESC");
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
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.getByLabel("Объём, л", { exact: true }).fill("0.001");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator("#volume-error")).toContainText("Введите");
  expect(requests.filter((request) => request.path === "calculate")).toHaveLength(0);
  await page.getByLabel("Объём, л", { exact: true }).fill("600,00");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".total-value")).toHaveText(/43\s500,00\s₽/);
  expect(requests.find((request) => request.path === "calculate")?.body).toMatchObject({
    selection,
    volumeLiters: "600.00",
    expectedSnapshotId: "snapshot-v1",
  });
  await page.getByLabel("Объём, л", { exact: true }).fill("601");
  await expect(page.locator(".total-value")).toHaveCount(0);
  await page.getByLabel("Объём, л", { exact: true }).fill("600");
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".total-value")).toBeVisible();
  await page.getByLabel("Топливо", { exact: true }).selectOption("AI_95");
  await expect(page.locator(".total-value")).toBeVisible();
  await page.getByRole("button", { name: "Показать", exact: true }).click();
  await expect(page.locator(".total-value")).toHaveCount(0);
  await expect(page.getByLabel("Объём, л", { exact: true })).toHaveValue("600");
});

test("a changed calculation snapshot refreshes the page to the POST revision", async ({ page }) => {
  const requests = await mockApi(page, { changedCalculation: true });
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".snapshot-notice")).toBeVisible();
  expect(
    requests.some(
      (request) => request.path === "overview" && request.query.get("dataVersion") === "v2",
    ),
  ).toBe(true);
  expect(
    requests.some(
      (request) => request.path === "trend" && request.query.get("dataVersion") === "v2",
    ),
  ).toBe(true);
});

test("archive revision conflict retries once, then stops", async ({ page }) => {
  const requests = await mockApi(page, { conflicts: 5 });
  await page.goto("/");
  await page.getByLabel("Мой регион", { exact: true }).selectOption(selection.baseRegionCode);
  await page.getByRole("button", { name: "Показать", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Данные обновляются");
  expect(requests.filter((request) => request.path === "overview")).toHaveLength(2);
  await expect(page.getByRole("button", { name: "Повторить", exact: true })).toBeEnabled();
});

test("one archive conflict recovers; individual block failure keeps the overview", async ({
  page,
}) => {
  await mockApi(page, { conflicts: 1 });
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.unrouteAll({ behavior: "wait" });
  await mockApi(page, { failTrend: true });
  await page.getByRole("button", { name: "Показать", exact: true }).click();
  await expect(page.getByRole("alert")).toContainText("Не удалось загрузить историю");
  await expect(page.locator(".overview")).toBeVisible();
});

test("late response cannot replace a newer selection", async ({ page }) => {
  await mockApi(page, { delayBase: selection.baseRegionCode });
  await page.goto(selectionUrl);
  await page.getByLabel("Мой регион", { exact: true }).selectOption("61000000000");
  await page.getByRole("button", { name: /Обновить выбор|Показать/ }).click();
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
  await expect(page.locator(".overview .eyebrow")).toContainText("Ростовская область");
});

for (const mode of ["empty", "no-common"] as const) {
  test(`${mode}: missing prices never become zero or a partial calculation`, async ({ page }) => {
    await mockApi(page, { mode });
    await page.goto(selectionUrl);
    await expect(page.locator(".comparison-row")).toHaveCount(3);
    await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeDisabled();
    await expect(page.locator(".total-value")).toHaveCount(0);
    await expect(page.locator(".comparison-section")).toContainText(
      mode === "empty" ? "нет наблюдений" : "Общего периода нет",
    );
  });
}

test("invalid backend payload fails validation, rather than rendering a price", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/fuel-prices/overview?**", (route) =>
    route.fulfill({
      json: { ...apiError("InternalError"), latestPrice: { priceRubPerLiter: 72.5 } },
    }),
  );
  await page.goto(selectionUrl);
  await expect(page.getByRole("alert")).toContainText("Не удалось загрузить данные");
  await expect(page.locator(".overview")).toHaveCount(0);
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
    await expect(page.locator(".comparison-row")).toHaveCount(3);
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    await page.getByRole("button", { name: "Таблица", exact: true }).focus();
    await page.keyboard.press("Enter");
    await expect(page.getByRole("table")).toBeVisible();
    expect(
      await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth),
    ).toBe(true);
    expect(errors).toEqual([]);
    await page.screenshot({ path: `/private/tmp/fuel-frontend-${width}.png`, fullPage: true });
  });
}

test("coverage is fetched on demand and fetched again after reopening", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  expect(requests.filter((item) => item.path === "coverage")).toHaveLength(0);
  const toggle = page.getByRole("button", { name: "Покрытие архива", exact: true });
  await toggle.click();
  await expect(page.locator(".coverage")).toContainText("22 наблюдений");
  const first = requests.filter((item) => item.path === "coverage").length;
  await toggle.click();
  await toggle.click();
  await expect
    .poll(() => requests.filter((item) => item.path === "coverage").length)
    .toBeGreaterThan(first);
});

test("sample-based price explains fallback and partial station coverage", async ({ page }) => {
  await mockApi(page);
  const overview = responses().overview;
  const sampled = price(selection.baseRegionCode);
  sampled.source = {
    provider: { code: "WHEREFUEL", name: "ГдеТопливо" },
    updateKey: "sample",
    aggregationMethod: "STATION_SAMPLE_MEAN",
    sample: {
      policyCode: "TOP20_FRESH7D_MIN5_V1",
      minStationCount: 5,
      dayCount: 4,
      expectedDayCount: 7,
      collectedAt: "2026-10-29T10:00:00Z",
    },
  };
  overview.latestPrice = sampled;
  await page.route("**/fuel-prices/overview?**", (route) => route.fulfill({ json: overview }));
  await page.goto(selectionUrl);
  await expect(page.locator(".overview")).toContainText("Резерв: ГдеТопливо");
  await expect(page.locator(".overview")).toContainText("4/7 дней · неполный период");
});

test("four regions and one point remain usable with text zoom and accessible semantics", async ({
  page,
}) => {
  await mockApi(page);
  const singlePoint = responses({
    ...selection,
    regionCodes: [...selection.regionCodes, "01000000000"],
  });
  singlePoint.trend.data = singlePoint.trend.data.map((series) => ({
    ...series,
    points: series.points.slice(-1),
  }));
  await page.route("**/fuel-prices/trend?**", (route) =>
    route.fulfill({ json: singlePoint.trend }),
  );
  await page.goto(
    selectionUrl.replace(
      "regions=23000000000%2C61000000000%2C26000000000",
      "regions=23000000000%2C61000000000%2C26000000000%2C01000000000",
    ),
  );
  await expect(page.locator(".chart circle")).toHaveCount(4);
  await expect(page.locator(".comparison-row")).toHaveCount(4);
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
          getComputedStyle(element).position !== "absolute"
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
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page
    .locator(".comparison-row")
    .filter({ hasText: "Ростовская область" })
    .getByRole("button", { name: "Сделать основным" })
    .click();
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
  await expect(page.locator(".comparison-row")).toHaveCount(3);
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

test("prices disappearing before POST produce an explanation and no partial total", async ({
  page,
}) => {
  await mockApi(page);
  await page.route("**/refueling/calculate", (route) =>
    route.fulfill({ status: 422, json: apiError("CalculationUnavailable") }),
  );
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".calculation-result")).toContainText("Нет сопоставимых цен");
  await expect(page.locator(".total-value")).toHaveCount(0);
});

test("a conflict while sorting performs only one complete refresh", async ({ page }) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  const before = requests.filter((item) => item.path === "overview").length;
  await page.route("**/fuel-prices/comparison?**", (route) =>
    route.fulfill({ status: 409, json: apiError("DataVersionChanged") }),
  );
  await page.getByLabel("Порядок строк").selectOption("PRICE:DESC");
  await expect(page.getByRole("alert")).toContainText("Данные обновляются");
  expect(requests.filter((item) => item.path === "overview").length).toBe(before + 1);
});
