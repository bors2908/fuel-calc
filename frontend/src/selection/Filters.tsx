import type { Selection } from "../api/types";
import { frequencyNames, sourceNames } from "../api/types";
import { defaults, presetRange } from "./model";
import type { Catalog } from "./model";
import { RegionSelect } from "./RegionSelect";
import "./filters.css";

type Props = {
  catalog: Catalog;
  value: Selection;
  error: string | null;
  onChange: (value: Selection) => void;
};
export function Filters({ catalog, value, error, onChange }: Props) {
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
      <details className="extra-regions">
        <summary>
          Сравнить регионы · {Math.max(0, value.regionCodes.length - 1)} из 3 дополнительных
        </summary>
        <fieldset>
          <legend>Дополнительные регионы</legend>
          <div className="region-options">
            {catalog.regions
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
            {(["ROSSTAT", "RUSTATA", "BENZUP"] as const).map((source) => (
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
            {(["DAILY", "WEEKLY", "MONTHLY"] as const).map((frequency) => (
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
      <button
        className="filter-reset"
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
