import { useId } from "react";
import type { Schema } from "../api/types";
import type { PriceSorting } from "./sorting";

type Props = {
  official: Schema<"Comparison">;
  market: Schema<"Comparison">;
  value: PriceSorting;
  disabled: boolean;
  onSort: (sorting: PriceSorting) => void;
};
export function ComparisonSorting({ official, market, value, disabled, onSort }: Props) {
  const id = useId();
  const sortValue = `${value.source}:${value.sortBy}:${value.sortOrder}`;
  return (
    <div className="comparison-sort">
      <label htmlFor={id}>Порядок регионов</label>
      <select
        id={id}
        disabled={disabled}
        value={sortValue}
        onChange={(event) => {
          const [source, sortBy, sortOrder] = event.target.value.split(":");
          if (
            (source === "OFFICIAL" || source === "MARKET") &&
            (sortBy === "PRICE" || sortBy === "REGION_NAME") &&
            (sortOrder === "ASC" || sortOrder === "DESC")
          ) {
            onSort({ source, sortBy, sortOrder });
          }
        }}
      >
        <option
          value="OFFICIAL:PRICE:ASC"
          disabled={!["AVAILABLE", "SINGLE_REGION"].includes(official.status)}
        >
          Официальная: сначала дешевле
        </option>
        <option
          value="OFFICIAL:PRICE:DESC"
          disabled={!["AVAILABLE", "SINGLE_REGION"].includes(official.status)}
        >
          Официальная: сначала дороже
        </option>
        <option
          value="MARKET:PRICE:ASC"
          disabled={!["AVAILABLE", "SINGLE_REGION"].includes(market.status)}
        >
          Рыночная: сначала дешевле
        </option>
        <option
          value="MARKET:PRICE:DESC"
          disabled={!["AVAILABLE", "SINGLE_REGION"].includes(market.status)}
        >
          Рыночная: сначала дороже
        </option>
        <option value={`${value.source}:REGION_NAME:ASC`}>Название: А–Я</option>
        <option value={`${value.source}:REGION_NAME:DESC`}>Название: Я–А</option>
      </select>
    </div>
  );
}
