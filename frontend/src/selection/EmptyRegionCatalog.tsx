export function EmptyRegionCatalog({
  className = "empty-state",
  onReload,
}: {
  className?: string;
  onReload?: () => void;
}) {
  return (
    <p
      className={className}
      role="status"
    >
      Цены пока не загружены. Список регионов появится после первой загрузки данных.
      {onReload && (
        <>
          {" "}
          <button
            type="button"
            onClick={onReload}
          >
            Обновить список
          </button>
        </>
      )}
    </p>
  );
}
