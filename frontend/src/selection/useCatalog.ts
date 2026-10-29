import { useCallback, useEffect } from "react";
import { api } from "../api/client";
import { useAsyncRequest } from "../ui/useAsyncRequest";
import type { Catalog } from "./types";

export function useCatalog() {
  const { value, error, run } = useAsyncRequest<Catalog>({ keepPrevious: false });
  const reload = useCallback(
    () =>
      run(async (signal) => {
        const [regions, fuels] = await Promise.all([api.regions(signal), api.fuels(signal)]);
        return { regions: regions.data, fuels: fuels.data, serverDate: regions.meta.serverDate };
      }),
    [run],
  );
  useEffect(() => {
    void reload();
  }, [reload]);
  return { catalog: value, error, reload };
}
