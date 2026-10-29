import { useEffect } from "react";
import { useCatalog } from "../selection/useCatalog";
import { SiteHeader } from "./SiteHeader";
import { PricesPage } from "../prices/PricesPage";
import { RefuelingPage } from "../refueling/RefuelingPage";
import { Feedback, Loading } from "../ui/Feedback";
import "./app.css";

export function App() {
  const refueling = location.pathname.replace(/\/$/, "") === "/refueling";
  const { catalog, error, reload } = useCatalog();
  useEffect(() => {
    document.title = refueling
      ? "Заправка · Топливо для хозяйства"
      : "Цены · Топливо для хозяйства";
  }, [refueling]);
  return (
    <>
      <a
        className="skip-link"
        href="#main"
      >
        К содержимому
      </a>
      {catalog ? (
        refueling ? (
          <RefuelingPage catalog={catalog} />
        ) : (
          <PricesPage catalog={catalog} />
        )
      ) : (
        <>
          <SiteHeader page={refueling ? "refueling" : "prices"} />
          <main
            id="main"
            className="wrap"
          >
            <section className="hero">
              <p className="eyebrow">Для тех, кто планирует сезон</p>
              <h1>{refueling ? "Рассчитайте заправку" : "Топливо. С понятной ценой."}</h1>
              {error ? (
                <Feedback
                  onRetry={() => {
                    void reload();
                  }}
                >
                  {error}
                </Feedback>
              ) : (
                <Loading>Загружаем список регионов и топлива…</Loading>
              )}
            </section>
          </main>
        </>
      )}
      <footer className="site-footer wrap">
        <span>Топливо для хозяйства</span>
        <span>Региональные средние · открытые источники</span>
      </footer>
    </>
  );
}
