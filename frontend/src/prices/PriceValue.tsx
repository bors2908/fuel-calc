import type { PriceCell, Schema } from "../api/types";
import { date, number } from "../ui/format";

export function PriceSource({
  price,
  preferredSource,
}: {
  price: PriceCell;
  preferredSource: Schema<"PriceSource">;
}) {
  if (price.priceRubPerLiter === null) {
    return <span className="muted">Нет наблюдения</span>;
  }
  return (
    <span className="source-label">
      {price.source.provider.code !== preferredSource && "Резерв: "}
      {price.source.provider.name}
      {price.source.sample && (
        <span className="sample-label">
          Средняя по выборке АЗС · минимум {price.source.sample.minStationCount} станций ·{" "}
          {price.source.sample.dayCount}/{price.source.sample.expectedDayCount} дней
          {price.source.sample.dayCount < price.source.sample.expectedDayCount &&
            " · неполный период"}
        </span>
      )}
    </span>
  );
}
export function PriceValue({
  price,
  preferredSource,
  onObservation,
}: {
  price: PriceCell;
  preferredSource: Schema<"PriceSource">;
  onObservation?: (id: string) => void;
}) {
  if (price.priceRubPerLiter === null) {
    return <span className="muted">Нет данных за период</span>;
  }
  return (
    <div className="price-value">
      <strong>
        {number(price.priceRubPerLiter)} <small>₽/л</small>
      </strong>
      <span>
        {price.period.label} · {date(price.period.referenceDate)}
      </span>
      <PriceSource
        price={price}
        preferredSource={preferredSource}
      />
      {onObservation && (
        <button
          type="button"
          className="text-button"
          onClick={() => onObservation(price.observationId)}
        >
          Об источнике и дате
        </button>
      )}
    </div>
  );
}
