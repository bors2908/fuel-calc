import { regionName } from "../regions/regionName";
import type { Schema } from "../api/types";
import { number, signed, date } from "../ui/format";
import type { Block } from "./load";
import { PriceValue } from "./PriceValue";
import { Feedback } from "../ui/Feedback";

export function Overview({
  value,
  official,
  showComparison,
  onObservation,
  onRetry,
}: {
  value: Block<Schema<"PriceOverview">>;
  official: Block<Schema<"PriceOverview">>;
  showComparison: boolean;
  onObservation: (id: string) => void;
  onRetry: () => void;
}) {
  return (
    <section
      className="overview"
      aria-label="Сводка цен"
    >
      <div
        data-source="OFFICIAL"
        aria-label="Официальная сводка"
      >
        <p className="overview-label">Официальная цена · Росстат</p>
        {official.value ? (
          <SourceSummary
            value={official.value}
            showComparison={showComparison}
            onObservation={onObservation}
          />
        ) : (
          <div className="overview-price">
            <Feedback onRetry={onRetry}>Не удалось загрузить официальную сводку.</Feedback>
          </div>
        )}
      </div>
      <div
        data-source="MARKET"
        aria-label="Рыночная сводка"
      >
        <p className="overview-label">Рыночная цена · BenzUp</p>
        {value.value ? (
          <SourceSummary
            value={value.value}
            showComparison={showComparison}
            onObservation={onObservation}
          />
        ) : (
          <div className="overview-price">
            <Feedback onRetry={onRetry}>
              Не удалось загрузить рыночную сводку. Официальные данные остаются доступны.
            </Feedback>
          </div>
        )}
      </div>
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
          price={value.latestPrice}
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
                  {number(comparison.lowestPriceRubPerLiter)} <small>₽/л</small>
                </strong>
              ) : (
                <span className="stat-message">Нет сопоставимой цены</span>
              )}
            </div>
            <p className="overview-description">
              {comparison.lowestPriceRubPerLiter !== null
                ? comparison.lowestRegions.map(regionName).join(", ")
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
