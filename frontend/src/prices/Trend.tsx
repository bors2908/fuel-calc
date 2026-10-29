import { useEffect, useRef, useState } from "react";
import type { PriceContext, Schema } from "../api/types";
import { sourceNames } from "../api/types";
import { regionName } from "../regions/regionName";
import { Feedback } from "../ui/Feedback";
import { date, number } from "../ui/format";
import type { Block } from "./load";
import { PriceValue } from "./PriceValue";

type Props = {
  context: PriceContext;
  market: Block<Schema<"PriceTrend">>;
  official: Block<Schema<"PriceTrend">>;
  onObservation: (id: string) => void;
  onRetry: () => void;
};
const dayMs = 86_400_000;
const sources = ["OFFICIAL", "MARKET"] as const;
export function Trend({ context, market, official, onObservation, onRetry }: Props) {
  const chart = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(1000);
  const [view, setView] = useState<"chart" | "table">("chart");
  const [active, setActive] = useState("");
  const groups = context.regions.map((region, index) => ({
    region,
    index,
    series: sources.map((source) => {
      const block = source === "OFFICIAL" ? official : market;
      const row = block.value?.data.find((item) => item.region.okatoCode === region.okatoCode);
      return {
        key: `${region.okatoCode}:${source}`,
        region,
        source,
        index,
        points: row?.points ?? [],
        failed: !block.value,
      };
    }),
  }));
  const series = groups.flatMap((group) => group.series);
  const regionNames = context.regions.map(regionName).join(", ");
  const dates = [
    ...new Set(
      series.flatMap((item) =>
        item.points.flatMap((point) => (point.period ? [point.period.referenceDate] : [])),
      ),
    ),
  ].sort();
  const selectedDate = dates.includes(active) ? active : (dates.at(-1) ?? "");
  const prices = series.flatMap((item) =>
    item.points.flatMap((point) =>
      point.priceRubPerLiter === null ? [] : [Number(point.priceRubPerLiter)],
    ),
  );
  const hasData = prices.length > 0;
  const min = hasData ? Math.min(...prices) : 0;
  const max = hasData ? Math.max(...prices) : 1;
  const padding = Math.max((max - min) * 0.15, 0.2);
  const lower = Math.max(0, min - padding);
  const upper = max + padding;
  const firstTime = Date.parse(context.dateFrom);
  const lastTime = Date.parse(context.dateTo);
  const x = (day: string) =>
    firstTime === lastTime
      ? width / 2
      : 64 + ((Date.parse(day) - firstTime) / (lastTime - firstTime)) * (width - 100);
  const y = (price: number) => 255 - ((price - lower) / (upper - lower)) * 210;
  const axisDates = [
    ...new Set([
      context.dateFrom,
      new Date(firstTime + Math.floor((lastTime - firstTime) / dayMs / 2) * dayMs)
        .toISOString()
        .slice(0, 10),
      context.dateTo,
    ]),
  ];
  useEffect(() => {
    const target = chart.current;
    if (!target) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => {
      if (entry) {
        setWidth(Math.max(280, entry.contentRect.width));
      }
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, [view, hasData]);
  return (
    <section
      id="trend"
      className="trend-section surface"
    >
      <div className="section-heading">
        <div>
          <p className="eyebrow">Официальная статистика и рынок</p>
          <h2>Как менялась цена</h2>
          <p className="muted">
            {context.fuelType.name} · ₽/л ·{" "}
            {context.regions.length === 1 ? regionNames : "Выбранные регионы"}
          </p>
        </div>
        <div
          className="view-switch"
          role="group"
          aria-label="Представление истории"
        >
          <button
            type="button"
            aria-pressed={view === "chart"}
            onClick={() => setView("chart")}
          >
            График
          </button>
          <button
            type="button"
            aria-pressed={view === "table"}
            onClick={() => setView("table")}
          >
            Таблица
          </button>
        </div>
      </div>
      <div
        className="legend"
        role="group"
        aria-label="Регионы и источники на графике"
      >
        {groups.map((group) => (
          <div
            className={`legend-region series-${group.index}`}
            key={group.region.okatoCode}
            data-region={group.region.okatoCode}
          >
            <strong>
              {regionName(group.region)}
              {group.region.okatoCode === context.baseRegion.okatoCode && " · мой регион"}
            </strong>
            {group.series.map((item) => (
              <div
                className="legend-item"
                key={item.key}
                data-source={item.source}
              >
                <span
                  className="legend-swatch"
                  aria-hidden="true"
                />
                <span>
                  {sourceNames[item.source]}
                  <small>
                    {item.failed
                      ? "История недоступна"
                      : !item.points.some((point) => point.priceRubPerLiter !== null)
                        ? "Нет наблюдений за период"
                        : item.source === "OFFICIAL"
                          ? "Росстат · по неделям"
                          : "BenzUp · по дням"}
                  </small>
                </span>
              </div>
            ))}
          </div>
        ))}
      </div>
      {sources.map((source) =>
        !(source === "OFFICIAL" ? official : market).value ? (
          <Feedback
            key={source}
            onRetry={onRetry}
          >
            Не удалось загрузить историю: {sourceNames[source].toLowerCase()}. Данные другого
            источника остаются доступны.
          </Feedback>
        ) : null,
      )}
      {!hasData ? (
        <p className="empty-state">В выбранном диапазоне нет истории. Попробуйте другой период.</p>
      ) : (
        <>
          {view === "chart" ? (
            <>
              <div
                className="chart"
                ref={chart}
              >
                <svg
                  viewBox={`0 0 ${width} 315`}
                  role="img"
                  aria-labelledby="chart-title chart-description"
                >
                  <title id="chart-title">Официальная и рыночная цена · {regionNames}</title>
                  <desc id="chart-description">
                    Общая шкала по дням. Официальные точки — недельные, рыночные — дневные. Пропуски
                    показаны разрывами. Цвет обозначает регион; сплошная линия и закрашенные кружки
                    — официальную цену, пунктир и полые кружки — рыночную. Точные значения доступны
                    в таблице и выборе даты.
                  </desc>
                  <text
                    x="8"
                    y="20"
                  >
                    ₽/л
                  </text>
                  {[0, 1, 2, 3].map((tick) => {
                    const price = lower + ((upper - lower) / 3) * tick;
                    return (
                      <g key={tick}>
                        <line
                          className="chart-grid"
                          x1="64"
                          x2={width - 36}
                          y1={y(price)}
                          y2={y(price)}
                        />
                        <text
                          x="54"
                          y={y(price) + 5}
                          textAnchor="end"
                        >
                          {price.toFixed(2).replace(".", ",")}
                        </text>
                      </g>
                    );
                  })}
                  {series.map((item) => {
                    let path = "";
                    let previous: number | null = null;
                    for (const point of item.points) {
                      if (point.priceRubPerLiter === null) {
                        previous = null;
                      } else {
                        const time = Date.parse(point.period.referenceDate);
                        const connected =
                          previous !== null &&
                          time - previous <= (item.source === "OFFICIAL" ? 7 : 1) * dayMs;
                        path += `${connected ? "L" : "M"}${x(point.period.referenceDate)},${y(Number(point.priceRubPerLiter))} `;
                        previous = time;
                      }
                    }
                    return (
                      <g
                        key={item.key}
                        className={`series series-${item.index}`}
                        data-source={item.source}
                        data-region={item.region.okatoCode}
                      >
                        <path d={path} />
                        {item.points.map((point) =>
                          point.priceRubPerLiter === null ? null : (
                            <circle
                              key={point.period.key}
                              cx={x(point.period.referenceDate)}
                              cy={y(Number(point.priceRubPerLiter))}
                              r={item.source === "OFFICIAL" ? 5 : 3.5}
                              onPointerEnter={() => setActive(point.period.referenceDate)}
                              onPointerDown={() => setActive(point.period.referenceDate)}
                            >
                              <title>
                                {regionName(item.region)} · {sourceNames[item.source]} ·{" "}
                                {date(point.period.referenceDate)} ·{" "}
                                {number(point.priceRubPerLiter)} ₽/л
                              </title>
                            </circle>
                          ),
                        )}
                      </g>
                    );
                  })}
                  {axisDates.map((day) => (
                    <text
                      key={day}
                      x={x(day)}
                      y="295"
                      textAnchor="middle"
                    >
                      {new Intl.DateTimeFormat("ru-RU", {
                        day: "numeric",
                        month: "short",
                        timeZone: "UTC",
                      }).format(new Date(`${day}T00:00:00Z`))}
                    </text>
                  ))}
                </svg>
              </div>
              <div className="chart-detail">
                <label htmlFor="chart-period">Дата на графике — точные значения</label>
                <select
                  id="chart-period"
                  value={selectedDate}
                  onChange={(event) => setActive(event.target.value)}
                >
                  {dates.map((day) => (
                    <option
                      key={day}
                      value={day}
                    >
                      {date(day)}
                    </option>
                  ))}
                </select>
                <div className="chart-prices">
                  {groups.map((group) => (
                    <section
                      className={`chart-region series-${group.index}`}
                      key={group.region.okatoCode}
                      data-region={group.region.okatoCode}
                    >
                      <h3>
                        {regionName(group.region)}
                        {group.region.okatoCode === context.baseRegion.okatoCode && " · мой регион"}
                      </h3>
                      <div className="chart-region-sources">
                        {group.series.map((item) => {
                          const point = item.points.find(
                            (value) => value.period?.referenceDate === selectedDate,
                          );
                          return (
                            <div
                              key={item.key}
                              data-source={item.source}
                            >
                              <h4>{sourceNames[item.source]}</h4>
                              {point ? (
                                <PriceValue
                                  price={point}
                                  onObservation={onObservation}
                                />
                              ) : (
                                <p className="muted">
                                  {item.failed
                                    ? "История недоступна"
                                    : "Нет наблюдения на эту дату"}
                                </p>
                              )}
                            </div>
                          );
                        })}
                      </div>
                    </section>
                  ))}
                </div>
              </div>
            </>
          ) : (
            <div
              className="table-scroll"
              tabIndex={0}
              role="region"
              aria-label="История цен"
            >
              <table>
                <caption>
                  {regionNames} · {context.fuelType.name}, ₽/л. «—» — нет наблюдения на эту дату.
                </caption>
                <colgroup span={1} />
                {groups.map((group) => (
                  <colgroup
                    key={group.region.okatoCode}
                    span={2}
                  />
                ))}
                <thead>
                  <tr>
                    <th
                      scope="col"
                      rowSpan={2}
                    >
                      Дата
                    </th>
                    {groups.map((group) => (
                      <th
                        scope="colgroup"
                        colSpan={2}
                        key={group.region.okatoCode}
                      >
                        {regionName(group.region)}
                        {group.region.okatoCode === context.baseRegion.okatoCode && " · мой регион"}
                      </th>
                    ))}
                  </tr>
                  <tr>
                    {series.map((item) => (
                      <th
                        scope="col"
                        key={item.key}
                      >
                        {sourceNames[item.source]}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {dates.map((day) => (
                    <tr key={day}>
                      <th scope="row">{date(day)}</th>
                      {series.map((item) => {
                        const point = item.points.find(
                          (value) => value.period?.referenceDate === day,
                        );
                        return (
                          <td
                            key={item.key}
                            data-region={item.region.okatoCode}
                            data-source={item.source}
                          >
                            {point?.priceRubPerLiter ? (
                              <PriceValue
                                price={point}
                                onObservation={onObservation}
                              />
                            ) : (
                              "—"
                            )}
                          </td>
                        );
                      })}
                    </tr>
                  ))}
                </tbody>
              </table>
            </div>
          )}
          <p className="muted chart-note">
            Каждый кружок — сохранённое наблюдение на указанную дату. Линии соединяют соседние
            наблюдения; ежедневные официальные значения не рассчитываются. Разрыв означает
            отсутствие данных.
          </p>
        </>
      )}
    </section>
  );
}
