import type { PresentedPrice } from "./presentation";
import { date } from "../ui/format";
import { Amount } from "../ui/Amount";
import { SourceLabel } from "../sources/SourceLabel";
import { ObservationButton } from "../observations/ObservationButton";

import "./price-value.css";

export function PriceValue({
  price,
  onObservation,
}: {
  price: PresentedPrice;
  onObservation?: (id: string) => void;
}) {
  if (price.status === "missing") {
    return <span className="muted">Нет данных за период</span>;
  }
  return (
    <div className="price-value">
      <strong>
        <Amount
          value={price.value}
          unit="₽/л"
        />
      </strong>
      <span>
        {date(price.date)}
        {price.source === "OFFICIAL" && " · недельное наблюдение"}
      </span>
      <SourceLabel source={price.source} />
      {onObservation && (
        <ObservationButton
          id={price.observationId}
          onObservation={onObservation}
        />
      )}
    </div>
  );
}
