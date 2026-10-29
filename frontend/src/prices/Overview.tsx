import { Amount } from "../ui/Amount";
import { sourceInfo, sources } from "../sources/model";
import type { Schema } from "../api/types";
import { signed, date } from "../ui/format";
import { presentPrice } from "./presentation";
import { PriceValue } from "./PriceValue";

import "./overview.css";

export function Overview({
  market,
  official,
  showComparison,
  onObservation,
}: {
  market: Schema<"PriceOverview">;
  official: Schema<"PriceOverview">;
  showComparison: boolean;
  onObservation: (id: string) => void;
}) {
  return (
    <section
      className="overview"
      aria-label="Сводка цен"
    >
      {sources.map((source) => (
        <div
          key={source}
          data-source={source}
          aria-label={`${sourceInfo[source].shortName} сводка`}
        >
          <p className="overview-label">
            {sourceInfo[source].name} · {sourceInfo[source].provider}
          </p>
          <SourceSummary
            value={source === "OFFICIAL" ? official : market}
            showComparison={showComparison}
            onObservation={onObservation}
          />
        </div>
      ))}
    </section>
  );
}

function SourceSummary({
  value,
  showComparison,
  onObservation,
}: {
  value: Schema<"PriceOverview">;
  showComparison: boolean;
  onObservation: (id: string) => void;
}) {
  const comparison = value.comparison;
  return (
    <>
      <div className="overview-price">
        <PriceValue
          price={presentPrice(value.latestPrice)}
          onObservation={onObservation}
        />
        {value.latestPrice.priceRubPerLiter === null && (
          <p className="muted">
            {value.latestAvailableDate
              ? `Последнее наблюдение: ${date(value.latestAvailableDate)}. Измените период.`
              : "Наблюдений этого источника пока нет."}
          </p>
        )}
      </div>
      <div className="overview-stats">
        <div>
          <p className="overview-label">Изменение за период</p>
          <div className="overview-stat-value">
            <strong className={value.change.percent !== null ? "stat-value" : "stat-message"}>
              {value.change.percent !== null
                ? `${signed(value.change.percent)}%`
                : "Недостаточно данных"}
            </strong>
          </div>
          <p className="muted">
            {value.change.absoluteRubPerLiter !== null
              ? `${signed(value.change.absoluteRubPerLiter)} ₽/л`
              : "Нужно не меньше двух наблюдений"}
            {value.change.status === "ZERO_BASE" && " · начальная цена равна нулю"}
          </p>
          <small>
            {value.change.startPrice.period
              ? `С ${date(value.change.startPrice.period.referenceDate)}`
              : null}
          </small>
        </div>
        {showComparison && (
          <div>
            <p className="overview-label">Ниже среди выбранных</p>
            <div className="overview-stat-value">
              {comparison.lowestPriceRubPerLiter !== null ? (
                <strong className="stat-value">
                  <Amount
                    value={comparison.lowestPriceRubPerLiter}
                    unit="₽/л"
                  />
                </strong>
              ) : (
                <span className="stat-message">Нет сопоставимой цены</span>
              )}
            </div>
            <p className="overview-description">
              {comparison.lowestPriceRubPerLiter !== null
                ? comparison.lowestRegions.map((region) => region.name).join(", ")
                : comparison.status === "SINGLE_REGION"
                  ? "Добавьте регион, чтобы сравнить цены."
                  : "Нет общего периода для сравнения."}
            </p>
            <small>
              {comparison.period ? `Общая дата: ${date(comparison.period.referenceDate)}` : null}
            </small>
          </div>
        )}
      </div>
    </>
  );
}
