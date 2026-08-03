import { useEffect, useState } from 'react';

/**
 * There is no server-side text search anywhere in the backend, so filtering is
 * client-side and every keystroke re-renders a potentially large table.
 */
export function useDebouncedValue<T>(value: T, delay = 300): T {
  const [debounced, setDebounced] = useState(value);
  useEffect(() => {
    const id = setTimeout(() => setDebounced(value), delay);
    return () => clearTimeout(id);
  }, [value, delay]);
  return debounced;
}
