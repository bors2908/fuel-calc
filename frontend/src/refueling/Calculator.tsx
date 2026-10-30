import { useState } from "react";
import type { ReactNode } from "react";
import type { Schema } from "../api/types";
import { sourceInfo } from "../sources/model";
import { date, number, signed } from "../ui/format";
import { Feedback, Loading } from "../ui/Feedback";
import { SourceLabel } from "../sources/SourceLabel";
import { Amount } from "../ui/Amount";
import "./calculator.css";
import { normalizeVolume } from "./model";

type Props = {
  disabled: boolean;
  calculating: boolean;
  result: Schema<"RefuelingCalculation"> | null;
  error: string | null;
  onCalculate: (volume: string) => void;
  onChange: () => void;
  children: ReactNode;
};
export function Calculator({
  disabled,
  calculating,
  result,
  error,
  onCalculate,
  onChange,
  children,
}: Props) {
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
      <header className="calculator-heading">
        <p className="eyebrow">От цены за литр — к вашим расходам</p>
        <h2>Сколько стоит ваша заправка</h2>
        <p>Один объём. Два расчёта: по официальной и рыночной цене.</p>
      </header>
      <div className="calculator-filters">{children}</div>
      <div className="calculator-input">
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
        {disabled && <p className="volume-hint">Выберите регион и корректную дату для расчёта.</p>}
      </div>
      <div
        className="calculation-result"
        aria-live="polite"
      >
        {calculating && <Loading>Рассчитываем по актуальным ценам…</Loading>}
        {error && <Feedback>{error}</Feedback>}
        {result ? (
          <div className="calculation-sources">
            {result.results.map((item) => (
              <section
                className="source-calculation"
                key={item.source}
                data-source={item.source}
                aria-label={`Расчёт: ${sourceInfo[item.source].name}`}
              >
                <h3>{sourceInfo[item.source].name}</h3>
                <p className="muted">
                  {result.context.baseRegion.name} · {number(result.volumeLiters)} л
                </p>
                {item.baseTotalRub !== null && item.comparison ? (
                  <>
                    <p className="total-value">
                      <Amount
                        value={item.baseTotalRub}
                        unit="₽"
                      />
                    </p>
                    <p className="muted">
                      {result.context.fuelType.name} · {date(item.comparison.period?.referenceDate)}
                    </p>

                    {item.comparison.data.map((row) => (
                      <div
                        className="calculation-row"
                        key={row.region.okatoCode}
                      >
                        <strong>
                          {row.region.name}
                          {row.relation === "BASE" && " · мой"}
                        </strong>
                        <span>
                          <Amount
                            value={row.totalRub}
                            unit="₽"
                          />
                        </span>
                        <small>
                          {row.relation === "BASE"
                            ? "Основной регион"
                            : row.relation === "EQUAL"
                              ? "Стоимость одинакова"
                              : `${signed(row.differenceRub)} ₽ к моему региону`}
                        </small>
                        <SourceLabel source={row.price.source} />
                        {row.price.forecast && (
                          <small>
                            Прогноз на неделю · база {date(row.price.forecast.baseDate)}
                          </small>
                        )}
                      </div>
                    ))}
                  </>
                ) : (
                  <p className="empty-calculation">
                    {item.status === "NO_COMMON_PERIOD"
                      ? "Нет общей даты для выбранных регионов. Измените дату заправки или состав регионов."
                      : result.context.dateTo > result.meta.serverDate
                        ? "Недостаточно официальной истории или текущих цен для прогноза."
                        : "Нет цен для расчёта на выбранную дату или ранее."}
                  </p>
                )}
              </section>
            ))}
          </div>
        ) : (
          !calculating &&
          !error && (
            <div className="calculation-placeholder">
              <span aria-hidden="true">↗</span>
              <h3>Две оценки расходов</h3>
              <p>
                Введите объём — покажем стоимость по официальной статистике и рыночному индексу.
              </p>
            </div>
          )
        )}
        <p className="calculation-disclaimer">
          Оценки по Росстату и рыночному индексу BenzUp. Даты источников могут различаться.
          Доставка, скидки и условия поставщика не учтены.
          {result && result.context.dateTo > result.meta.serverDate && (
            <>
              {" "}
              Будущая стоимость рассчитана по прогнозу сезонной исторической динамики Росстата.
              Резкие изменения рынка и длинный горизонт повышают неопределённость; фактическая
              стоимость может отличаться.
            </>
          )}
        </p>
      </div>
    </section>
  );
}
