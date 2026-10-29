import { useCallback, useEffect, useRef } from "react";

// Abort saves work; identity also protects against a response already being resolved.
export function useRequestGate() {
  const active = useRef<AbortController | null>(null);
  const cancel = useCallback(() => {
    active.current?.abort();
    active.current = null;
  }, []);
  const begin = useCallback(() => {
    active.current?.abort();
    const controller = new AbortController();
    active.current = controller;
    return { signal: controller.signal, isCurrent: () => active.current === controller };
  }, []);
  useEffect(() => cancel, [cancel]);
  return { begin, cancel };
}
