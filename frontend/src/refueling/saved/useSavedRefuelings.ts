import { useEffect, useRef, useState } from "react";
import type { Schema } from "../../api/types";
import { emptyList, parseSavedList, savedLimit, snapshot } from "./model";
import { clearSaved, readSaved, savedStorageKey, writeSaved } from "./storage";
import type { SavedState } from "./storage";

export function useSavedRefuelings() {
  const [state, setState] = useState(readSaved);
  const current = useRef(state);
  const [lastAdded, setLastAdded] = useState<Schema<"RefuelingCalculation"> | null>(null);
  const [error, setError] = useState<string | null>(null);
  function publish(next: SavedState) {
    current.current = next;
    setState(next);
    setError(null);
  }
  useEffect(() => {
    function restore(event: StorageEvent) {
      if (event.key === savedStorageKey || event.key === null) {
        publish(readSaved());
      }
    }
    window.addEventListener("storage", restore);
    return () => window.removeEventListener("storage", restore);
  }, []);
  function add(result: Schema<"RefuelingCalculation">) {
    try {
      const { list, warning } = current.current;
      if (
        list.entries.length >= savedLimit ||
        (list.region && list.region.okatoCode !== result.context.baseRegion.okatoCode)
      ) {
        throw new Error("План заполнен или закреплён за другим регионом.");
      }
      const entry = snapshot(result);
      const next = parseSavedList({
        version: 1,
        region: list.region ?? { ...result.context.baseRegion },
        entries: [...list.entries, entry],
      });
      publish(writeSaved(next, warning));
      setLastAdded(result);
    } catch (error) {
      setError(error instanceof Error ? error.message : "Не удалось добавить заправку.");
    }
  }
  function remove(id: string) {
    const { list, warning } = current.current;
    const entries = list.entries.filter((entry) => entry.id !== id);
    publish(writeSaved(entries.length ? { ...list, entries } : emptyList(), warning));
    setLastAdded(null);
  }
  function clear() {
    publish(clearSaved());
    setLastAdded(null);
  }
  return { ...state, error, lastAdded, add, remove, clear };
}
