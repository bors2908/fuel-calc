import type { Schema, Sorting } from "../api/types";
import { date, signed } from "../ui/format";
import { PriceValue } from "./PriceValue";

type Props = {
  value: Schema<"PriceComparison">;
  disabled: boolean;
  onSort: (sorting: Sorting) => void;
  onBase: (code: string) => void;
  onObservation: (id: string) => void;
};
export function Comparison({ value, disabled, onSort, onBase, onObservation }: Props) {
  const info = value.comparison;
  const explanations = {
    AVAILABLE: "Региональные средние за последний общий период",
    SINGLE_REGION: "Добавьте ещё один регион для сравнения.",
    NO_COMMON_PERIOD:
      "Общего периода нет. Показаны индивидуальные последние цены; ранжирование и совместный расчёт недоступны.",
    NO_DATA: "В выбранном диапазоне нет наблюдений. Измените период или источник.",
  };
  return (
    <section
      id="comparison"
      className="comparison-section"
    >
      <div className="section-heading">
        <div>
          <h2>Сравните регионы</h2>
          <p className="muted">{explanations[info.status]}</p>
        </div>
        <div>
          <label htmlFor="sorting">Порядок строк</label>
          <select
            id="sorting"
            disabled={disabled}
            value={`${info.sortBy}:${info.sortOrder}`}
            onChange={(event) => {
              const [sortBy, sortOrder] = event.target.value.split(":");
              if (
                (sortBy === "PRICE" || sortBy === "REGION_NAME") &&
                (sortOrder === "ASC" || sortOrder === "DESC")
              ) {
                onSort({ sortBy, sortOrder });
              }
            }}
          >
            <option value="PRICE:ASC">Сначала дешевле</option>
            <option value="PRICE:DESC">Сначала дороже</option>
            <option value="REGION_NAME:ASC">Название: А–Я</option>
            <option value="REGION_NAME:DESC">Название: Я–А</option>
          </select>
        </div>
      </div>
      {info.period && <p>Общий период · {info.period.label}</p>}
      {info.allPricesEqual && <p>Цены одинаковы во всех выбранных регионах.</p>}
      {info.hasNewerObservations && (
        <p className="muted">
          Есть более свежие индивидуальные цены. Для сравнения используется общий период.
        </p>
      )}
      <div className="comparison-list">
        {info.data.map((row) => (
          <article
            className={`comparison-row${row.isBase ? " base-row" : ""}`}
            key={row.region.code}
          >
            <div>
              <h3>{row.region.name}</h3>
              <small>
                {row.isBase
                  ? "Мой регион"
                  : row.isLowest
                    ? "Минимальная цена среди выбранных"
                    : row.isHighest
                      ? "Максимальная цена среди выбранных"
                      : ""}
              </small>
            </div>
            <PriceValue
              price={row.price}
              preferredSource={value.context.preferredSource}
              onObservation={onObservation}
            />
            <div>
              <p>
                {row.isBase
                  ? "Основной регион"
                  : row.differenceRubPerLiter !== null
                    ? `${signed(row.differenceRubPerLiter)} ₽/л к моему региону`
                    : "Нет сопоставимой разницы"}
              </p>
              {row.price.priceRubPerLiter === null && (
                <small>Последняя дата архива: {date(row.latestAvailableDate)}</small>
              )}
              {!row.isBase && (
                <button
                  className="text-button"
                  type="button"
                  disabled={disabled}
                  onClick={() => onBase(row.region.code)}
                >
                  Сделать основным
                </button>
              )}
            </div>
          </article>
        ))}
      </div>
    </section>
  );
}
