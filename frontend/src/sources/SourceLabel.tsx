import { sourceInfo } from "./model";
import type { Source } from "./model";

export function SourceLabel({ source }: { source: Source }) {
  return <span className="source-label">{sourceInfo[source].name}</span>;
}

import "./source-label.css";
