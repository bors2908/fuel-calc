import { useEffect, useRef, useState } from "react";
import { api } from "../api/client";
import type { Schema } from "../api/types";
import { useAsyncRequest } from "../ui/useAsyncRequest";
import { Feedback, Loading } from "../ui/Feedback";
import { date, safeLink, timestamp } from "../ui/format";
import { presentPrice } from "../prices/presentation";
import { PriceValue } from "../prices/PriceValue";

import "./observation-dialog.css";

type Props = { id: string; onClose: () => void };
export function ObservationDialog({ id, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [attempt, setAttempt] = useState(0);
  const { value, error, run } = useAsyncRequest<Schema<"PriceObservationResponse">>({
    keepPrevious: false,
  });
  useEffect(() => {
    const target = dialog.current;
    const previous = document.activeElement;
    target?.showModal();
    return () => {
      target?.close();
      if (previous instanceof HTMLElement && previous.isConnected) {
        previous.focus();
      }
    };
  }, []);
  useEffect(() => {
    void run((signal) => api.observation(id, signal));
  }, [id, attempt, run]);
  const observation = value?.observation;
  return (
    <dialog
      ref={dialog}
      onCancel={onClose}
      className="observation-dialog"
      aria-labelledby="observation-title"
    >
      <div className="section-heading">
        <h2 id="observation-title">Об источнике и дате</h2>
        <button
          type="button"
          onClick={onClose}
          autoFocus
        >
          Закрыть
        </button>
      </div>
      {!observation && !error && <Loading />}
      {error && (
        <Feedback
          onRetry={() => {
            setAttempt(attempt + 1);
          }}
        >
          {error}
        </Feedback>
      )}
      {observation && (
        <>
          <p>
            {observation.region.name} · {observation.fuelType.name}
          </p>
          <PriceValue price={presentPrice(observation.price)} />
          <dl className="provenance-list">
            <dt>Дата наблюдения</dt>
            <dd>{date(observation.price.period.referenceDate)}</dd>
            <dt>Дата публикации</dt>
            <dd>{date(observation.publicationDate)}</dd>
            <dt>Загружено в архив</dt>
            <dd>{timestamp(observation.loadedAt)}</dd>
            <dt>Поставщик данных</dt>
            <dd>{observation.provider.name}</dd>
            <dt>Последнее доступное наблюдение</dt>
            <dd>{date(observation.latestAvailableDate)}</dd>
          </dl>
          {safeLink(observation.provider.url) && (
            <a
              href={safeLink(observation.provider.url)}
              target="_blank"
              rel="noreferrer"
            >
              Первоисточник ↗
            </a>
          )}
          <p className="muted">
            Даты наблюдения, публикации и загрузки имеют разный смысл. Неизвестная дата не
            заменяется датой загрузки.
          </p>
        </>
      )}
    </dialog>
  );
}
