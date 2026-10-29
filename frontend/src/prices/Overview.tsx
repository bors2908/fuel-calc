import type { Schema } from "../api/types";
import { number, signed, date } from "../ui/format";
import { PriceValue } from "./PriceValue";

export function Overview({
  value,
  onObservation,
}: {
  value: Schema<"PriceOverview">;
  onObservation: (id: string) => void;
}) {
  const comparison = value.comparison;
  return (
    <section
      className="overview"
      aria-label="Сводка цен"
    >
      <div className="overview-primary">
        <p className="overview-label">
          {value.context.fuelType.name} · {value.context.baseRegion.name}
        </p>
        <PriceValue
          price={value.latestPrice}
          preferredSource={value.context.preferredSource}
          onObservation={onObservation}
        />
        {value.latestPrice.priceRubPerLiter === null && (
          <p className="muted">
            Последнее наблюдение архива: {date(value.latestAvailableDate)}. Попробуйте другой
            период.
          </p>
        )}
      </div>
      <div>
        <p className="overview-label">Изменение за период</p>
        <strong className={value.change.percent !== null ? "stat-value" : "stat-message"}>
          {value.change.percent !== null
            ? `${signed(value.change.percent)}%`
            : "Недостаточно данных"}
        </strong>
        <p className="muted">
          {value.change.absoluteRubPerLiter !== null
            ? `${signed(value.change.absoluteRubPerLiter)} ₽/л`
            : "Нужно не меньше двух наблюдений"}
          {value.change.status === "ZERO_BASE" && " · начальная цена равна нулю"}
        </p>
        {value.change.startPrice.period && (
          <small>С {date(value.change.startPrice.period.referenceDate)}</small>
        )}
      </div>
      <div>
        <p className="overview-label">Ниже среди выбранных</p>
        {comparison.lowestPriceRubPerLiter !== null ? (
          <>
            <strong className="stat-value">
              {number(comparison.lowestPriceRubPerLiter)} <small>₽/л</small>
            </strong>
            <p className="overview-description">
              {comparison.lowestRegions.map((region) => region.name).join(", ")}
            </p>
            <small>Общий период: {comparison.period?.label ?? "—"}</small>
          </>
        ) : (
          <p className="muted">
            {comparison.status === "SINGLE_REGION"
              ? "Добавьте регион, чтобы сравнить цены."
              : "Нет общего периода для сравнения."}
          </p>
        )}
      </div>
    </section>
  );
}
