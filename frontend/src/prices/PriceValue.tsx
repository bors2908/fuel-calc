import type { PriceCell } from "../api/types";
import { sourceNames } from "../api/types";
import { date, number } from "../ui/format";

export function PriceSource({ price }: { price: PriceCell }) {
  return (
    <span className="source-label">
      {price.priceRubPerLiter === null ? "Нет наблюдения" : sourceNames[price.source]}
    </span>
  );
}
export function PriceValue({
  price,
  onObservation,
}: {
  price: PriceCell;
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
        {date(price.period.referenceDate)}
        {price.source === "OFFICIAL" && " · недельное наблюдение"}
      </span>
      <PriceSource price={price} />
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
