import { useEffect, useState } from "react";
import { selectionHref } from "./model";
import type { Catalog, Selection, SelectionModel } from "./types";

type Actions<S> = {
  onApply?: (value: S) => void | Promise<void>;
  onInvalidate: () => void;
  onClear?: () => void;
};
export function useSelection<S extends Selection>(
  catalog: Catalog,
  model: SelectionModel<S>,
  { onApply, onInvalidate, onClear = onInvalidate }: Actions<S>,
) {
  const [initial] = useState(() => model.parse(new URLSearchParams(location.search), catalog));
  const [selection, setSelection] = useState(initial.selection);
  const [applied, setApplied] = useState<S | null>(
    initial.requested && !initial.error ? initial.selection : null,
  );
  const [error, setError] = useState(initial.error);
  useEffect(() => {
    if (initial.requested && !initial.error) {
      void onApply?.(initial.selection);
    }
    function restore() {
      const restored = model.parse(new URLSearchParams(location.search), catalog);
      setSelection(restored.selection);
      setError(restored.error);
      setApplied(restored.requested && !restored.error ? restored.selection : null);
      onInvalidate();
      if (restored.requested && !restored.error) {
        void onApply?.(restored.selection);
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
  }, [initial, catalog, model, onApply, onClear, onInvalidate]);
  function change(value: S) {
    const validation = model.validate(value, catalog);
    setSelection(value);
    setError(value.baseRegionCode ? validation : null);
    onInvalidate();
    if (!validation) {
      setApplied(value);
      history.pushState(null, "", `${selectionHref(value, location.pathname)}${location.hash}`);
      void onApply?.(value);
    }
  }
  return {
    selection,
    applied,
    error,
    valid: model.validate(selection, catalog) === null && !error,
    change,
  };
}
