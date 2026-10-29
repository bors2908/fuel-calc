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
}: {
  series: ChartSeries[];
  prices: number[];
  from: string;
  to: string;
  regionNames: string;
  onSelect: (date: string) => void;
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
          Общая шкала по дням. Официальные точки — недельные, рыночные — дневные. Пропуски показаны
          разрывами. Цвет обозначает регион; сплошная линия и закрашенные кружки — официальную цену,
          пунктир и полые кружки — рыночную. Точные значения доступны в таблице и выборе даты.
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
          const path = seriesPath(item, { x, y });
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
  );
}
