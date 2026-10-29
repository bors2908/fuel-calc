import type { Schema } from "../api/types";
import type { Source } from "../sources/model";
import type { PriceSorting } from "./sorting";
import { comparisonModel } from "./comparisonModel";
import { ComparisonSorting } from "./ComparisonSorting";
import { ComparisonTable } from "./ComparisonTable";

import "./comparison.css";

type Props = {
  official: Schema<"Comparison">;
  market: Schema<"Comparison">;
  baseRegionCode: string;
  ordering: Source;
  disabled: boolean;
  onSort: (sorting: PriceSorting) => void;
  onBase: (code: string) => void;
  onObservation: (id: string) => void;
};
export function Comparison({
  official,
  market,
  baseRegionCode,
  ordering,
  disabled,
  onSort,
  onBase,
  onObservation,
}: Props) {
  const { columns, rows, sorting } = comparisonModel(official, market, ordering);
  return (
    <section
      id="comparison"
      className="comparison-section"
      aria-labelledby="comparison-title"
    >
      <div className="section-heading">
        <div>
          <h2 id="comparison-title">Сравните регионы</h2>
          <p className="muted">Сравнение с вашим регионом по двум источникам.</p>
        </div>
        <ComparisonSorting
          official={official}
          market={market}
          value={sorting}
          disabled={disabled}
          onSort={onSort}
        />
      </div>
      <ComparisonTable
        columns={columns}
        rows={rows}
        baseRegionCode={baseRegionCode}
        disabled={disabled}
        onBase={onBase}
        onObservation={onObservation}
      />
      <p className="comparison-note muted">
        Разницы — внутри одного источника и общей даты. Если общей даты нет, показаны последние цены
        без разницы.
      </p>
    </section>
  );
}
