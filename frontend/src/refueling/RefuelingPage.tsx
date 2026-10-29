import { SiteHeader } from "../app/SiteHeader";
import type { Catalog } from "../selection/model";
import { selectionHref, withDefaultRange } from "../selection/model";
import { Filters } from "../selection/Filters";
import { useSelection } from "../selection/useSelection";
import { Calculator } from "./Calculator";
import { useRefueling } from "./useRefueling";

export function RefuelingPage({ catalog }: { catalog: Catalog }) {
  const calculation = useRefueling();
  const filters = useSelection(
    catalog,
    calculation.invalidate,
    calculation.invalidate,
    calculation.invalidate,
    "date",
  );
  return (
    <>
      <SiteHeader
        page="refueling"
        selection={filters.selection}
      />
      <main
        id="main"
        className="wrap refueling-page"
      >
        <section className="hero hero-compact">
          <a
            className="back-link"
            href={selectionHref(withDefaultRange(filters.selection), "/")}
          >
            ← К ценам и динамике
          </a>
          <p className="eyebrow">Планирование расходов</p>
          <h1>Рассчитайте заправку</h1>
          <p>Выберите регионы и объём. Покажем стоимость по официальной и рыночной цене.</p>
        </section>
        <Calculator
          disabled={!filters.valid || !filters.applied}
          calculating={calculation.busy}
          result={calculation.result}
          error={calculation.error}
          onCalculate={(volume) => {
            if (filters.applied && filters.valid) {
              void calculation.calculate(filters.applied, volume);
            }
          }}
          onChange={calculation.invalidate}
        >
          {catalog.regions.length === 0 && (
            <p
              className="volume-hint"
              role="status"
            >
              Цены пока не загружены. Список регионов появится после первой загрузки данных.
            </p>
          )}
          <Filters
            catalog={catalog}
            value={filters.selection}
            error={filters.error}
            onChange={filters.change}
            dateMode="date"
          />
        </Calculator>
      </main>
    </>
  );
}
