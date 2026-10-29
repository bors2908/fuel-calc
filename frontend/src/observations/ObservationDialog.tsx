import { regionName } from "../regions/regionName";
import { useEffect, useRef, useState } from "react";
import { api, errorMessage } from "../api/client";
import type { Schema } from "../api/types";
import { Feedback, Loading } from "../ui/Feedback";
import { date, safeLink, timestamp } from "../ui/format";
import { PriceValue } from "../prices/PriceValue";

type Props = { id: string; onClose: () => void };
export function ObservationDialog({ id, onClose }: Props) {
  const dialog = useRef<HTMLDialogElement>(null);
  const [attempt, setAttempt] = useState(0);
  const [state, setState] = useState<{
    value?: Schema<"PriceObservationResponse">;
    error?: string;
  }>({});
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
    const controller = new AbortController();
    let current = true;
    void api
      .observation(id, controller.signal)
      .then((value) => {
        if (current) {
          setState({ value });
        }
      })
      .catch((error: unknown) => {
        if (current) {
          setState({ error: errorMessage(error) });
        }
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [id, attempt]);
  const observation = state.value?.observation;
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
      {!observation && !state.error && <Loading />}
      {state.error && (
        <Feedback
          onRetry={() => {
            setState({});
            setAttempt(attempt + 1);
          }}
        >
          {state.error}
        </Feedback>
      )}
      {observation && (
        <>
          <p>
            {regionName(observation.region)} · {observation.fuelType.name}
          </p>
          <PriceValue price={observation.price} />
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
