import type { ChartGroup, ChartSeries } from "./model";
import { date } from "../../ui/format";
import { sourceInfo } from "../../sources/model";
import { PriceValue } from "../PriceValue";
export function HistoryTable({
  groups,
  series,
  dates,
  baseRegionCode,
  regionNames,
  fuelName,
  onObservation,
}: {
  groups: ChartGroup[];
  series: ChartSeries[];
  dates: string[];
  baseRegionCode: string;
  regionNames: string;
  fuelName: string;
  onObservation: (id: string) => void;
}) {
  return (
    <div
      className="table-scroll"
      tabIndex={0}
      role="region"
      aria-label="История цен"
    >
      <table>
        <caption>
          {regionNames} · {fuelName}, ₽/л. «—» — нет наблюдения на эту дату.
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
                {group.region.name}
                {group.region.okatoCode === baseRegionCode && " · мой регион"}
              </th>
            ))}
          </tr>
          <tr>
            {series.map((item) => (
              <th
                scope="col"
                key={item.key}
              >
                {sourceInfo[item.source].name}
              </th>
            ))}
          </tr>
        </thead>
        <tbody>
          {dates.map((day) => (
            <tr key={day}>
              <th scope="row">{date(day)}</th>
              {series.map((item) => {
                const point = item.byDate.get(day);
                return (
                  <td
                    key={item.key}
                    data-region={item.region.okatoCode}
                    data-source={item.source}
                  >
                    {point?.status === "available" ? (
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
  );
}
