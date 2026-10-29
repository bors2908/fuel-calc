import { useId } from "react";
import type { Catalog, BaseSelection } from "./types";

export function FuelSelector({
  fuels,
  value,
  onChange,
}: {
  fuels: Catalog["fuels"];
  value: BaseSelection["fuelType"];
  onChange: (fuel: BaseSelection["fuelType"]) => void;
}) {
  const name = useId();
  return (
    <fieldset className="fuel-selector">
      <legend>Топливо</legend>
      <div className="fuel-options">
        {fuels.map((fuel) => (
          <label key={fuel.code}>
            <input
              type="radio"
              name={name}
              value={fuel.code}
              checked={value === fuel.code}
              onChange={() => onChange(fuel.code)}
            />
            <span title={fuel.name}>{fuel.code === "DIESEL" ? "Дизель" : fuel.name}</span>
          </label>
        ))}
      </div>
    </fieldset>
  );
}
