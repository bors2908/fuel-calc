import type { Selection } from "../selection/types";
import { selectionHref } from "../selection/model";
import { TractorLogo } from "./TractorLogo";

type Props = { page: "prices" | "refueling"; selection?: Selection; showComparison?: boolean };
export function SiteHeader({ page, selection, showComparison = false }: Props) {
  const pricesHref = selection ? selectionHref(selection, "/") : `/${location.search}`;
  const refuelingHref = selection
    ? selectionHref(selection, "/refueling")
    : `/refueling${location.search}`;
  return (
    <header className="site-header wrap">
      <a
        className="brand"
        href="/"
      >
        <span
          className="brand-mark"
          aria-hidden="true"
        >
          <TractorLogo />
        </span>
        <span>
          Топливо для хозяйства<small>Региональные цены. Понятные расходы.</small>
        </span>
      </a>
      <nav aria-label="Разделы сервиса">
        {page === "refueling" ? (
          <a href={pricesHref}>Цены и динамика</a>
        ) : (
          <>
            <a href="#trend">Динамика</a>
            {showComparison && <a href="#comparison">Сравнение</a>}
          </>
        )}
        <a
          href={refuelingHref}
          aria-current={page === "refueling" ? "page" : undefined}
        >
          Заправка
        </a>
      </nav>
    </header>
  );
}
