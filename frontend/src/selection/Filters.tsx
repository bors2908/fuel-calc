import { useState } from "react";
import type { Selection } from "../api/types";
import { frequencyNames, sourceNames } from "../api/types";
import { defaults, presetRange } from "./model";
import type { Catalog } from "./model";
import "./filters.css";

type Props = {
  catalog: Catalog;
  value: Selection;
  dirty: boolean;
  busy: boolean;
  error: string | null;
  onChange: (value: Selection) => void;
  onApply: () => void;
  onCancel: () => void;
};
export function Filters({
  catalog,
  value,
  dirty,
  busy,
  error,
  onChange,
  onApply,
  onCancel,
}: Props) {
  const [search, setSearch] = useState("");
  const regions = catalog.regions.filter(
    (r) =>
      r.name.toLocaleLowerCase("ru").includes(search.toLocaleLowerCase("ru")) ||
      value.regionCodes.includes(r.code),
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
        onApply();
      }}
      aria-label="Выбор данных"
    >
      <div className="filter-search">
        <label htmlFor="region-search">Найти регион</label>
        <input
          id="region-search"
          type="search"
          value={search}
          onChange={(event) => setSearch(event.target.value)}
          placeholder="Начните вводить название"
        />
      </div>
      <div className="filter-main">
        <div>
          <label htmlFor="base-region">Мой регион</label>
          <select
            id="base-region"
            value={value.baseRegionCode}
            onChange={(event) => setBase(event.target.value)}
            required
          >
            <option value="">Выберите регион</option>
            {regions.map((region) => (
              <option
                key={region.code}
                value={region.code}
              >
                {region.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="fuel">Топливо</label>
          <select
            id="fuel"
            value={value.fuelType}
            onChange={(event) => {
              const fuel = catalog.fuels.find((f) => f.code === event.target.value);
              if (fuel) {
                onChange({ ...value, fuelType: fuel.code });
              }
            }}
          >
            {catalog.fuels.map((fuel) => (
              <option
                key={fuel.code}
                value={fuel.code}
              >
                {fuel.name}
              </option>
            ))}
          </select>
        </div>
        <div>
          <label htmlFor="period">Период</label>
          <select
            id="period"
            value={preset?.toString() ?? "custom"}
            onChange={(event) =>
              onChange({ ...value, ...presetRange(catalog.serverDate, Number(event.target.value)) })
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
        <button
          className="button-primary"
          type="submit"
        >
          {busy ? "Обновить выбор" : "Показать"}
        </button>
      </div>
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
          <label htmlFor="date-to">По дату включительно</label>
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
      <details className="extra-regions">
        <summary>
          Сравнить регионы · {Math.max(0, value.regionCodes.length - 1)} из 3 дополнительных
        </summary>
        <fieldset>
          <legend>Дополнительные регионы</legend>
          <div className="region-options">
            {regions
              .filter((r) => r.code !== value.baseRegionCode)
              .map((region) => (
                <label key={region.code}>
                  <input
                    type="checkbox"
                    checked={value.regionCodes.includes(region.code)}
                    disabled={
                      !value.baseRegionCode ||
                      (!value.regionCodes.includes(region.code) && value.regionCodes.length >= 4)
                    }
                    onChange={(event) =>
                      onChange({
                        ...value,
                        regionCodes: event.target.checked
                          ? [...value.regionCodes, region.code]
                          : value.regionCodes.filter((code) => code !== region.code),
                      })
                    }
                  />
                  {region.name}
                </label>
              ))}
          </div>
        </fieldset>
      </details>
      <details className="data-parameters">
        <summary>
          Параметры данных · {sourceNames[value.preferredSource]} ·{" "}
          {frequencyNames[value.frequency]}
        </summary>
        <fieldset>
          <legend>Предпочтительный источник</legend>
          <div className="source-options">
            {(["ROSSTAT", "RUSTATA", "WHEREFUEL"] as const).map((source) => (
              <label key={source}>
                <input
                  type="radio"
                  name="preferred-source"
                  value={source}
                  checked={value.preferredSource === source}
                  onChange={() => onChange({ ...value, preferredSource: source })}
                />
                {sourceNames[source]}
              </label>
            ))}
          </div>
        </fieldset>
        <fieldset>
          <legend>Периодичность наблюдений</legend>
          <div className="source-options">
            {(["WEEKLY", "MONTHLY"] as const).map((frequency) => (
              <label key={frequency}>
                <input
                  type="radio"
                  name="frequency"
                  checked={value.frequency === frequency}
                  onChange={() => onChange({ ...value, frequency })}
                />
                {frequencyNames[frequency]}
              </label>
            ))}
          </div>
        </fieldset>
        <p className="muted">
          Если у источника нет цены за нужный период, сервис использует резерв. Фактический источник
          указан у каждого значения.
        </p>
      </details>
      <div className="filter-actions">
        {dirty && <span role="status">Есть изменения в фильтрах</span>}
        <button
          type="button"
          onClick={onCancel}
        >
          Отменить изменения
        </button>
        <button
          type="button"
          onClick={() =>
            onChange({
              ...defaults(catalog.serverDate),
              baseRegionCode: value.baseRegionCode,
              regionCodes: value.baseRegionCode ? [value.baseRegionCode] : [],
              preferredSource: value.preferredSource,
            })
          }
        >
          Сбросить фильтры
        </button>
      </div>
      {error && (
        <p
          className="field-error"
          role="alert"
        >
          {error}
        </p>
      )}
    </form>
  );
}
