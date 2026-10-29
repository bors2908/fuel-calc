import { useEffect, useState } from "react";
import { api, ApiError, errorMessage } from "../api/client";
import type { Schema, PriceContext } from "../api/types";
import { frequencyNames } from "../api/types";
import { Feedback, Loading } from "../ui/Feedback";
import { fromContext } from "../selection/model";
import { date } from "../ui/format";

export function Coverage({
  context,
  version,
  onRefresh,
}: {
  context: PriceContext;
  version: string;
  onRefresh: () => void;
}) {
  const [state, setState] = useState<{ value?: Schema<"CoverageList">; error?: unknown }>({});
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    void api
      .coverage(fromContext(context), controller.signal, version)
      .then((value) => {
        if (current) {
          setState({ value });
        }
      })
      .catch((error: unknown) => {
        if (current) {
          setState({ error });
        }
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [context, version, attempt]);
  return (
    <div className="coverage">
      {!state.value && !state.error && <Loading />}
      {state.error !== undefined && (
        <Feedback
          onRetry={() => {
            if (state.error instanceof ApiError && state.error.status === 409) {
              onRefresh();
              return;
            }
            setState({});
            setAttempt(attempt + 1);
          }}
        >
          {errorMessage(state.error)}
        </Feedback>
      )}
      {state.value && (
        <ul>
          {state.value.data.map((row) => (
            <li key={`${row.region.code}-${row.fuelType.code}-${row.frequency}`}>
              <strong>
                {row.region.name} · {row.fuelType.name} · {frequencyNames[row.frequency]}
              </strong>
              <span>
                {row.observationCount > 0
                  ? `${date(row.firstAvailableDate)} — ${date(row.lastAvailableDate)} · ${row.observationCount} наблюдений`
                  : "Архив ещё не содержит наблюдений"}
              </span>
            </li>
          ))}
        </ul>
      )}
    </div>
  );
}
