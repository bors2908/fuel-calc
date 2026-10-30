import { emptyList, parseSavedList } from "./model";
import type { SavedList } from "./model";

export const savedStorageKey = "fuel-calc:saved-refuelings";
export type SavedState = { list: SavedList; warning: string | null };
export function readSaved(): SavedState {
  try {
    const raw = localStorage.getItem(savedStorageKey);
    return {
      list: raw === null ? emptyList() : parseSavedList(JSON.parse(raw) as unknown),
      warning: null,
    };
  } catch {
    return {
      list: emptyList(),
      warning:
        "Не удалось прочитать сохранённый список. Новые записи доступны только до закрытия страницы. Очистите список, чтобы восстановить сохранение.",
    };
  }
}
export function writeSaved(list: SavedList, warning: string | null): SavedState {
  if (warning) {
    return { list, warning };
  }
  try {
    localStorage.setItem(savedStorageKey, JSON.stringify(list));
    return { list, warning: null };
  } catch {
    return {
      list,
      warning:
        "Сохранение в браузере недоступно. Список работает до закрытия страницы; скачайте CSV, чтобы сохранить записи.",
    };
  }
}
export function clearSaved(): SavedState {
  try {
    localStorage.removeItem(savedStorageKey);
    return { list: emptyList(), warning: null };
  } catch {
    return {
      list: emptyList(),
      warning:
        "Хранилище браузера недоступно. Список очищен только на этой странице; старые записи могут появиться при следующем открытии.",
    };
  }
}
