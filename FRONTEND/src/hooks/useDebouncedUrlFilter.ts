import { useEffect, useState } from "react";

/**
 * Debounces a filter value before applying it to URL table state to avoid query storms while typing.
 */
export function useDebouncedUrlFilter(
  value: string,
  delayMs = 350,
): string {
  const [debounced, setDebounced] = useState(value);

  useEffect(() => {
    const handle = window.setTimeout(() => setDebounced(value), delayMs);
    return () => window.clearTimeout(handle);
  }, [value, delayMs]);

  return debounced;
}
