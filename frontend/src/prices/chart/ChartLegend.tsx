import type { ChartGroup } from "./model";
import { sourceInfo } from "../../sources/model";
export function ChartLegend({
  groups,
  baseRegionCode,
}: {
  groups: ChartGroup[];
  baseRegionCode: string;
}) {
  return (
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
            {group.region.name}
            {group.region.okatoCode === baseRegionCode && " · мой регион"}
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
                {sourceInfo[item.source].name}
                <small>
                  {!item.points.some((point) => point.priceRubPerLiter !== null)
                    ? "Нет наблюдений за период"
                    : sourceInfo[item.source].caption}
                </small>
              </span>
            </div>
          ))}
        </div>
      ))}
    </div>
  );
}
