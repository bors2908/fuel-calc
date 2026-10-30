import type { ChartGroup } from "./model";
import { sourceInfo } from "../../sources/model";
export function ChartLegend({
  groups,
  baseRegionCode,
  activeSeries,
  onActivate,
}: {
  groups: ChartGroup[];
  baseRegionCode: string;
  activeSeries: string | null;
  onActivate: (key: string | null) => void;
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
            <button
              type="button"
              className="legend-item"
              key={item.key}
              data-source={item.source}
              data-active={activeSeries === item.key}
              aria-label={`Показать точки: ${group.region.name} · ${sourceInfo[item.source].name}`}
              disabled={!item.points.some((point) => point.priceRubPerLiter !== null)}
              onPointerEnter={() => onActivate(item.key)}
              onPointerLeave={() => onActivate(null)}
              onFocus={() => onActivate(item.key)}
              onBlur={() => onActivate(null)}
              onClick={() => onActivate(item.key)}
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
            </button>
          ))}
        </div>
      ))}
    </div>
  );
}
