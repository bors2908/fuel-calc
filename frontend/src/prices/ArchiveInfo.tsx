import type { Schema } from "../api/types";
import { sourceNames } from "../api/types";
import { timestamp } from "../ui/format";

export function ArchiveInfo({ updates }: { updates: Schema<"SourceUpdates"> }) {
  return (
    <section className="archive-info">
      <h2>Два взгляда на цену</h2>
      <p>
        Официальная статистика Росстата выходит по неделям. Рыночный индекс BenzUp сохраняется по
        дням. Это отдельные ряды: один источник не заменяет другой.
      </p>
      <ul className="source-updates">
        {Object.entries(updates).map(([key, update]) => (
          <li key={key}>
            <strong>
              {sourceNames[update.source]} ·{" "}
              {update.source === "OFFICIAL" ? "Росстат / rustata" : "BenzUp"}
            </strong>
            <span>
              {
                {
                  READY: "Данные загружены",
                  UNAVAILABLE: "Источник временно недоступен; сохранённая история доступна",
                  UNKNOWN: "Загрузок пока нет",
                }[update.status]
              }
            </span>
            <small>Последняя успешная загрузка: {timestamp(update.lastSuccessAt)}</small>
          </li>
        ))}
      </ul>
      <p className="muted">
        Дата наблюдения указана у цены. Время загрузки показывает, когда данные появились в сервисе.
      </p>
    </section>
  );
}
