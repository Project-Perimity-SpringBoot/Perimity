// Boundaries are enforced by lint, not by convention — convention decays.
export default [
  {
    files: ['src/**/*.{ts,tsx}'],
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
];
