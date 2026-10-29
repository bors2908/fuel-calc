import { useState } from "react";
import type { Schema } from "../api/types";
import { date, number, signed } from "../ui/format";
import { Feedback, Loading } from "../ui/Feedback";
import { PriceSource } from "../prices/PriceValue";
import "./calculator.css";
import { normalizeVolume } from "./model";

type Props = {
  disabled: boolean;
  calculating: boolean;
  result: Schema<"RefuelingCalculation"> | null;
  error: string | null;
  onCalculate: (volume: string) => void;
  onChange: () => void;
};
export function Calculator({ disabled, calculating, result, error, onCalculate, onChange }: Props) {
  const [volume, setVolume] = useState("600");
  const [validation, setValidation] = useState<string | null>(null);
  function change(value: string) {
    setVolume(value);
    setValidation(null);
    onChange();
  }
  return (
    <section
      id="calculator"
      className="calculator"
    >
      <div className="calculator-input">
        <p className="eyebrow">От цены за литр — к вашим расходам</p>
        <h2>Сколько стоит ваша заправка</h2>
        <p>Один объём. Все выбранные регионы.</p>
        <form
          onSubmit={(event) => {
            event.preventDefault();
            const value = normalizeVolume(volume);
            if (value === null) {
              setValidation("Введите от 0,01 до 1 000 000 л, не более двух знаков после запятой.");
            } else {
              setValidation(null);
              onCalculate(value);
            }
          }}
        >
          <label htmlFor="volume">Объём, л</label>
          <input
            id="volume"
            type="text"
            inputMode="decimal"
            autoComplete="off"
            value={volume}
            onChange={(event) => change(event.target.value)}
            aria-invalid={validation ? true : undefined}
            aria-describedby="volume-hint volume-error"
          />
          <p
            className="volume-hint"
            id="volume-hint"
          >
            От 0,01 до 1 000 000 л
          </p>
          <div
            className="volume-presets"
            role="group"
            aria-label="Быстрый ввод объёма"
          >
            {["200", "600", "1000"].map((value) => (
              <button
                key={value}
                type="button"
                onClick={() => change(value)}
              >
                {value} л
              </button>
            ))}
          </div>
          <p
            id="volume-error"
            className="volume-error"
            role={validation ? "alert" : undefined}
          >
            {validation}
          </p>
          <button
            className="calculate-button"
            type="submit"
            disabled={disabled || calculating}
          >
            {calculating ? "Рассчитываем…" : "Рассчитать"}
          </button>
        </form>
        {disabled && <p className="volume-hint">Примените фильтры и дождитесь сопоставимых цен.</p>}
      </div>
      <div
        className="calculation-result"
        aria-live="polite"
      >
        {calculating && <Loading>Рассчитываем по актуальным ценам…</Loading>}
        {error && <Feedback>{error}</Feedback>}
        {result ? (
          <>
            <p>Оценочная стоимость · {result.context.baseRegion.name}</p>
            <p className="total-value">
              {number(result.baseTotalRub)} <span>₽</span>
            </p>
            <p className="muted">
              {number(result.volumeLiters)} л · {result.context.fuelType.name} ·{" "}
              {result.comparison.period?.label ?? date(result.context.dateTo)}
            </p>
            {result.snapshotChanged && (
              <p
                className="snapshot-notice"
                role="status"
              >
                Цены изменились после просмотра. Расчёт и сравнение обновлены.
              </p>
            )}
            <div>
              {result.comparison.data.map((row) => (
                <div
                  className="calculation-row"
                  key={row.region.code}
                >
                  <strong>
                    {row.region.name}
                    {row.relation === "BASE" && " · мой"}
                  </strong>
                  <span>{number(row.totalRub)} ₽</span>
                  <small>
                    {row.relation === "BASE"
                      ? "Основной регион"
                      : row.relation === "EQUAL"
                        ? "Стоимость одинакова"
                        : `${signed(row.differenceRub)} ₽ к моему региону`}
                  </small>
                  <PriceSource
                    price={row.price}
                    preferredSource={result.context.preferredSource}
                  />
                </div>
              ))}
            </div>
          </>
        ) : (
          !calculating &&
          !error && (
            <div className="calculation-placeholder">
              <span aria-hidden="true">↗</span>
              <h3>Спланируйте расходы</h3>
              <p>Введите объём, чтобы узнать стоимость и сравнить затраты по выбранным регионам.</p>
            </div>
          )
        )}
        <p className="calculation-disclaimer">
          Оценка по региональной средней цене. Доставка, скидки и условия поставщика не учтены.
        </p>
      </div>
    </section>
  );
}
