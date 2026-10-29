import { useEffect, useRef, useState } from "react";
import { api, errorMessage } from "../api/client";
import type { Schema } from "../api/types";
import { Feedback, Loading } from "../ui/Feedback";
import { date, safeLink, timestamp } from "../ui/format";
import { PriceValue } from "../prices/PriceValue";

type Props = { id: string; preferredSource: Schema<"PriceSource">; onClose: () => void };
export function ObservationDialog({ id, preferredSource, onClose }: Props) {
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
            {observation.region.name} · {observation.fuelType.name}
          </p>
          <PriceValue
            price={observation.price}
            preferredSource={preferredSource}
          />
          <dl className="provenance-list">
            <dt>Дата наблюдения</dt>
            <dd>{date(observation.price.period.referenceDate)}</dd>
            <dt>Дата публикации</dt>
            <dd>{date(observation.publicationDate)}</dd>
            <dt>Время публикации</dt>
            <dd>{timestamp(observation.publishedAt)}</dd>
            <dt>Загружено в архив</dt>
            <dd>{timestamp(observation.loadedAt)}</dd>
            <dt>Производитель данных</dt>
            <dd>{observation.publisher.name}</dd>
            <dt>Канал получения</dt>
            <dd>{observation.price.source.provider.name}</dd>
            <dt>Методика</dt>
            <dd>
              {observation.price.source.aggregationMethod === "OFFICIAL_STATISTICS"
                ? "Официальная статистика"
                : "Региональная средняя по выборке АЗС"}
            </dd>
          </dl>
          <p>{observation.attribution}</p>
          {observation.note && <p>{observation.note}</p>}
          <div className="publication-links">
            {[
              { name: "Публикация", url: observation.publicationUrl },
              { name: "Производитель", url: observation.publisher.url },
              { name: "Канал получения", url: observation.providerUrl },
              { name: "Методика", url: observation.methodologyUrl },
            ].map((link) =>
              safeLink(link.url) ? (
                <a
                  key={link.name}
                  href={safeLink(link.url)}
                  target="_blank"
                  rel="noreferrer"
                >
                  {link.name} ↗
                </a>
              ) : null,
            )}
          </div>
          <p className="muted">
            Даты наблюдения, публикации и загрузки имеют разный смысл. Неизвестная дата не
            заменяется датой загрузки.
          </p>
        </>
      )}
    </dialog>
  );
}
