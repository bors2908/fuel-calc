import { useState } from "react";
import type { Schema } from "../api/types";
import { prepareChart } from "./chart/model";
import { PriceChart } from "./chart/PriceChart";
import { ChartLegend } from "./chart/ChartLegend";
import { ChartDetails } from "./chart/ChartDetails";
import { HistoryTable } from "./chart/HistoryTable";

import "./chart/chart.css";

type Props = {
  context: Schema<"PricePageContext">;
  market: Schema<"RegionPriceSeries">[];
  official: Schema<"RegionPriceSeries">[];
  onObservation: (id: string) => void;
  serverDate: string;
};
export function Trend({ context, market, official, onObservation, serverDate }: Props) {
  const [view, setView] = useState<"chart" | "table">("chart");
  const [activeSeries, setActiveSeries] = useState<string | null>(null);
  const [active, setActive] = useState("");
  const model = prepareChart(context, official, market);
  const selectedDate = model.detailDates.includes(active)
    ? active
    : (model.detailDates.at(-1) ?? "");
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
            {context.regions.length === 1 ? model.regionNames : "Выбранные регионы"}
          </p>
        </div>
        <div
          className="view-switch"
          role="group"
          aria-label="Представление истории"
        >
          {(["chart", "table"] as const).map((mode) => (
            <button
              key={mode}
              type="button"
              aria-pressed={view === mode}
              onClick={() => setView(mode)}
            >
              {mode === "chart" ? "График" : "Таблица"}
            </button>
          ))}
        </div>
      </div>
      <ChartLegend
        groups={model.groups}
        baseRegionCode={context.baseRegion.okatoCode}
        activeSeries={activeSeries}
        onActivate={setActiveSeries}
      />
      {model.prices.length === 0 ? (
        <p className="empty-state">
          {context.dateTo > serverDate
            ? "Недостаточно официальной истории или текущих цен для прогноза. Попробуйте другой период."
            : "В выбранном диапазоне нет истории. Попробуйте другой период."}
        </p>
      ) : (
        <>
          {view === "chart" ? (
            <>
              <PriceChart
                series={model.series}
                prices={model.prices}
                from={context.dateFrom}
                to={context.dateTo}
                regionNames={model.regionNames}
                onSelect={setActive}
                activeSeries={activeSeries}
                onActivate={setActiveSeries}
                serverDate={serverDate}
              />
              <ChartDetails
                groups={model.groups}
                baseRegionCode={context.baseRegion.okatoCode}
                dates={model.detailDates}
                selectedDate={selectedDate}
                onSelect={setActive}
                onObservation={onObservation}
              />
            </>
          ) : (
            <HistoryTable
              groups={model.groups}
              series={model.series}
              dates={model.dates}
              baseRegionCode={context.baseRegion.okatoCode}
              regionNames={model.regionNames}
              fuelName={context.fuelType.name}
              onObservation={onObservation}
            />
          )}
          <p className="muted chart-note">
            Кружки обозначают наблюдения на исходных датах и явно отмеченные прогнозные точки.
            Недельная цена действует семь дней; последняя продолжается до конца выбранной
            фактической части. Наведите на линию или её легенду, чтобы показать кружки; легенда
            также доступна с клавиатуры. Разрыв означает отсутствие данных.
          </p>
          {context.dateTo > serverDate && (
            <p className="muted chart-note">
              Полупрозрачные линии — прогноз по сезонной исторической динамике Росстата,
              рассчитанный на текущую дату. Чем дальше горизонт и резче изменения рынка, тем выше
              неопределённость. Это оценка для планирования; фактическая цена может отличаться. При
              недостатке истории соседние доступные точки соединяются прямым отрезком; новые
              прогнозные точки при этом не создаются.
            </p>
          )}
        </>
      )}
    </section>
  );
}
