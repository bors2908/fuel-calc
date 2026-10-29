import { sourceInfo } from "../sources/model";
import { date } from "../ui/format";
import type { ComparisonColumn, ComparisonRegion } from "./comparisonModel";
import { ComparisonPrice } from "./ComparisonPrice";

type Props = {
  columns: ComparisonColumn[];
  rows: ComparisonRegion[];
  baseRegionCode: string;
  disabled: boolean;
  onBase: (code: string) => void;
  onObservation: (id: string) => void;
};
export function ComparisonTable({
  columns,
  rows,
  baseRegionCode,
  disabled,
  onBase,
  onObservation,
}: Props) {
  return (
    <div
      className="comparison-scroll"
      tabIndex={0}
      role="region"
      aria-label="Сравнение официальных и рыночных цен"
    >
      <table className="comparison-table">
        <caption>Цены, ₽/л · разница с вашим регионом</caption>
        <thead>
          <tr>
            <th
              scope="col"
              className="comparison-region"
            >
              Регион
            </th>
            {columns.map(({ source, title, block }) => (
              <th
                scope="col"
                className="comparison-column"
                key={source}
                data-source={source}
              >
                <span>{title}</span>
                <small>{sourceInfo[source].caption}</small>
                <span className="comparison-period">
                  {block.period
                    ? `Общая дата: ${date(block.period.referenceDate)}`
                    : block.status === "NO_COMMON_PERIOD"
                      ? "Общего периода нет"
                      : block.status === "NO_DATA"
                        ? "Нет наблюдений"
                        : "Последнее наблюдение"}
                </span>
                {block.allPricesEqual && <span className="comparison-period">Цены одинаковы</span>}
                {block.hasNewerObservations && (
                  <span className="comparison-period">Есть более свежие индивидуальные цены</span>
                )}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {rows.map(({ region, prices }) => (
            <tr
              key={region.okatoCode}
              className={`comparison-row${region.okatoCode === baseRegionCode ? " base-row" : ""}`}
            >
              <th
                scope="row"
                className="comparison-region"
              >
                <span className="comparison-region-name">{region.name}</span>
                {region.okatoCode === baseRegionCode ? (
                  <span className="comparison-base-label">Мой регион</span>
                ) : (
                  <button
                    type="button"
                    className="text-button comparison-base-button"
                    disabled={disabled}
                    onClick={() => onBase(region.okatoCode)}
                  >
                    Сделать основным
                  </button>
                )}
              </th>
              {columns.map(({ source, title, block }) => (
                <td
                  className="comparison-cell"
                  data-source={source}
                  key={source}
                >
                  <ComparisonPrice
                    row={prices[source]}
                    sourceTitle={title}
                    commonDate={block.period?.referenceDate ?? null}
                    onObservation={onObservation}
                  />
                </td>
              ))}
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
