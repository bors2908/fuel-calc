export default {
  extends: ["stylelint-config-standard"],
  ignoreFiles: [
    "**/node_modules/**",
    "**/build/**",
    "**/dist/**",
    "**/coverage/**",
    "**/generated/**",
  ],
  rules: {
    "selector-class-pattern": "^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$",
    "custom-property-pattern": "^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$",
    "keyframes-name-pattern": "^[a-z][a-z0-9]*(?:-[a-z0-9]+)*$",
    "selector-max-id": 0,
    "selector-max-specificity": "0,4,2",
    // Disjoint component selectors and responsive overrides need not share DOM ancestry.
    "no-descending-specificity": null,
    "max-nesting-depth": 2,
    "declaration-no-important": true,
  },
};
