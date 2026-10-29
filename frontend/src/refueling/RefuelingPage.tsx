import { SiteHeader } from "../app/SiteHeader";
import type { Catalog } from "../selection/types";
import { selectionHref, refuelingSelectionModel } from "../selection/model";
import { EmptyRegionCatalog } from "../selection/EmptyRegionCatalog";
import { Filters } from "../selection/Filters";
import { useSelection } from "../selection/useSelection";
import { Calculator } from "./Calculator";
import { useRefueling } from "./useRefueling";

export function RefuelingPage({ catalog }: { catalog: Catalog }) {
  const calculation = useRefueling();
  const filters = useSelection(catalog, refuelingSelectionModel, {
    onInvalidate: calculation.invalidate,
  });
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
            href={selectionHref(filters.selection, "/")}
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
          {catalog.regions.length === 0 && <EmptyRegionCatalog className="volume-hint" />}
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
