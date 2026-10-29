import { useId, useState } from "react";
import type { BaseSelection, Catalog } from "./types";
import { filterRegions } from "./regionSearch";

export function AdditionalRegions({
  regions,
  value,
  onChange,
}: {
  regions: Catalog["regions"];
  value: BaseSelection;
  onChange: (regionCodes: string[]) => void;
}) {
  const id = useId();
  const [search, setSearch] = useState("");
  const matches = filterRegions(regions, search).filter(
    (region) => region.okatoCode !== value.baseRegionCode,
  );
  return (
    <details className="extra-regions">
      <summary>
        Сравнить регионы · {Math.max(0, value.regionCodes.length - 1)} из 3 дополнительных
      </summary>
      <fieldset>
        <legend>Дополнительные регионы</legend>
        <div className="extra-region-search">
          <label htmlFor={id}>Найти регион</label>
          <input
            id={id}
            type="search"
            autoComplete="off"
            placeholder="Введите название региона"
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </div>
        <div className="region-options">
          {matches.map((region) => (
            <label key={region.okatoCode}>
              <input
                type="checkbox"
                checked={value.regionCodes.includes(region.okatoCode)}
                disabled={
                  !value.regionCodes.includes(region.okatoCode) && value.regionCodes.length >= 4
                }
                onChange={(event) =>
                  onChange(
                    event.target.checked
                      ? [...value.regionCodes, region.okatoCode]
                      : value.regionCodes.filter((code) => code !== region.okatoCode),
                  )
                }
              />
              {region.name}
            </label>
          ))}
        </div>
        {matches.length === 0 && (
          <p
            className="muted"
            role="status"
          >
            Регион не найден. Попробуйте другое название.
          </p>
        )}
      </fieldset>
    </details>
  );
}
