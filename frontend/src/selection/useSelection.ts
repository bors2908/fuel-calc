import { useEffect, useState } from "react";
import type { Selection } from "../api/types";
import { readSelection, selectionError, writeSelection } from "./model";
import type { Catalog, DateMode } from "./model";

export function useSelection(
  catalog: Catalog,
  onApply: (selection: Selection) => void | Promise<void>,
  onClear: () => void,
  onCancel: () => void,
  dateMode: DateMode = "range",
) {
  const [initial] = useState(() => readSelection(catalog, dateMode));
  const [selection, setSelection] = useState(initial.selection);
  const [applied, setApplied] = useState<Selection | null>(
    initial.requested && !initial.error ? initial.selection : null,
  );
  const [error, setError] = useState(initial.error);
  useEffect(() => {
    if (initial.requested && !initial.error) {
      void onApply(initial.selection);
    }
    function restore() {
      const restored = readSelection(catalog, dateMode);
      setSelection(restored.selection);
      setError(restored.error);
      setApplied(restored.requested && !restored.error ? restored.selection : null);
      if (restored.requested && !restored.error) {
        void onApply(restored.selection);
      } else {
        onClear();
      }
    }
    function resume(event: PageTransitionEvent) {
      if (event.persisted) {
        restore();
      }
    }
    window.addEventListener("popstate", restore);
    window.addEventListener("pageshow", resume);
    return () => {
      window.removeEventListener("popstate", restore);
      window.removeEventListener("pageshow", resume);
    };
  }, [initial, catalog, onApply, onClear, dateMode]);
  function change(value: Selection) {
    setSelection(value);
    const validation = selectionError(value, catalog, dateMode);
    setError(value.baseRegionCode ? validation : null);
    if (validation) {
      onCancel();
      return;
    }
    setApplied(value);
    writeSelection(value);
    void onApply(value);
  }
  return {
    selection,
    applied,
    error,
    valid: selectionError(selection, catalog, dateMode) === null && !error,
    change,
  };
}
