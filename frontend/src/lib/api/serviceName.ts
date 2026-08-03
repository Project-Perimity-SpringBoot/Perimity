/**
 * The six service names, in their own module.
 *
 * client.ts imports the mock adapter and the mock adapter needs this type, so
 * declaring it here breaks what would otherwise be a cycle between them. It is
 * a type-only import either way and TypeScript would erase it, but a bundler
 * cycle that only exists on paper is still one somebody has to reason about.
 */
export type ServiceName = 'auth' | 'user' | 'gatepass' | 'campus' | 'guard' | 'qr';
