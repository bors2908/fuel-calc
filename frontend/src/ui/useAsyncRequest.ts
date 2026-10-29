import { useCallback, useState } from "react";
import { errorMessage } from "../api/client";
import { useRequestGate } from "./useRequestGate";

type RequestState<T> =
  | { status: "idle"; value: null; error: null }
  | { status: "loading"; value: T | null; error: null }
  | { status: "ready"; value: T; error: null }
  | { status: "error"; value: T | null; error: string };
export function useAsyncRequest<T>({ keepPrevious }: { keepPrevious: boolean }) {
  const [state, setState] = useState<RequestState<T>>({ status: "idle", value: null, error: null });
  const { begin, cancel: abort } = useRequestGate();
  const run = useCallback(
    async (request: (signal: AbortSignal) => Promise<T>) => {
      const task = begin();
      setState((previous) => ({
        status: "loading",
        value: keepPrevious ? previous.value : null,
        error: null,
      }));
      try {
        const value = await request(task.signal);
        if (task.isCurrent()) {
          setState({ status: "ready", value, error: null });
        }
      } catch (error) {
        if (task.isCurrent()) {
          setState((previous) => ({
            status: "error",
            value: previous.value,
            error: errorMessage(error),
          }));
        }
      }
    },
    [begin, keepPrevious],
  );
  const cancel = useCallback(() => {
    abort();
    setState((previous) =>
      previous.value === null
        ? { status: "idle", value: null, error: null }
        : { status: "ready", value: previous.value, error: null },
    );
  }, [abort]);
  const clear = useCallback(() => {
    abort();
    setState({ status: "idle", value: null, error: null });
  }, [abort]);
  return { ...state, busy: state.status === "loading", run, cancel, clear };
}
