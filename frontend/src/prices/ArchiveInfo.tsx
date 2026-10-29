import { useState } from "react";
import type { Schema } from "../api/types";
import { sourceNames, frequencyNames } from "../api/types";
import { timestamp } from "../ui/format";
import { Coverage } from "./Coverage";

export function ArchiveInfo({
  overview,
  busy,
  onRefresh,
}: {
  overview: Schema<"PriceOverview">;
  busy: boolean;
  onRefresh: () => void;
}) {
  const [coverageOpen, setCoverageOpen] = useState(false);
  return (
    <section className="archive-info">
      <h2>Откуда берутся данные</h2>
      <p>
        Цены — региональные средние из открытых источников. Источник и дата указаны у каждого
        значения. Обновление сайта не превращает архивное наблюдение в сегодняшнюю цену.
      </p>
      <ul className="source-updates">
        {Object.entries(overview.sourceUpdates).map(([key, update]) => (
          <li key={key}>
            <strong>
              {sourceNames[update.provider]} · {frequencyNames[update.frequency]}
            </strong>
            <span>
              {
                {
                  READY: "Поток доступен",
                  DELAYED: "Публикация задерживается",
                  UNAVAILABLE: "Источник временно недоступен; используется сохранённый архив",
                  UNKNOWN: "Срок обновления неизвестен",
                }[update.status]
              }
            </span>
            <small>Последняя успешная загрузка: {timestamp(update.lastSuccessAt)}</small>
            {update.nextExpectedPublicationAt && (
              <small>Ожидаемая публикация: {timestamp(update.nextExpectedPublicationAt)}</small>
            )}
          </li>
        ))}
      </ul>
      <button
        type="button"
        aria-expanded={coverageOpen}
        disabled={busy}
        onClick={() => setCoverageOpen(!coverageOpen)}
      >
        Покрытие архива
      </button>
      {coverageOpen && (
        <Coverage
          context={overview.context}
          version={overview.meta.dataVersion}
          onRefresh={onRefresh}
        />
      )}
    </section>
  );
}
