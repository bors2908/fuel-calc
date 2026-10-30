import { useId, useRef, useState } from "react";
import { Amount } from "../../ui/Amount";
import { date, number, timestamp } from "../../ui/format";
import { sources } from "../../sources/model";
import { savedLimit, savedTotals } from "./model";
import type { SavedList, SavedQuote } from "./model";
import { downloadSavedCsv } from "./csv";
import "./saved-refuelings.css";

type Props = {
  list: SavedList;
  warning: string | null;
  error: string | null;
  onRemove: (id: string) => void;
  onClear: () => void;
};
export function SavedRefuelings({ list, warning, error, onRemove, onClear }: Props) {
  const id = useId();
  const heading = useRef<HTMLHeadingElement>(null);
  const [confirmClear, setConfirmClear] = useState(false);
  const totals = savedTotals(list.entries);
  return (
    <section
      className="saved-refuelings surface"
      aria-labelledby={id}
    >
      <div className="saved-heading">
        <div>
          <p className="eyebrow">План заправок</p>
          <h2
            id={id}
            ref={heading}
            tabIndex={-1}
          >
            Список заправок
          </h2>
          <p className="muted">{list.region?.name ?? "Один регион · до 10 заправок"}</p>
        </div>
        <span
          className="saved-count"
          role="status"
          aria-label="Сохранено заправок"
        >
          {list.entries.length}/{savedLimit}
        </span>
      </div>
      {warning && (
        <p
          className="saved-notice"
          role="alert"
        >
          {warning}
        </p>
      )}
      {error && (
        <p
          className="field-error"
          role="alert"
        >
          {error}
        </p>
      )}
      {list.entries.length ? (
        <div
          className="table-scroll"
          role="region"
          aria-label="Сохранённые заправки"
          tabIndex={0}
        >
          <table>
            <caption>
              {list.region?.name} · сохранённые расчёты. Официальная и рыночная цены — две отдельные
              оценки расходов.
            </caption>
            <thead>
              <tr>
                <th scope="col">Дата заправки</th>
                <th scope="col">Топливо</th>
                <th scope="col">Объём, л</th>
                <th scope="col">По официальной цене</th>
                <th scope="col">По рыночной цене</th>
                <th scope="col">Действия</th>
              </tr>
            </thead>
            <tbody>
              {list.entries.map((entry, index) => (
                <tr key={entry.id}>
                  <th scope="row">
                    {date(entry.requestedDate)}
                    <small className="saved-detail">Расчёт: {timestamp(entry.calculatedAt)}</small>
                  </th>
                  <td>{entry.fuel.name}</td>
                  <td>{number(entry.volumeLiters)}</td>
                  {sources.map((source) => (
                    <td
                      key={source}
                      data-source={source}
                    >
                      <SavedCost quote={entry.quotes[source]} />
                    </td>
                  ))}
                  <td>
                    <button
                      type="button"
                      aria-label={`Удалить заправку ${index + 1}`}
                      onClick={() => {
                        onRemove(entry.id);
                        heading.current?.focus();
                      }}
                    >
                      Удалить
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
            <tfoot>
              <tr>
                <th
                  scope="row"
                  colSpan={2}
                >
                  Итого
                </th>
                <td>{number(totals.volumeLiters)}</td>
                {sources.map((source) => (
                  <td
                    key={source}
                    data-source={source}
                  >
                    <strong>
                      <Amount
                        value={totals[source].amount}
                        unit="₽"
                      />
                    </strong>
                    <small className="saved-detail">
                      {totals[source].count === list.entries.length
                        ? "Полный итог"
                        : "Частичный итог"}{" "}
                      · {totals[source].count} из {list.entries.length}
                    </small>
                  </td>
                ))}
                <td />
              </tr>
            </tfoot>
          </table>
        </div>
      ) : (
        <p className="saved-empty">
          Рассчитайте заправку для одного региона и добавьте результат в список.
        </p>
      )}
      <div className="saved-actions">
        <button
          type="button"
          disabled={!list.entries.length}
          onClick={() => downloadSavedCsv(list)}
        >
          Скачать CSV
        </button>
        <button
          type="button"
          disabled={!list.entries.length && !warning}
          onClick={() => setConfirmClear(true)}
        >
          Очистить список
        </button>
      </div>
      {confirmClear && (
        <div
          className="saved-confirm"
          role="group"
          aria-label="Очистка списка"
        >
          <p>Удалить все сохранённые заправки?</p>
          <button
            type="button"
            onClick={() => {
              onClear();
              setConfirmClear(false);
              heading.current?.focus();
            }}
          >
            Удалить все заправки
          </button>
          <button
            type="button"
            onClick={() => setConfirmClear(false)}
          >
            Отмена
          </button>
        </div>
      )}
      <p className="muted saved-footnote">
        Список хранится только в этом браузере. Цены зафиксированы на момент расчёта и автоматически
        не обновляются.
      </p>
    </section>
  );
}
function SavedCost({ quote }: { quote: SavedQuote | null }) {
  return quote ? (
    <>
      <strong>
        <Amount
          value={quote.totalRub}
          unit="₽"
        />
      </strong>
      <small className="saved-detail">
        {number(quote.price.priceRubPerLiter)} ₽/л · {date(quote.price.period.referenceDate)}
      </small>
      <small className="saved-detail">
        {quote.price.forecast ? "Прогноз на неделю" : "Наблюдение"}
      </small>
    </>
  ) : (
    <span className="muted">Нет данных</span>
  );
}
