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
};
export function Trend({ context, market, official, onObservation }: Props) {
  const [view, setView] = useState<"chart" | "table">("chart");
  const [activeSeries, setActiveSeries] = useState<string | null>(null);
  const [active, setActive] = useState("");
  const model = prepareChart(context, official, market);
  const selectedDate = model.dates.includes(active) ? active : (model.dates.at(-1) ?? "");
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
        <p className="empty-state">В выбранном диапазоне нет истории. Попробуйте другой период.</p>
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
              />
              <ChartDetails
                groups={model.groups}
                baseRegionCode={context.baseRegion.okatoCode}
                dates={model.dates}
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
            Каждый кружок — сохранённое наблюдение на указанную дату. Недельная цена действует семь
            дней, а последний замер продолжается до конца выбранного периода. Наведите на линию или
            её легенду, чтобы показать кружки; легенда также доступна с клавиатуры. Разрыв означает
            отсутствие данных.
          </p>
        </>
      )}
    </section>
  );
}
