import { useEffect, useState } from "react";
import { api, errorMessage } from "../api/client";
import type { Catalog } from "../selection/model";
import { SiteHeader } from "./SiteHeader";
import { PricesPage } from "../prices/PricesPage";
import { RefuelingPage } from "../refueling/RefuelingPage";
import { Feedback, Loading } from "../ui/Feedback";
import "./app.css";

export function App() {
  const refueling = location.pathname.replace(/\/$/, "") === "/refueling";
  const [catalog, setCatalog] = useState<Catalog | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [attempt, setAttempt] = useState(0);
  useEffect(() => {
    document.title = refueling
      ? "Заправка · Топливо для хозяйства"
      : "Цены · Топливо для хозяйства";
  }, [refueling]);
  useEffect(() => {
    const controller = new AbortController();
    let current = true;
    void Promise.all([api.regions(controller.signal), api.fuels(controller.signal)])
      .then(([regions, fuels]) => {
        if (current) {
          setCatalog({
            regions: regions.data,
            fuels: fuels.data,
            serverDate: regions.meta.serverDate,
          });
        }
      })
      .catch((failure: unknown) => {
        if (current) {
          setError(errorMessage(failure));
        }
      });
    return () => {
      current = false;
      controller.abort();
    };
  }, [attempt]);
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
                    setError(null);
                    setAttempt(attempt + 1);
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
