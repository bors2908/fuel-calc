import { useCallback, useState } from "react";
import type { Selection } from "../api/types";
import type { Catalog } from "../selection/model";
import { selectionHref, selectionKey } from "../selection/model";
import { useSelection } from "../selection/useSelection";
import { Filters } from "../selection/Filters";
import { SiteHeader } from "../app/SiteHeader";
import { regionName } from "../regions/regionName";
import { Feedback, Loading } from "../ui/Feedback";
import { date } from "../ui/format";
import { Overview } from "./Overview";
import { Trend } from "./Trend";
import { Comparison } from "./Comparison";
import { ArchiveInfo } from "./ArchiveInfo";
import { usePrices } from "./usePrices";
import { ObservationDialog } from "../observations/ObservationDialog";

export function PricesPage({ catalog }: { catalog: Catalog }) {
  const [observation, setObservation] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const prices = usePrices();
  const { load, clear, cancelLoad } = prices;
  const apply = useCallback(
    (value: Selection) => {
      setObservation(null);
      return load(value);
    },
    [load],
  );
  const reset = useCallback(() => {
    setObservation(null);
    clear();
  }, [clear]);
  const filters = useSelection(catalog, apply, reset, cancelLoad);
  const { selection, applied, error: validation } = filters;
  const closeObservation = useCallback(() => setObservation(null), []);
  // Returning to the landing URL unmounts results rather than reusing a previous selection.
  const page = applied ? prices.page : null;
  const showComparison = Boolean(
    page && selection.regionCodes.length > 1 && page.selection.regionCodes.length > 1,
  );
  const displayMatches = page && applied && selectionKey(page.selection) === selectionKey(applied);
  const blocked = !filters.valid || prices.busy || !displayMatches;
  function changeSelection(value: Selection) {
    setObservation(null);
    setFiltersOpen(true);
    filters.change(value);
  }
  function refresh() {
    if (applied && !validation) {
      void load(applied);
    }
  }
  return (
    <>
      <SiteHeader
        page="prices"
        selection={selection}
        showComparison={showComparison}
      />
      <main
        id="main"
        className="wrap"
      >
        <section className={`hero${page ? " hero-compact" : ""}`}>
          <p className="eyebrow">Для тех, кто планирует сезон</p>
          <h1>Топливо. С понятной ценой.</h1>
          {!page && <p>Сравните региональные цены и узнайте стоимость заправки вашей техники.</p>}
        </section>
        <button
          type="button"
          className="mobile-filter-toggle"
          aria-expanded={filtersOpen || !page}
          aria-controls="filter-panel"
          onClick={() => setFiltersOpen(!filtersOpen)}
        >
          Изменить выбор
        </button>
        <div
          id="filter-panel"
          className={`filter-panel${filtersOpen || !page ? " filters-open" : ""}`}
        >
          {catalog.regions.length === 0 && (
            <p
              className="empty-state"
              role="status"
            >
              Цены пока не загружены. Список регионов появится после первой загрузки данных.{" "}
              <button
                type="button"
                onClick={() => window.location.reload()}
              >
                Обновить список
              </button>
            </p>
          )}
          <Filters
            catalog={catalog}
            value={selection}
            error={validation}
            onChange={changeSelection}
          />
        </div>
        {prices.busy && (
          <Loading>
            {page
              ? "Обновляем данные. Ниже пока показан прежний выбор."
              : "Загружаем региональные цены…"}
          </Loading>
        )}
        {prices.error && <Feedback onRetry={refresh}>{prices.error}</Feedback>}
        {page ? (
          <div
            className="results"
            aria-busy={prices.busy}
          >
            <div className="applied-context">
              <strong>
                {regionName(page.overview.context.baseRegion)} ·{" "}
                {page.overview.context.fuelType.name}
              </strong>
              <span>
                {date(page.overview.context.dateFrom)} — {date(page.overview.context.dateTo)}
              </span>
            </div>
            <Overview
              value={page.marketOverview}
              official={page.officialOverview}
              showComparison={showComparison}
              onObservation={setObservation}
              onRetry={refresh}
            />
            <Trend
              key={page.overview.context.key}
              context={page.overview.context}
              market={page.trend}
              official={page.officialTrend}
              onObservation={setObservation}
              onRetry={refresh}
            />
            {showComparison && (
              <Comparison
                official={page.officialComparison}
                market={page.comparison}
                regions={page.overview.context.regions}
                baseRegionCode={page.selection.baseRegionCode}
                ordering={page.comparisonOrder}
                disabled={blocked}
                onSort={(source, sorting) => {
                  void prices.sort(source, sorting);
                }}
                onBase={(code) => changeSelection({ ...page.selection, baseRegionCode: code })}
                onObservation={setObservation}
                onRetry={refresh}
              />
            )}
            <ArchiveInfo
              updates={{
                ...page.overview.sourceUpdates,
                ...page.trend.value?.sourceUpdates,
                ...page.officialTrend.value?.sourceUpdates,
              }}
            />
            {observation && (
              <ObservationDialog
                key={observation}
                id={observation}
                onClose={closeObservation}
              />
            )}
          </div>
        ) : (
          !prices.busy &&
          !prices.error && (
            <section className="landing-note">
              <p className="eyebrow">Региональные средние · открытые источники</p>
              <h2>Выберите регион. Остальное — по делу.</h2>
              <p>
                История цен, сравнение с другими регионами и стоимость вашего объёма — в одном
                выборе.
              </p>
              <div>
                <span>01 · Динамика без догадок</span>
                <span>02 · Сопоставимые периоды</span>
                <span>03 · Расчёт по вашему объёму</span>
              </div>
            </section>
          )
        )}
        <section className="refueling-callout">
          <div>
            <p className="eyebrow">От цены к расходам</p>
            <h2>Сколько будет стоить заправка?</h2>
            <p>Рассчитайте свой объём по официальной и рыночной цене.</p>
          </div>
          <a
            className="refueling-link"
            href={selectionHref(selection, "/refueling")}
          >
            Рассчитать заправку <span aria-hidden="true">↗</span>
          </a>
        </section>
      </main>
    </>
  );
}
