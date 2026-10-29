import type { Schema, Sorting } from "../api/types";
import type { Block } from "./load";
import { date, number, signed } from "../ui/format";
import { Feedback } from "../ui/Feedback";

type Source = Schema<"PriceSource">;
type Props = {
  official: Block<Schema<"PriceComparison">>;
  market: Block<Schema<"PriceComparison">>;
  regions: Schema<"RegionRef">[];
  baseRegionCode: string;
  ordering: Source;
  disabled: boolean;
  onSort: (source: Source, sorting: Sorting) => void;
  onBase: (code: string) => void;
  onObservation: (id: string) => void;
  onRetry: () => void;
};

export function Comparison({
  official,
  market,
  regions,
  baseRegionCode,
  ordering,
  disabled,
  onSort,
  onBase,
  onObservation,
  onRetry,
}: Props) {
  const sources = (["OFFICIAL", "MARKET"] as const).map((source) => ({
    source,
    title: source === "OFFICIAL" ? "Официальная" : "Рыночная",
    block: source === "OFFICIAL" ? official : market,
  }));
  const requested = ordering === "OFFICIAL" ? official.value : market.value;
  const ordered = requested ?? official.value ?? market.value;
  const orderedSource = ordered?.context.source ?? ordering;
  const info = ordered?.comparison;
  const rows = info?.data.map((row) => row.region) ?? regions;
  const sortValue = `${orderedSource}:${info?.sortBy ?? "REGION_NAME"}:${info?.sortOrder ?? "ASC"}`;
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
        <div className="comparison-sort">
          <label htmlFor="comparison-sort">Порядок регионов</label>
          <select
            id="comparison-sort"
            disabled={disabled || !ordered}
            value={sortValue}
            onChange={(event) => {
              const [source, sortBy, sortOrder] = event.target.value.split(":");
              if (
                (source === "OFFICIAL" || source === "MARKET") &&
                (sortBy === "PRICE" || sortBy === "REGION_NAME") &&
                (sortOrder === "ASC" || sortOrder === "DESC")
              ) {
                onSort(source, { sortBy, sortOrder });
              }
            }}
          >
            <option
              value="OFFICIAL:PRICE:ASC"
              disabled={
                !official.value ||
                !["AVAILABLE", "SINGLE_REGION"].includes(official.value.comparison.status)
              }
            >
              Официальная: сначала дешевле
            </option>
            <option
              value="OFFICIAL:PRICE:DESC"
              disabled={
                !official.value ||
                !["AVAILABLE", "SINGLE_REGION"].includes(official.value.comparison.status)
              }
            >
              Официальная: сначала дороже
            </option>
            <option
              value="MARKET:PRICE:ASC"
              disabled={
                !market.value ||
                !["AVAILABLE", "SINGLE_REGION"].includes(market.value.comparison.status)
              }
            >
              Рыночная: сначала дешевле
            </option>
            <option
              value="MARKET:PRICE:DESC"
              disabled={
                !market.value ||
                !["AVAILABLE", "SINGLE_REGION"].includes(market.value.comparison.status)
              }
            >
              Рыночная: сначала дороже
            </option>
            <option value={`${orderedSource}:REGION_NAME:ASC`}>Название: А–Я</option>
            <option value={`${orderedSource}:REGION_NAME:DESC`}>Название: Я–А</option>
          </select>
        </div>
      </div>
      {sources.map(({ source, block }) =>
        !block.value ? (
          <Feedback
            key={source}
            onRetry={onRetry}
          >
            Не удалось загрузить {source === "OFFICIAL" ? "официальные" : "рыночные"} цены. Второй
            источник остаётся доступным.
          </Feedback>
        ) : null,
      )}
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
              {sources.map(({ source, title, block }) => (
                <th
                  scope="col"
                  className="comparison-column"
                  key={source}
                  data-source={source}
                >
                  <span>{title}</span>
                  <small>
                    {source === "OFFICIAL" ? "Росстат · по неделям" : "BenzUp · по дням"}
                  </small>
                  <span className="comparison-period">
                    {block.value?.comparison.period
                      ? `Общая дата: ${date(block.value.comparison.period.referenceDate)}`
                      : block.value?.comparison.status === "NO_COMMON_PERIOD"
                        ? "Общего периода нет"
                        : block.value?.comparison.status === "NO_DATA"
                          ? "Нет наблюдений"
                          : block.value
                            ? "Последнее наблюдение"
                            : "Данные недоступны"}
                  </span>
                  {block.value?.comparison.allPricesEqual && (
                    <span className="comparison-period">Цены одинаковы</span>
                  )}
                  {block.value?.comparison.hasNewerObservations && (
                    <span className="comparison-period">Есть более свежие индивидуальные цены</span>
                  )}
                </th>
              ))}
            </tr>
          </thead>
          <tbody>
            {rows.map((region) => (
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
                {sources.map(({ source, title, block }) => (
                  <td
                    className="comparison-cell"
                    data-source={source}
                    key={source}
                  >
                    <ComparisonPrice
                      row={block.value?.comparison.data.find(
                        (row) => row.region.okatoCode === region.okatoCode,
                      )}
                      sourceTitle={title}
                      unavailable={!block.value}
                      commonDate={block.value?.comparison.period?.referenceDate}
                      onObservation={onObservation}
                    />
                  </td>
                ))}
              </tr>
            ))}
          </tbody>
        </table>
      </div>
      <p className="comparison-note muted">
        Разницы — внутри одного источника и общей даты. Если общей даты нет, показаны последние цены
        без разницы.
      </p>
    </section>
  );
}

function ComparisonPrice({
  row,
  sourceTitle,
  unavailable,
  commonDate,
  onObservation,
}: {
  row: Schema<"ComparisonRow"> | undefined;
  sourceTitle: string;
  unavailable: boolean;
  commonDate: string | undefined;
  onObservation: (id: string) => void;
}) {
  if (!row || row.price.priceRubPerLiter === null) {
    return (
      <div className="comparison-missing">
        <span>{unavailable ? "Недоступно" : "Нет данных"}</span>
        {row?.latestAvailableDate && <small>Последняя цена: {date(row.latestAvailableDate)}</small>}
      </div>
    );
  }
  const price = row.price;
  return (
    <div className="comparison-cell-value">
      <button
        type="button"
        className="comparison-price"
        aria-label={`${sourceTitle} цена: ${number(price.priceRubPerLiter)} ₽/л, ${row.region.name}. Об источнике и дате`}
        onClick={() => onObservation(price.observationId)}
      >
        {number(price.priceRubPerLiter)} <small>₽/л</small>
      </button>
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
