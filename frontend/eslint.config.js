// Boundaries are enforced by lint, not by convention — convention decays.
import tseslint from 'typescript-eslint';

export default [
  {
    files: ['src/**/*.{ts,tsx}'],

    // Required, not optional. ESLint's default parser cannot read TypeScript or
    // JSX, so without this every file in src/ fails to parse before a single
    // rule runs. This config declared .tsx from the start but never set a
    // parser, which is one reason `npm run lint` had never worked.
    languageOptions: {
      parser: tseslint.parser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        ecmaFeatures: { jsx: true },
      },
    },

    // Deliberately NOT typescript-eslint's recommended set. tsc already runs in
    // strict mode; these are the rules tsc cannot express.
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [
          {
            group: ['@/types/wire', '**/types/wire'],
            message: 'Wire envelopes are restricted to src/lib/api. Use Paged<T> from @/types/api.',
          },
          {
            group: ['@features/*/**'],
            message: 'Features must not import from other features.',
          },
        ],
      }],

      'no-restricted-syntax': ['error', {
        // Server timestamps are zone-less LocalDateTime.
        // :not(BinaryExpression) exempts epoch arithmetic - new Date(exp * 1000)
        // on a JWT claim is correct and was being flagged.
        selector:
          "NewExpression[callee.name='Date'][arguments.length=1]:not([arguments.0.type='BinaryExpression'])",
        message: 'Use parseServerDateTime/parseServerDate — server times carry no zone.',
      }, {
        // Replaces react/no-danger without the plugin. eslint-plugin-react
        // supports ESLint 9.7 at the latest; pulling it in for one rule would
        // pin the whole toolchain backwards.
        selector: "JSXAttribute[name.name='dangerouslySetInnerHTML']",
        message: 'dangerouslySetInnerHTML is not permitted. Render text as children.',
      }, {
        // ==================================================================
        // NO HAND-WRITTEN API PATHS. This is the rule that stops the endpoint
        // problem from recurring.
        // ==================================================================
        // The build plan's "Backend verification" tables were wrong in every
        // row - wrong prefixes, and in three cases the wrong service entirely.
        // They were written before the controllers existed and never
        // reconciled. Anyone typing a URL from that document gets a 404 that
        // looks like a missing backend rather than a stale doc.
        //
        // The fix is not a better table. A table copied out of the controllers
        // drifts the moment someone renames a mapping - which is exactly how
        // this happened. src/lib/api/services/ cannot drift, because it is the
        // thing being called.
        //
        // So: a path literal outside the API layer is an error. Add the
        // function there instead and import it.
        selector: "Literal[value=/^\\/api\\//]",
        message:
          'Do not hand-write API paths. Import from src/lib/api/services/ — those are the '
          + 'only correct, current calls. If the function you need is missing, add it there.',
      }, {
        // Same rule for `/api/...` inside a template literal.
        selector: "TemplateElement[value.raw=/^\\/api\\//]",
        message:
          'Do not hand-write API paths. Import from src/lib/api/services/ — those are the '
          + 'only correct, current calls. If the function you need is missing, add it there.',
      }],
    },
  },

  // The API layer is the one place allowed to know both the wire shape and the
  // paths. That is its entire job, and the restrictions above exist to protect
  // it rather than to constrain it.
  {
    files: ['src/lib/api/**/*.ts'],
    rules: {
      'no-restricted-imports': ['error', {
        patterns: [
          {
            group: ['@features/*/**'],
            message: 'The API layer must not import from features.',
          },
        ],
      }],
      'no-restricted-syntax': 'off',
    },
  },
];
