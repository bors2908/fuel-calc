import { useState, useEffect, useRef } from "react";
import type { Schema } from "../api/types";
import { number } from "../ui/format";
import { PriceValue } from "./PriceValue";

type Props = { value: Schema<"PriceTrend">; onObservation: (id: string) => void };
export function Trend({ value, onObservation }: Props) {
  const chart = useRef<HTMLDivElement>(null);
  const [width, setWidth] = useState(1000);
  const [view, setView] = useState<"chart" | "table">("chart");
  const [hidden, setHidden] = useState<string[]>([]);
  const [active, setActive] = useState("");
  const periods =
    value.data[0]?.points.flatMap((point) => (point.period ? [point.period] : [])) ?? [];
  const selectedPeriod = periods.find((period) => period.key === active) ?? periods.at(-1);
  const codes = value.data.map((series) => series.region.code).sort();
  const visible = value.data.filter((series) => !hidden.includes(series.region.code));
  const prices = visible.flatMap((series) =>
    series.points.flatMap((point) =>
      point.priceRubPerLiter === null ? [] : [Number(point.priceRubPerLiter)],
    ),
  );
  const min = prices.length > 0 ? Math.min(...prices) : 0;
  const max = prices.length > 0 ? Math.max(...prices) : 1;
  const padding = Math.max((max - min) * 0.15, 0.2);
  const lower = Math.max(0, min - padding);
  const upper = max + padding;
  const firstTime = Date.parse(periods[0]?.referenceDate ?? "2000-01-01");
  const lastTime = Date.parse(periods.at(-1)?.referenceDate ?? "2000-01-01");
  const x = (day: string) =>
    firstTime === lastTime
      ? width / 2
      : 76 + ((Date.parse(day) - firstTime) / (lastTime - firstTime)) * (width - 130);
  const y = (price: number) => 255 - ((price - lower) / (upper - lower)) * 210;
  const hasData = value.data.some((series) =>
    series.points.some((point) => point.priceRubPerLiter !== null),
  );
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
          <h2>Как менялась цена</h2>
          <p className="muted">
            {value.context.fuelType.name} · ₽/л ·{" "}
            {
              { DAILY: "Дневные", WEEKLY: "Недельные", MONTHLY: "Месячные" }[
                value.context.frequency
              ]
            }{" "}
            наблюдения
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
      {!hasData ? (
        <p className="empty-state">
          В выбранном диапазоне нет истории. Измените период; доступные границы можно посмотреть в
          «Покрытии архива».
        </p>
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
                  <title id="chart-title">
                    Цены на {value.context.fuelType.name} в выбранных регионах
                  </title>
                  <desc id="chart-description">
                    Пропуски показаны разрывами. Точные значения и источники доступны ниже в выборе
                    периода и в таблице.
                  </desc>
                  <text
                    x="12"
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
                          x1="76"
                          x2={width - 54}
                          y1={y(price)}
                          y2={y(price)}
                        />
                        <text
                          x="65"
                          y={y(price) + 5}
                          textAnchor="end"
                        >
                          {price.toFixed(2).replace(".", ",")}
                        </text>
                      </g>
                    );
                  })}
                  {visible.map((series) => {
                    let path = "";
                    let connected = false;
                    for (const point of series.points) {
                      if (point.priceRubPerLiter === null) {
                        connected = false;
                      } else {
                        path += `${connected ? "L" : "M"}${x(point.period.referenceDate)},${y(Number(point.priceRubPerLiter))} `;
                        connected = true;
                      }
                    }
                    return (
                      <g
                        key={series.region.code}
                        className={`series series-${codes.indexOf(series.region.code)}${series.region.code === value.context.baseRegion.code ? " base-series" : ""}`}
                      >
                        <path d={path} />
                        {series.points.map((point) =>
                          point.priceRubPerLiter === null ? null : (
                            <circle
                              key={point.period.key}
                              cx={x(point.period.referenceDate)}
                              cy={y(Number(point.priceRubPerLiter))}
                              r="4"
                              onPointerEnter={() => setActive(point.period.key)}
                              onPointerDown={() => setActive(point.period.key)}
                            >
                              <title>
                                {series.region.name}: {number(point.priceRubPerLiter)} ₽/л ·{" "}
                                {point.period.label} · {point.source.provider.name}
                              </title>
                            </circle>
                          ),
                        )}
                      </g>
                    );
                  })}
                  {periods
                    .filter(
                      (_, index) =>
                        index === 0 ||
                        index === periods.length - 1 ||
                        index === Math.floor(periods.length / 2),
                    )
                    .map((period) => (
                      <text
                        key={period.key}
                        x={x(period.referenceDate)}
                        y="295"
                        textAnchor="middle"
                      >
                        {new Intl.DateTimeFormat("ru-RU", {
                          day: "numeric",
                          month: "short",
                          timeZone: "UTC",
                        }).format(new Date(`${period.referenceDate}T00:00:00Z`))}
                      </text>
                    ))}
                </svg>
              </div>
              <div
                className="legend"
                role="group"
                aria-label="Видимые линии"
              >
                {value.data.map((series) => (
                  <button
                    type="button"
                    className={`legend-item series-${codes.indexOf(series.region.code)}`}
                    key={series.region.code}
                    aria-pressed={!hidden.includes(series.region.code)}
                    onClick={() =>
                      setHidden(
                        hidden.includes(series.region.code)
                          ? hidden.filter((code) => code !== series.region.code)
                          : [...hidden, series.region.code],
                      )
                    }
                  >
                    <span
                      className="legend-swatch"
                      aria-hidden="true"
                    />
                    {series.region.name}
                    {series.region.code === value.context.baseRegion.code && " · мой"}
                  </button>
                ))}
              </div>
              <div className="chart-detail">
                <label htmlFor="chart-period">Период на графике — точные значения</label>
                <select
                  id="chart-period"
                  value={selectedPeriod?.key ?? ""}
                  onChange={(event) => setActive(event.target.value)}
                >
                  {periods.map((period) => (
                    <option
                      key={period.key}
                      value={period.key}
                    >
                      {period.label}
                    </option>
                  ))}
                </select>
                <div className="chart-prices">
                  {visible.map((series) => {
                    const point = series.points.find(
                      (item) => item.period?.key === selectedPeriod?.key,
                    );
                    return (
                      <div key={series.region.code}>
                        <h3>{series.region.name}</h3>
                        {point && (
                          <PriceValue
                            price={point}
                            preferredSource={value.context.preferredSource}
                            onObservation={onObservation}
                          />
                        )}
                      </div>
                    );
                  })}
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
                  Наблюдения · {value.context.fuelType.name}, ₽/л. «—» означает отсутствие
                  наблюдения.
                </caption>
                <thead>
                  <tr>
                    <th scope="col">Период</th>
                    {value.data.map((series) => (
                      <th
                        scope="col"
                        key={series.region.code}
                      >
                        {series.region.name}
                      </th>
                    ))}
                  </tr>
                </thead>
                <tbody>
                  {periods.map((period) => (
                    <tr key={period.key}>
                      <th scope="row">{period.label}</th>
                      {value.data.map((series) => {
                        const point = series.points.find((item) => item.period?.key === period.key);
                        return (
                          <td key={series.region.code}>
                            {point && (
                              <PriceValue
                                price={point}
                                preferredSource={value.context.preferredSource}
                                onObservation={onObservation}
                              />
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
            Региональные средние. Разрыв линии означает отсутствие наблюдения, а не нулевую цену.
            Источники указаны у значений.
          </p>
        </>
      )}
    </section>
  );
}
