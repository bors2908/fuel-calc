import process from "node:process";
import ts from "typescript";

const host = {
  getCanonicalFileName: (file) => file,
  getCurrentDirectory: ts.sys.getCurrentDirectory,
  getNewLine: () => ts.sys.newLine,
};
const config = ts.readConfigFile("tsconfig.json", ts.sys.readFile);
const parsed = ts.parseJsonConfigFileContent(config.config ?? {}, ts.sys, process.cwd());
const configErrors = [config.error, ...parsed.errors].filter(
  (error) => error && error.code !== 18003,
);

if (configErrors.length > 0) {
  process.stderr.write(ts.formatDiagnosticsWithColorAndContext(configErrors, host));
  process.exitCode = 1;
} else if (parsed.fileNames.length === 0) {
  console.log("TypeScript: skipped — no TS/TSX source files yet.");
} else {
  const program = ts.createProgram(parsed.fileNames, parsed.options);
  const diagnostics = ts.getPreEmitDiagnostics(program);

  if (diagnostics.length > 0) {
    process.stderr.write(ts.formatDiagnosticsWithColorAndContext(diagnostics, host));
    process.exitCode = 1;
  } else {
    console.log(`TypeScript: checked ${parsed.fileNames.length} source file(s).`);
  }
}
