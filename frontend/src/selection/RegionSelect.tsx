import { useEffect, useId, useRef, useState } from "react";
import { filterRegions } from "./regionSearch";
import type { Catalog } from "./types";

type Props = {
  regions: Catalog["regions"];
  value: string;
  onChange: (code: string) => void;
};

export function RegionSelect({ regions, value, onChange }: Props) {
  const id = useId();
  const list = useRef<HTMLUListElement>(null);
  const [open, setOpen] = useState(false);
  const [search, setSearch] = useState("");
  const [activeCode, setActiveCode] = useState("");
  const selected = regions.find((region) => region.okatoCode === value);
  const matches = filterRegions(regions, search);
  const active = matches.find((region) => region.okatoCode === activeCode);

  useEffect(() => {
    if (open && activeCode) {
      list.current?.querySelector('[data-active="true"]')?.scrollIntoView({ block: "nearest" });
    }
  }, [open, activeCode]);

  function expand() {
    if (!open) {
      setSearch("");
      setActiveCode(value);
      setOpen(true);
    }
  }

  function select(code: string) {
    onChange(code);
    setOpen(false);
  }

  return (
    <div className="region-combobox">
      <label htmlFor={id}>Мой регион</label>
      <input
        id={id}
        className="select-input"
        type="text"
        role="combobox"
        aria-autocomplete="list"
        aria-expanded={open}
        aria-controls={open ? `${id}-list` : undefined}
        aria-activedescendant={open && active ? `${id}-${active.okatoCode}` : undefined}
        aria-required="true"
        autoComplete="off"
        placeholder={open ? "Введите название региона" : "Выберите регион"}
        value={open ? search : selected ? selected.name : ""}
        onFocus={expand}
        onClick={expand}
        onBlur={() => setOpen(false)}
        onChange={(event) => {
          setSearch(event.target.value);
          setActiveCode("");
          setOpen(true);
        }}
        onKeyDown={(event) => {
          if (event.nativeEvent.isComposing) {
            return;
          }
          if (event.key === "ArrowDown" || event.key === "ArrowUp") {
            event.preventDefault();
            if (!open) {
              expand();
              return;
            }
            const index = matches.findIndex((region) => region.okatoCode === activeCode);
            const next =
              event.key === "ArrowDown"
                ? Math.min(index + 1, matches.length - 1)
                : index < 0
                  ? matches.length - 1
                  : Math.max(0, index - 1);
            setActiveCode(matches[next]?.okatoCode ?? "");
          } else if (event.key === "Enter" && open) {
            event.preventDefault();
            const choice = active ?? (matches.length === 1 ? matches[0] : undefined);
            if (choice) {
              select(choice.okatoCode);
            }
          } else if (event.key === "Escape" && open) {
            event.preventDefault();
            setOpen(false);
          }
        }}
      />
      {open && (
        <div className="region-popup">
          <ul
            id={`${id}-list`}
            ref={list}
            role="listbox"
            aria-label="Регионы"
          >
            {matches.map((region) => (
              <li
                id={`${id}-${region.okatoCode}`}
                key={region.okatoCode}
                role="option"
                aria-selected={region.okatoCode === value}
                data-active={region.okatoCode === activeCode}
                onPointerDown={(event) => event.preventDefault()}
                onClick={() => select(region.okatoCode)}
              >
                <span>{region.name}</span>
                {region.okatoCode === value && <span aria-hidden="true">✓</span>}
              </li>
            ))}
          </ul>
          {matches.length === 0 && (
            <p role="status">Регион не найден. Попробуйте другое название.</p>
          )}
        </div>
      )}
    </div>
  );
}
