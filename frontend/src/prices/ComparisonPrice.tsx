import type { Schema } from "../api/types";
import { date, number, signed } from "../ui/format";
import { Amount } from "../ui/Amount";
import { ObservationButton } from "../observations/ObservationButton";

export function ComparisonPrice({
  row,
  sourceTitle,
  commonDate,
  onObservation,
}: {
  row: Schema<"ComparisonRow">;
  sourceTitle: string;
  commonDate: string | null;
  onObservation: (id: string) => void;
}) {
  if (row.price.priceRubPerLiter === null) {
    return (
      <div className="comparison-missing">
        <span>Нет данных</span>
        {row.latestAvailableDate && <small>Последняя цена: {date(row.latestAvailableDate)}</small>}
      </div>
    );
  }
  const price = row.price;
  return (
    <div className="comparison-cell-value">
      <ObservationButton
        id={price.observationId}
        onObservation={onObservation}
        className="comparison-price"
        label={`${sourceTitle} цена: ${number(price.priceRubPerLiter)} ₽/л, ${row.region.name}. Об источнике и дате`}
      >
        <Amount
          value={price.priceRubPerLiter}
          unit="₽/л"
        />
      </ObservationButton>
      {price.period.referenceDate !== commonDate && (
        <small className="comparison-date">{date(price.period.referenceDate)}</small>
      )}
      <span className="comparison-difference">
        {row.isBase
          ? "Точка отсчёта"
          : row.differenceRubPerLiter !== null
            ? `${signed(row.differenceRubPerLiter)} ₽/л`
            : "Нет сопоставимой разницы"}
      </span>
      {row.isLowest && (
        <span
          className="comparison-lowest"
          aria-label="Минимальная цена среди выбранных регионов"
        >
          Минимум
        </span>
      )}
    </div>
  );
}
