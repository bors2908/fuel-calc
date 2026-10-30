import type { Schema } from "../api/types";
import { sourceInfo } from "../sources/model";
import { timestamp } from "../ui/format";

import "./archive-info.css";

export function ArchiveInfo({ updates }: { updates: Schema<"SourceUpdates"> }) {
  return (
    <section
      className="archive-info"
      aria-labelledby="archive-info-title"
    >
      <h2 id="archive-info-title">Актуальность данных</h2>
      <ul className="source-updates">
        {Object.entries(updates).map(([key, update]) => (
          <li key={key}>
            <strong>
              {sourceInfo[update.source].name} · {sourceInfo[update.source].provider}
            </strong>
            <small>Последняя успешная загрузка: {timestamp(update.lastSuccessAt)}</small>
          </li>
        ))}
      </ul>
    </section>
  );
}
