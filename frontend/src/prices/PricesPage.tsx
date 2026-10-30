import { useCallback, useState } from "react";
import type { PriceSelection } from "../selection/types";
import type { Catalog } from "../selection/types";
import { selectionHref, selectionKey, priceSelectionModel } from "../selection/model";
import { useSelection } from "../selection/useSelection";
import { EmptyRegionCatalog } from "../selection/EmptyRegionCatalog";
import { Filters } from "../selection/Filters";
import { SiteHeader } from "../app/SiteHeader";
import { Feedback, Loading } from "../ui/Feedback";
import { date } from "../ui/format";
import { Overview } from "./Overview";
import { Trend } from "./Trend";
import { Comparison } from "./Comparison";
import { ArchiveInfo } from "./ArchiveInfo";
import { usePrices } from "./usePrices";
import { ObservationDialog } from "../observations/ObservationDialog";

import "./prices-page.css";

export function PricesPage({ catalog }: { catalog: Catalog }) {
  const [observation, setObservation] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const prices = usePrices();
  const { load, clear, cancelLoad } = prices;
  const apply = useCallback(
    (value: PriceSelection) => {
      setObservation(null);
      return load(value);
    },
    [load],
  );
  const reset = useCallback(() => {
    setObservation(null);
    clear();
  }, [clear]);
  const filters = useSelection(catalog, priceSelectionModel, {
    onApply: apply,
    onClear: reset,
    onInvalidate: cancelLoad,
  });
  const { selection, applied, error: validation } = filters;
  const closeObservation = useCallback(() => setObservation(null), []);
  // Returning to the landing URL unmounts results rather than reusing a previous selection.
  const page = applied ? prices.page : null;
  const showComparison = Boolean(
    page && selection.regionCodes.length > 1 && page.selection.regionCodes.length > 1,
  );
  const displayMatches = page && applied && selectionKey(page.selection) === selectionKey(applied);
  const blocked = !filters.valid || prices.busy || !displayMatches;
  function changeSelection(value: PriceSelection) {
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
            <EmptyRegionCatalog onReload={() => window.location.reload()} />
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
                {page.context.baseRegion.name} · {page.context.fuelType.name}
              </strong>
              <span>
                {date(page.context.dateFrom)} — {date(page.context.dateTo)}
              </span>
            </div>
            <Overview
              market={page.sources.MARKET.overview}
              official={page.sources.OFFICIAL.overview}
              showComparison={showComparison}
              onObservation={setObservation}
            />
            <Trend
              key={selectionKey(page.selection)}
              context={page.context}
              market={page.sources.MARKET.trend}
              official={page.sources.OFFICIAL.trend}
              onObservation={setObservation}
              serverDate={page.meta.serverDate}
            />
            {showComparison && (
              <Comparison
                official={page.sources.OFFICIAL.comparison}
                market={page.sources.MARKET.comparison}
                baseRegionCode={page.selection.baseRegionCode}
                ordering={page.comparisonOrder}
                disabled={blocked}
                onSort={(sorting) => {
                  void prices.sort(sorting);
                }}
                onBase={(code) => changeSelection({ ...page.selection, baseRegionCode: code })}
                onObservation={setObservation}
              />
            )}
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
              <h2>Выберите регион и мы все рассчитаем.</h2>
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
        {page && <ArchiveInfo updates={page.sourceUpdates} />}
      </main>
    </>
  );
}
