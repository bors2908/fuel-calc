import { expect, test } from "@playwright/test";
import type { Page } from "@playwright/test";
import AxeBuilder from "@axe-core/playwright";
import { apiError, mockApi, selection, selectionUrl, responses, price } from "./fixtures";
import { normalizeVolume } from "../src/refueling/model";
import { number } from "../src/ui/format";
import { presetRange } from "../src/selection/model";

async function chooseRegion(page: Page, name: string) {
  await page.getByRole("combobox", { name: "Мой регион", exact: true }).fill(name);
  await page.getByRole("option", { name, exact: true }).click();
}

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
  await expect(page.locator(".comparison-row")).toHaveCount(3);
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

test("fuel segments apply immediately when navigated with the keyboard", async ({ page }) => {
  await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
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

test("source and frequency apply immediately and browser history restores every choice", async ({
  page,
}) => {
  await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.locator(".data-parameters summary").click();
  await page.getByRole("radio", { name: "BenzUp", exact: true }).check();
  await expect(page.locator(".applied-context")).toContainText("BenzUp");
  await page.getByRole("radio", { name: "Месяцы", exact: true }).check();
  await expect(page.locator(".applied-context")).toContainText("Месяцы");
  await page.goBack();
  await expect(page.locator(".applied-context")).toContainText("Недели");
  await expect(page.getByRole("radio", { name: "Недели", exact: true })).toBeChecked();
  await page.goBack();
  await expect(page.locator(".applied-context")).toContainText("Росстат");
  await expect(page.getByRole("radio", { name: "Росстат", exact: true })).toBeChecked();
  await page.goForward();
  await expect(page.locator(".applied-context")).toContainText("BenzUp");
});

test("incomplete and invalid dates keep the displayed context and never request prices", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.getByRole("button", { name: "Рассчитать", exact: true }).click();
  await expect(page.locator(".total-value")).toBeVisible();
  const before = requests.length;
  const originalUrl = page.url();
  await page.getByLabel("С даты", { exact: true }).fill("");
  await expect(page.locator(".total-value")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeDisabled();
  await page.getByLabel("По дату", { exact: true }).fill("2026-01-01");
  await page.getByLabel("С даты", { exact: true }).fill("2026-02-01");
  await expect(page.getByRole("alert")).toContainText("корректный период");
  expect(requests.length).toBe(before);
  expect(page.url()).toBe(originalUrl);
  await expect(page.locator(".overview")).toBeVisible();
  await page.getByLabel("По дату", { exact: true }).fill("2026-03-01");
  await expect(page).toHaveURL(/from=2026-02-01&to=2026-03-01/);
  await expect(page.getByRole("alert")).toHaveCount(0);
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeEnabled();
  expect(requests.length).toBeGreaterThan(before);
});

test("period presets, additional regions and reset apply without confirmation", async ({
  page,
}) => {
  const requests = await mockApi(page);
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page).toHaveURL(/from=2026-09-29/);
  await expect(page.getByLabel("С даты", { exact: true })).toHaveValue("2026-09-29");
  await page.locator(".extra-regions summary").click();
  await page.getByRole("checkbox", { name: /очень длинным/ }).check();
  await expect(page.locator(".comparison-row")).toHaveCount(4);
  await page.locator(".data-parameters summary").click();
  await page.getByRole("radio", { name: "BenzUp", exact: true }).check();
  await expect(page.locator(".applied-context")).toContainText("BenzUp");
  await page.getByRole("radio", { name: "Месяцы", exact: true }).check();
  await expect(page.locator(".applied-context")).toContainText("Месяцы");
  await page.getByRole("button", { name: "Сбросить фильтры" }).click();
  await expect(page.locator(".comparison-row")).toHaveCount(1);
  await expect(page.getByLabel("С даты", { exact: true })).toHaveValue(selection.dateFrom);
  expect(
    requests
      .filter((item) => item.path === "overview")
      .at(-1)
      ?.query.get("preferredSource"),
  ).toBe("BENZUP");
  await expect(page.getByRole("radio", { name: "Недели", exact: true })).toBeChecked();
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
  await page
    .getByRole("group", { name: "Топливо", exact: true })
    .getByText("АИ-95", { exact: true })
    .click();
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
  await expect(page.locator(".comparison-row")).toHaveCount(3);
  await page.unrouteAll({ behavior: "wait" });
  await mockApi(page, { failTrend: true });
  await page.getByLabel("Период", { exact: true }).selectOption("1");
  await expect(page.getByRole("alert")).toContainText("Не удалось загрузить историю");
  await expect(page.locator(".overview")).toBeVisible();
});

test("late response cannot replace a newer selection", async ({ page }) => {
  await mockApi(page, { delayBase: selection.baseRegionCode });
  await page.goto(selectionUrl);
  await chooseRegion(page, "Ростовская область");
  await expect(page.locator(".applied-context strong")).toContainText("Ростовская область");
  await expect(page.locator(".overview .overview-label").first()).toContainText(
    "Ростовская область",
  );
});

test("an incomplete date cancels an in-flight selection and keeps the previous screen", async ({
  page,
}) => {
  const requests = await mockApi(page, { delayBase: "61000000000" });
  await page.goto(selectionUrl);
  await expect(page.locator(".comparison-row")).toHaveCount(3);
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
  await expect(page.getByRole("button", { name: "Рассчитать", exact: true })).toBeDisabled();
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

test("BenzUp price explains fallback and partial measurement coverage", async ({ page }) => {
  await mockApi(page);
  const overview = responses().overview;
  const sampled = price(selection.baseRegionCode);
  sampled.source = {
    provider: { code: "BENZUP", name: "BenzUp" },
    updateKey: "sample",
    aggregationMethod: "REGIONAL_INDEX_MEAN",
    sample: {
      policyCode: "BENZUP_DAILY_MEAN_V1",
      measurementCount: 5,
      dayCount: 4,
      expectedDayCount: 7,
      collectedAt: "2026-10-29T10:00:00Z",
    },
  };
  overview.latestPrice = sampled;
  await page.route("**/fuel-prices/overview?**", (route) => route.fulfill({ json: overview }));
  await page.goto(selectionUrl);
  await expect(page.locator(".overview")).toContainText("Резерв: BenzUp");
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
