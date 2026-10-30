import { useEffect, useId, useRef, useState } from "react";
import type { ChartSeries } from "./model";
import { chartGeometry, seriesPath } from "./geometry";
import { date, number } from "../../ui/format";
import { sourceInfo } from "../../sources/model";
export function PriceChart({
  series,
  prices,
  from,
  to,
  regionNames,
  onSelect,
  activeSeries,
  onActivate,
}: {
  series: ChartSeries[];
  prices: number[];
  from: string;
  to: string;
  regionNames: string;
  onSelect: (date: string) => void;
  activeSeries: string | null;
  onActivate: (key: string | null) => void;
}) {
  const id = useId();
  const chart = useRef<HTMLDivElement>(null);
  const [{ width, fontSize }, setDimensions] = useState({ width: 1000, fontSize: 16 });
  const { lower, upper, left, right, x, y, axisDates } = chartGeometry(
    prices,
    from,
    to,
    width,
    fontSize,
  );
  useEffect(() => {
    const target = chart.current;
    if (!target) {
      return;
    }
    const observer = new ResizeObserver(([entry]) => {
      if (entry) {
        setDimensions({
          width: Math.max(280, entry.contentRect.width),
          fontSize: Number.parseFloat(getComputedStyle(target).fontSize),
        });
      }
    });
    observer.observe(target);
    return () => observer.disconnect();
  }, []);
  return (
    <div
      className="chart"
      ref={chart}
    >
      <svg
        viewBox={`0 0 ${width} 315`}
        role="img"
        aria-labelledby={`${id}-title ${id}-description`}
      >
        <title id={`${id}-title`}>Официальная и рыночная цена · {regionNames}</title>
        <desc id={`${id}-description`}>
          Общая шкала по дням. Официальные точки — недельные; последняя недельная цена продолжается
          до конца выбранного периода. Рыночные точки — дневные. Пропуски показаны разрывами. Цвет
          обозначает регион; сплошная линия — официальную цену, пунктир — рыночную. Кружки
          появляются при наведении на линию или её легенду. Точные значения доступны в таблице и
          выборе даты.
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
                x1={left}
                x2={width - right}
                y1={y(price)}
                y2={y(price)}
              />
              <text
                x={left - 10}
                y={y(price) + 5}
                textAnchor="end"
              >
                {price.toFixed(2).replace(".", ",")}
              </text>
            </g>
          );
        })}
        {series.map((item) => {
          const path = seriesPath(item, { x, y }, to);
          return (
            <g
              key={item.key}
              className={`series series-${item.index}`}
              data-source={item.source}
              data-region={item.region.okatoCode}
              data-active={activeSeries === item.key}
              onPointerEnter={() => onActivate(item.key)}
              onPointerLeave={() => onActivate(null)}
            >
              <path
                className="series-line"
                d={path}
              />
              <path
                className="series-hit"
                d={
                  path +
                  item.points
                    .flatMap((point) =>
                      point.priceRubPerLiter !== null
                        ? [`M${x(point.date) - 1},${y(Number(point.priceRubPerLiter))}h2 `]
                        : [],
                    )
                    .join("")
                }
                aria-hidden="true"
              />
              {activeSeries === item.key &&
                item.points.map((point) => {
                  if (point.priceRubPerLiter === null) {
                    return null;
                  }
                  return (
                    <circle
                      key={point.date}
                      cx={x(point.date)}
                      cy={y(Number(point.priceRubPerLiter))}
                      r={item.source === "OFFICIAL" ? 5 : 3.5}
                      onPointerEnter={() => onSelect(point.date)}
                      onPointerDown={() => onSelect(point.date)}
                    >
                      <title>
                        {item.region.name} · {sourceInfo[item.source].name} · {date(point.date)} ·{" "}
                        {number(point.priceRubPerLiter)} ₽/л
                      </title>
                    </circle>
                  );
                })}
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
  );
}
