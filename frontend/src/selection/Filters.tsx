import { useId, useState } from "react";
import { regionName } from "../regions/regionName";
import type { Selection } from "../api/types";
import { defaults, presetRange } from "./model";
import type { Catalog, DateMode } from "./model";
import { RegionSelect } from "./RegionSelect";
import "./filters.css";

type Props = {
  catalog: Catalog;
  value: Selection;
  error: string | null;
  onChange: (value: Selection) => void;
  dateMode?: DateMode;
};
export function Filters({ catalog, value, error, onChange, dateMode = "range" }: Props) {
  const searchId = useId();
  const [regionSearch, setRegionSearch] = useState("");
  const matches = catalog.regions.filter(
    (region) =>
      region.okatoCode !== value.baseRegionCode &&
      regionName(region)
        .toLocaleLowerCase("ru")
        .includes(regionSearch.trim().toLocaleLowerCase("ru")),
  );
  const preset = [1, 3, 12].find((months) => {
    const range = presetRange(catalog.serverDate, months);
    return range.dateFrom === value.dateFrom && range.dateTo === value.dateTo;
  });
  function setBase(code: string) {
    const others = value.regionCodes.filter(
      (item) => item !== value.baseRegionCode && item !== code,
    );
    onChange({ ...value, baseRegionCode: code, regionCodes: code ? [code, ...others] : [] });
  }
  return (
    <form
      className="filters"
      onSubmit={(event) => {
        event.preventDefault();
      }}
      aria-label="Выбор данных"
    >
      <div className="filter-main">
        <RegionSelect
          regions={catalog.regions}
          value={value.baseRegionCode}
          onChange={setBase}
        />
        <fieldset className="fuel-selector">
          <legend>Топливо</legend>
          <div className="fuel-options">
            {catalog.fuels.map((fuel) => (
              <label key={fuel.code}>
                <input
                  type="radio"
                  name="fuel"
                  value={fuel.code}
                  checked={value.fuelType === fuel.code}
                  onChange={() => onChange({ ...value, fuelType: fuel.code })}
                />
                <span title={fuel.name}>{fuel.code === "DIESEL" ? "Дизель" : fuel.name}</span>
              </label>
            ))}
          </div>
        </fieldset>
        {dateMode === "date" ? (
          <div>
            <label htmlFor="refueling-date">Дата заправки</label>
            <input
              id="refueling-date"
              type="date"
              required
              max={catalog.serverDate}
              value={value.dateTo}
              aria-invalid={error ? true : undefined}
              aria-describedby={error ? "selection-error" : undefined}
              onChange={(event) =>
                onChange({ ...value, dateFrom: event.target.value, dateTo: event.target.value })
              }
            />
          </div>
        ) : (
          <div>
            <label htmlFor="period">Период</label>
            <select
              id="period"
              value={preset?.toString() ?? "custom"}
              onChange={(event) =>
                onChange({
                  ...value,
                  ...presetRange(catalog.serverDate, Number(event.target.value)),
                })
              }
            >
              <option
                value="custom"
                disabled
              >
                Свой диапазон
              </option>
              <option value="1">1 месяц</option>
              <option value="3">3 месяца</option>
              <option value="12">Год</option>
            </select>
          </div>
        )}
      </div>
      {dateMode === "range" && (
        <div className="date-range">
          <div>
            <label htmlFor="date-from">С даты</label>
            <input
              id="date-from"
              type="date"
              required
              max={catalog.serverDate}
              value={value.dateFrom}
              onChange={(event) => onChange({ ...value, dateFrom: event.target.value })}
            />
          </div>
          <span aria-hidden="true">—</span>
          <div>
            <label htmlFor="date-to">По дату</label>
            <input
              id="date-to"
              type="date"
              required
              min={value.dateFrom}
              max={catalog.serverDate}
              value={value.dateTo}
              onChange={(event) => onChange({ ...value, dateTo: event.target.value })}
            />
          </div>
        </div>
      )}
      <details className="extra-regions">
        <summary>
          Сравнить регионы · {Math.max(0, value.regionCodes.length - 1)} из 3 дополнительных
        </summary>
        <fieldset>
          <legend>Дополнительные регионы</legend>
          <div className="extra-region-search">
            <label htmlFor={searchId}>Найти регион</label>
            <input
              id={searchId}
              type="search"
              autoComplete="off"
              placeholder="Введите название региона"
              value={regionSearch}
              onChange={(event) => setRegionSearch(event.target.value)}
            />
          </div>
          <div className="region-options">
            {matches.map((region) => (
              <label key={region.okatoCode}>
                <input
                  type="checkbox"
                  checked={value.regionCodes.includes(region.okatoCode)}
                  disabled={
                    !value.baseRegionCode ||
                    (!value.regionCodes.includes(region.okatoCode) && value.regionCodes.length >= 4)
                  }
                  onChange={(event) =>
                    onChange({
                      ...value,
                      regionCodes: event.target.checked
                        ? [...value.regionCodes, region.okatoCode]
                        : value.regionCodes.filter((code) => code !== region.okatoCode),
                    })
                  }
                />
                {regionName(region)}
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
      <button
        className="filter-reset"
        type="button"
        onClick={() => {
          setRegionSearch("");
          onChange({
            ...defaults(catalog.serverDate),
            ...(dateMode === "date" ? { dateFrom: catalog.serverDate } : {}),
            baseRegionCode: value.baseRegionCode,
            regionCodes: value.baseRegionCode ? [value.baseRegionCode] : [],
          });
        }}
      >
        Сбросить фильтры
      </button>
      {error && (
        <p
          id="selection-error"
          className="field-error"
          role="alert"
        >
          {error}
        </p>
      )}
    </form>
  );
}
