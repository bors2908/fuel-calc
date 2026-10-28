import js from "@eslint/js";
import { defineConfig, globalIgnores } from "eslint/config";
import prettier from "eslint-config-prettier/flat";
import eslintReact from "@eslint-react/eslint-plugin";
import reactHooks from "eslint-plugin-react-hooks";
import globals from "globals";
import tseslint from "typescript-eslint";

export default defineConfig(
  globalIgnores([
    "**/node_modules/**",
    "**/build/**",
    "**/dist/**",
    "**/coverage/**",
    "**/generated/**",
  ]),
  {
    files: ["**/*.{js,mjs,cjs}"],
    extends: [js.configs.recommended],
    languageOptions: { globals: globals.node },
  },
  {
    files: ["**/*.{ts,tsx}"],
    extends: [
      js.configs.recommended,
      tseslint.configs.recommendedTypeChecked,
      reactHooks.configs.flat.recommended,
    ],
    languageOptions: {
      globals: globals.browser,
      parserOptions: {
        projectService: true,
        tsconfigRootDir: import.meta.dirname,
      },
    },
    rules: {
      "no-unused-vars": "off",
      "@typescript-eslint/no-unused-vars": ["error", { argsIgnorePattern: "^_" }],
      "@typescript-eslint/consistent-type-imports": ["error", { prefer: "type-imports" }],
      "@typescript-eslint/no-non-null-assertion": "error",
      "react-hooks/exhaustive-deps": "error",
      eqeqeq: ["error", "always"],
    },
  },
  {
    files: ["**/*.tsx"],
    plugins: { "@eslint-react": eslintReact },
    rules: {
      "@eslint-react/no-missing-key": "error",
      "@eslint-react/no-duplicate-key": "error",
      "@eslint-react/no-array-index-key": "error",
      "@eslint-react/dom-no-missing-button-type": "error",
      "@eslint-react/dom-no-unknown-property": "error",
      "@eslint-react/dom-no-dangerously-set-innerhtml": "error",
    },
  },
  prettier,
  {
    files: ["**/*.{ts,tsx}"],
    // Requiring every block is compatible with Prettier and avoids misleading control flow.
    rules: { curly: ["error", "all"] },
  },
);
