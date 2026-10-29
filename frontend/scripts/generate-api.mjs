import { readFile, mkdir, writeFile } from "node:fs/promises";
import openapiTS, { astToString } from "openapi-typescript";
import YAML from "yaml";

const contract = new URL("../../openapi.yml", import.meta.url);
const output = new URL("../src/api/generated/", import.meta.url);
const spec = YAML.parse(await readFile(contract, "utf8"));
const types = astToString(await openapiTS(contract, { emptyObjectsUnknown: true }));
// Ajv understands OpenAPI nullable. Only references and documentation annotations change.
const schemas = JSON.stringify(spec.components.schemas, (key, value) => {
  if (["description", "example", "examples"].includes(key)) {
    return undefined;
  }
  return key === "$ref" && typeof value === "string"
    ? value.replace("#/components/schemas/", "#/definitions/")
    : value;
});
const files = {
  "contract.ts": types,
  "schemas.json": JSON.stringify({ definitions: JSON.parse(schemas) }, null, 2) + "\n",
};
await mkdir(output, { recursive: true });
for (const [name, contents] of Object.entries(files)) {
  const file = new URL(name, output);
  if (process.argv.includes("--check")) {
    const existing = await readFile(file, "utf8").catch(() => "");
    if (existing !== contents) {
      throw new Error(`API contract changed: run npm run api:generate (${name}).`);
    }
  } else {
    await writeFile(file, contents);
  }
}
console.log("OpenAPI types and runtime schemas are up to date.");
