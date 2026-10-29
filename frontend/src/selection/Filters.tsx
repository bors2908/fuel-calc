import { useId, useState } from "react";
import type { BaseSelection, Catalog, PriceSelection, RefuelingSelection } from "./types";
import { priceDefaults, refuelingDefaults } from "./model";
import { RegionSelect } from "./RegionSelect";
import { FuelSelector } from "./FuelSelector";
import { DateField, DateRangeFields, PeriodPreset } from "./DateControls";
import { AdditionalRegions } from "./AdditionalRegions";
import "./filters.css";

type Props = { catalog: Catalog; error: string | null } & (
  | { dateMode?: "range"; value: PriceSelection; onChange: (value: PriceSelection) => void }
  | { dateMode: "date"; value: RefuelingSelection; onChange: (value: RefuelingSelection) => void }
);
export function Filters(props: Props) {
  const { catalog, value, error } = props;
  const errorId = useId();
  const [resetCount, setResetCount] = useState(0);
  function changeBase(base: BaseSelection) {
    if (props.dateMode === "date") {
      props.onChange({ ...props.value, ...base });
    } else {
      props.onChange({ ...props.value, ...base });
    }
  }
  function setBase(code: string) {
    const others = value.regionCodes.filter(
      (item) => item !== value.baseRegionCode && item !== code,
    );
    changeBase({ ...value, baseRegionCode: code, regionCodes: code ? [code, ...others] : [] });
  }
  function reset() {
    setResetCount((count) => count + 1);
    const base = {
      baseRegionCode: value.baseRegionCode,
      regionCodes: value.baseRegionCode ? [value.baseRegionCode] : [],
    };
    if (props.dateMode === "date") {
      props.onChange({ ...refuelingDefaults(catalog.serverDate), ...base });
    } else {
      props.onChange({ ...priceDefaults(catalog.serverDate), ...base });
    }
  }
  return (
    <form
      className="filters"
      aria-label="Выбор данных"
      onSubmit={(event) => event.preventDefault()}
    >
      <div className="filter-main">
        <RegionSelect
          regions={catalog.regions}
          value={value.baseRegionCode}
          onChange={setBase}
        />
        <FuelSelector
          fuels={catalog.fuels}
          value={value.fuelType}
          onChange={(fuelType) => changeBase({ ...value, fuelType })}
        />
        {props.dateMode === "date" ? (
          <DateField
            label="Дата заправки"
            value={props.value.date}
            max={catalog.serverDate}
            {...(error ? { errorId } : {})}
            onChange={(date) => props.onChange({ ...props.value, date })}
          />
        ) : (
          <PeriodPreset
            value={props.value}
            serverDate={catalog.serverDate}
            onChange={props.onChange}
          />
        )}
      </div>
      {props.dateMode !== "date" && (
        <DateRangeFields
          value={props.value}
          serverDate={catalog.serverDate}
          errorId={error ? errorId : undefined}
          onChange={props.onChange}
        />
      )}
      <AdditionalRegions
        key={resetCount}
        regions={catalog.regions}
        value={value}
        onChange={(regionCodes) => changeBase({ ...value, regionCodes })}
      />
      <button
        className="filter-reset"
        type="button"
        onClick={reset}
      >
        Сбросить фильтры
      </button>
      {error && (
        <p
          id={errorId}
          className="field-error"
          role="alert"
        >
          {error}
        </p>
      )}
    </form>
  );
}
