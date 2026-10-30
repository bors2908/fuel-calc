import { useId } from "react";
import { priceOnDate } from "./model";
import type { ChartGroup } from "./model";
import { date } from "../../ui/format";
import { sourceInfo } from "../../sources/model";
import { PriceValue } from "../PriceValue";
export function ChartDetails({
  groups,
  baseRegionCode,
  dates,
  selectedDate,
  onSelect,
  onObservation,
}: {
  groups: ChartGroup[];
  baseRegionCode: string;
  dates: string[];
  selectedDate: string;
  onSelect: (date: string) => void;
  onObservation: (id: string) => void;
}) {
  const id = useId();
  return (
    <div className="chart-detail">
      <label htmlFor={id}>Дата на графике — точные значения</label>
      <select
        id={id}
        value={selectedDate}
        onChange={(event) => onSelect(event.target.value)}
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
              {group.region.name}
              {group.region.okatoCode === baseRegionCode && " · мой регион"}
            </h3>
            <div className="chart-region-sources">
              {group.series.map((item) => {
                const point = priceOnDate(item, selectedDate);
                return (
                  <div
                    key={item.key}
                    data-source={item.source}
                  >
                    <h4>{sourceInfo[item.source].name}</h4>
                    {point ? (
                      <PriceValue
                        price={point}
                        onObservation={onObservation}
                      />
                    ) : (
                      <p className="muted">Нет наблюдения на эту дату</p>
                    )}
                  </div>
                );
              })}
            </div>
          </section>
        ))}
      </div>
    </div>
  );
}
