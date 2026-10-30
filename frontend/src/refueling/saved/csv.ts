import type { SavedList, SavedQuote } from "./model";
import { savedTotals } from "./model";

function cell(value: string): string {
  let start = 0;
  while (
    start < value.length &&
    (value.charCodeAt(start) <= 32 || value.charAt(start).trim() === "")
  ) {
    start++;
  }
  const text = /^[=+\-@＝＋－＠]/u.test(value.slice(start)) ? `'${value}` : value;
  return `"${text.replaceAll('"', '""')}"`;
}
function quoteCells(quote: SavedQuote | null): string[] {
  return quote
    ? [
        quote.price.priceRubPerLiter.replace(".", ","),
        quote.price.period.referenceDate,
        quote.price.forecast ? "Прогноз" : "Наблюдение",
        quote.totalRub.replace(".", ","),
      ]
    : ["", "", "Нет данных", ""];
}
export function savedCsv(list: SavedList): string {
  const rows = [
    [
      "№",
      "Регион",
      "ОКАТО",
      "Дата заправки",
      "Топливо",
      "Объём, л",
      "Официальная цена, ₽/л",
      "Дата официальной цены",
      "Официальная оценка",
      "Стоимость по официальной цене, ₽",
      "Рыночная цена, ₽/л",
      "Дата рыночной цены",
      "Рыночная оценка",
      "Стоимость по рыночной цене, ₽",
      "Рассчитано",
      "Добавлено",
    ],
  ];
  for (const [index, entry] of list.entries.entries()) {
    rows.push([
      String(index + 1),
      list.region?.name ?? "",
      list.region?.okatoCode ?? "",
      entry.requestedDate,
      entry.fuel.name,
      entry.volumeLiters.replace(".", ","),
      ...quoteCells(entry.quotes.OFFICIAL),
      ...quoteCells(entry.quotes.MARKET),
      entry.calculatedAt,
      entry.savedAt,
    ]);
  }
  const totals = savedTotals(list.entries);
  const status = (count: number) =>
    `${count === list.entries.length ? "Полный" : "Частичный"} итог: ${count}/${list.entries.length}`;
  rows.push([
    "Итого",
    list.region?.name ?? "",
    list.region?.okatoCode ?? "",
    "",
    "",
    totals.volumeLiters.replace(".", ","),
    "",
    "",
    status(totals.OFFICIAL.count),
    totals.OFFICIAL.amount?.replace(".", ",") ?? "",
    "",
    "",
    status(totals.MARKET.count),
    totals.MARKET.amount?.replace(".", ",") ?? "",
    "",
    "",
  ]);
  return `\uFEFF${rows.map((row) => row.map(cell).join(";")).join("\r\n")}\r\n`;
}
export function downloadSavedCsv(list: SavedList) {
  const url = URL.createObjectURL(new Blob([savedCsv(list)], { type: "text/csv;charset=utf-8" }));
  const link = document.createElement("a");
  link.href = url;
  link.download = `zapravki-${list.region?.okatoCode ?? "region"}.csv`;
  link.click();
  setTimeout(() => URL.revokeObjectURL(url), 0);
}
