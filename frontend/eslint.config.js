// Boundaries are enforced by lint, not by convention — convention decays.
//
// The rules below are unchanged. What was added is the machinery they need to
// run at all: until now `npm run lint` failed for everyone, because eslint was
// not a dependency, nothing gave the flat config a TypeScript parser, and
// `react/no-danger` named a plugin that was never registered. A boundary rule
// that cannot execute is a comment.
//
// eslint is pinned to ^9 deliberately. eslint-plugin-react does not support
// eslint 10 yet, and --legacy-peer-deps would install a combination nobody
// tested rather than the one that works.
import tseslint from 'typescript-eslint';
import react from 'eslint-plugin-react';

export default [
  {
    ignores: ['dist/**', 'node_modules/**', 'coverage/**'],
  },
  {
    files: ['src/**/*.{ts,tsx}'],
    languageOptions: {
      // Type-aware linting is NOT switched on. These four rules are syntactic,
      // the type errors are already caught by `tsc -b` in the build, and a
      // project-service parse would make lint minutes long for no extra catch.
      parser: tseslint.parser,
      parserOptions: {
        ecmaVersion: 'latest',
        sourceType: 'module',
        ecmaFeatures: { jsx: true },
      },
    },
    plugins: { react },
    rules: {
      // The envelope must not escape the API layer.
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
      // Server timestamps are zone-less LocalDateTime; parse via parseServerDateTime.
      'no-restricted-syntax': ['error', {
        selector: "NewExpression[callee.name='Date'][arguments.length=1]",
        message: 'Use parseServerDateTime/parseServerDate — server times carry no zone.',
      }],
      'react/no-danger': 'error',
    },
  },
  {
    // src/lib/api IS the API layer, so the envelope restriction cannot apply to
    // it — normalize.ts exists precisely to unwrap those envelopes.
    files: ['src/lib/api/**/*.{ts,tsx}'],
    rules: { 'no-restricted-imports': 'off' },
  },
];
