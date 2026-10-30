import { SiteHeader } from "../app/SiteHeader";
import type { Catalog } from "../selection/types";
import { selectionHref, refuelingSelectionModel } from "../selection/model";
import { EmptyRegionCatalog } from "../selection/EmptyRegionCatalog";
import { Filters } from "../selection/Filters";
import { useSelection } from "../selection/useSelection";
import { Calculator } from "./Calculator";
import { useRefueling } from "./useRefueling";
import { useSavedRefuelings } from "./saved/useSavedRefuelings";
import { SavedRefuelings } from "./saved/SavedRefuelings";
import { savedLimit } from "./saved/model";

export function RefuelingPage({ catalog }: { catalog: Catalog }) {
  const calculation = useRefueling();
  const saved = useSavedRefuelings();
  const filters = useSelection(catalog, refuelingSelectionModel, {
    onInvalidate: calculation.invalidate,
  });
  const singleRegion = filters.selection.regionCodes.length === 1;
  const differentRegion =
    saved.list.region !== null && saved.list.region.okatoCode !== filters.selection.baseRegionCode;
  const hasPrices = calculation.result?.results.some((item) => item.baseTotalRub !== null);
  const matchesSelection =
    calculation.result?.context.baseRegion.okatoCode === filters.selection.baseRegionCode &&
    calculation.result.context.regions.length === 1 &&
    calculation.result.context.fuelType.code === filters.selection.fuelType &&
    calculation.result.context.dateTo === filters.selection.date;
  const reason = !singleRegion
    ? "Для списка заправок выберите ровно один регион."
    : differentRegion
      ? `Список составлен для региона «${saved.list.region?.name}». Вернитесь к нему или очистите список.`
      : saved.list.entries.length >= savedLimit
        ? "Сохранено 10 заправок. Удалите запись, чтобы добавить новую."
        : calculation.result && saved.lastAdded === calculation.result
          ? "Расчёт добавлен. Рассчитайте следующую заправку."
          : null;
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
          resultActions={
            <div className="save-refueling">
              <button
                type="button"
                disabled={
                  Boolean(reason) ||
                  !filters.valid ||
                  calculation.busy ||
                  !calculation.result ||
                  !matchesSelection ||
                  !hasPrices
                }
                onClick={() => {
                  if (
                    calculation.result &&
                    filters.valid &&
                    matchesSelection &&
                    !reason &&
                    !calculation.busy
                  ) {
                    saved.add(calculation.result);
                  }
                }}
              >
                Добавить в список · {saved.list.entries.length}/{savedLimit}
              </button>
              {reason && <p>{reason}</p>}
            </div>
          }
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
        <SavedRefuelings
          list={saved.list}
          warning={saved.warning}
          error={saved.error}
          onRemove={saved.remove}
          onClear={saved.clear}
        />
      </main>
    </>
  );
}
