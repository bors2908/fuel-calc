import { useCallback, useEffect, useState } from "react";
import { api, errorMessage } from "../api/client";
import type { Selection } from "../api/types";
import { frequencyNames, sourceNames } from "../api/types";
import { Filters } from "../selection/Filters";
import {
  defaults,
  fromContext,
  readSelection,
  selectionError,
  selectionKey,
  writeSelection,
} from "../selection/model";
import type { Catalog } from "../selection/model";
import { Feedback, Loading } from "../ui/Feedback";
import { date } from "../ui/format";
import { Overview } from "../prices/Overview";
import { Trend } from "../prices/Trend";
import { Comparison } from "../prices/Comparison";
import { ArchiveInfo } from "../prices/ArchiveInfo";
import { usePrices } from "../prices/usePrices";
import { Calculator } from "../refueling/Calculator";
import { ObservationDialog } from "../observations/ObservationDialog";
import "./app.css";

export function App() {
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    void Promise.all([api.regions(controller.signal), api.fuels(controller.signal)])
      .then(([regions, fuels]) => {
        if (current) {
          setCatalog({
            regions: regions.data,
            fuels: fuels.data,
            serverDate: regions.meta.serverDate,
          });
        }
      })
      .catch((failure: unknown) => {
        if (current) {
          setError(errorMessage(failure));
        }
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [attempt]);
  return (
    <>
      <a
        className="skip-link"
        href="#main"
      >
        К содержимому
      </a>
      <header className="site-header wrap">
        <a
          className="brand"
          href="/"
        >
          <span
            className="brand-mark"
            aria-hidden="true"
          >
            ↗
          </span>
          <span>
            Топливо для хозяйства<small>Региональные цены. Понятные расходы.</small>
          </span>
        </a>
        <nav aria-label="Разделы страницы">
          <a href="#trend">Динамика</a>
          <a href="#comparison">Сравнение</a>
          <a href="#calculator">Заправка</a>
        </nav>
      </header>
      <main
        id="main"
        className="wrap"
      >
        {catalog ? (
          <Workspace catalog={catalog} />
        ) : (
          <section className="hero">
            <p className="eyebrow">Для тех, кто планирует сезон</p>
            <h1>Топливо. С понятной ценой.</h1>
            <p>Сравните региональные цены и узнайте стоимость заправки вашей техники.</p>
            {error ? (
              <Feedback
                onRetry={() => {
                  setError(null);
                  setAttempt(attempt + 1);
                }}
              >
                {error}
              </Feedback>
            ) : (
              <Loading>Загружаем список регионов и топлива…</Loading>
            )}
          </section>
        )}
      </main>
      <footer className="site-footer wrap">
        <span>Топливо для хозяйства</span>
        <span>Региональные средние · открытые источники</span>
      </footer>
    </>
  );
}

function Workspace({ catalog }: { catalog: Catalog }) {
  const [initial] = useState(() => readSelection(catalog));
  const [draft, setDraft] = useState(initial.selection);
  const [applied, setApplied] = useState<Selection | null>(
    initial.requested && !initial.error ? initial.selection : null,
  );
  const [validation, setValidation] = useState(initial.error);
  const [observation, setObservation] = useState<string | null>(null);
  const [filtersOpen, setFiltersOpen] = useState(false);
  const prices = usePrices();
  const { load, clear } = prices;
  const closeObservation = useCallback(() => setObservation(null), []);
  useEffect(() => {
    if (initial.requested && !initial.error) {
      void load(initial.selection);
    }
    function restore() {
      const restored = readSelection(catalog);
      setDraft(restored.selection);
      setValidation(restored.error);
      setObservation(null);
      setApplied(restored.requested && !restored.error ? restored.selection : null);
      if (restored.requested && !restored.error) {
        void load(restored.selection);
      } else {
        clear();
      }
    }
    window.addEventListener("popstate", restore);
    return () => window.removeEventListener("popstate", restore);
  }, [initial, catalog, load, clear]);
  // Returning to the landing URL unmounts results rather than reusing a previous selection.
  const page = applied ? prices.page : null;
  const dirty = selectionKey(draft) !== selectionKey(applied ?? defaults(catalog.serverDate));
  const displayMatches =
    page && applied && selectionKey(fromContext(page.overview.context)) === selectionKey(applied);
  const blocked = dirty || prices.busy || prices.calculating || !displayMatches;
  function apply(selection: Selection, scroll = false) {
    const error = selectionError(selection, catalog);
    setValidation(error);
    if (error) {
      return;
    }
    setObservation(null);
    setFiltersOpen(false);
    setDraft(selection);
    setApplied(selection);
    writeSelection(selection);
    void load(selection);
    if (scroll) {
      document.getElementById("calculator")?.scrollIntoView({ block: "start" });
    }
  }
  return (
    <>
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
        Изменить выбор · {sourceNames[draft.preferredSource]} · {frequencyNames[draft.frequency]}
      </button>
      <div
        id="filter-panel"
        className={`filter-panel${filtersOpen || !page ? " filters-open" : ""}`}
      >
        <Filters
          catalog={catalog}
          value={draft}
          dirty={dirty}
          busy={prices.busy}
          error={validation}
          onChange={(value) => {
            setDraft(value);
            setValidation(null);
          }}
          onCancel={() => {
            setDraft(applied ?? defaults(catalog.serverDate));
            setValidation(null);
          }}
          onApply={() => apply(draft)}
        />
      </div>
      {prices.busy && (
        <Loading>
          {page
            ? "Обновляем данные. Ниже пока показан прежний выбор."
            : "Загружаем региональные цены…"}
        </Loading>
      )}
      {prices.error && (
        <Feedback
          onRetry={() => {
            if (applied) {
              void load(applied);
            }
          }}
        >
          {prices.error}
        </Feedback>
      )}
      {prices.notice && <p role="status">{prices.notice}</p>}
      {page ? (
        <div
          className="results"
          aria-busy={prices.busy}
        >
          <div className="applied-context">
            <strong>
              {page.overview.context.baseRegion.name} · {page.overview.context.fuelType.name}
            </strong>
            <span>
              {date(page.overview.context.dateFrom)} — {date(page.overview.context.dateTo)} ·{" "}
              {frequencyNames[page.overview.context.frequency]} ·{" "}
              {sourceNames[page.overview.context.preferredSource]}
            </span>
          </div>
          <Overview
            value={page.overview}
            onObservation={setObservation}
          />
          {page.trend.value ? (
            <Trend
              value={page.trend.value}
              onObservation={setObservation}
            />
          ) : (
            <section
              id="trend"
              className="surface"
            >
              <h2>Как менялась цена</h2>
              <Feedback
                onRetry={() => {
                  if (applied) {
                    void load(applied);
                  }
                }}
              >
                Не удалось загрузить историю. Сводка остаётся доступной.
              </Feedback>
            </section>
          )}
          {page.comparison.value ? (
            <Comparison
              value={page.comparison.value}
              disabled={blocked}
              onSort={(sorting) => {
                void prices.sort(sorting);
              }}
              onBase={(code) =>
                apply({ ...fromContext(page.overview.context), baseRegionCode: code }, true)
              }
              onObservation={setObservation}
            />
          ) : (
            <section id="comparison">
              <h2>Сравните регионы</h2>
              <Feedback
                onRetry={() => {
                  if (applied) {
                    void load(applied);
                  }
                }}
              >
                Не удалось загрузить сравнение.
              </Feedback>
            </section>
          )}
          <Calculator
            disabled={blocked || !page.comparison.value?.comparison.snapshotId}
            calculating={prices.calculating}
            result={prices.calculation}
            error={prices.calculationError}
            onCalculate={(volume) => {
              void prices.calculate(volume);
            }}
            onChange={prices.invalidateCalculation}
          />
          <ArchiveInfo
            key={`${page.overview.context.key}-${page.overview.meta.dataVersion}`}
            overview={page.overview}
            busy={prices.busy}
            onRefresh={() => {
              if (applied) {
                void load(applied);
              }
            }}
          />
          {observation && (
            <ObservationDialog
              key={observation}
              id={observation}
              preferredSource={page.overview.context.preferredSource}
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
              История цен, сравнение с другими регионами и стоимость вашего объёма — в одном выборе.
            </p>
            <div>
              <span>01 · Динамика без догадок</span>
              <span>02 · Сопоставимые периоды</span>
              <span>03 · Расчёт по вашему объёму</span>
            </div>
          </section>
        )
      )}
    </>
  );
}
