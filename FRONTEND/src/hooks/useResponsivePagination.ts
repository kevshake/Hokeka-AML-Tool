import { useState, useEffect } from 'react';

/**
 * Hook that returns optimal rows-per-page based on viewport height.
 *
 * Calculates how many table rows fit on screen without scrolling by
 * accounting for header, pagination bar, and a configurable buffer.
 * Recalculates on window resize.
 *
 * @param rowHeight  approximate height of a single table row in px (default 52)
 * @param buffer     extra px to reserve for headers/bars/padding (default 380)
 * @returns          [rowsPerPage, rowsPerPageOptions, dynamicRowsPerPage]
 */
export function useResponsivePagination(rowHeight = 52, buffer = 380) {
  const [dynamicRows, setDynamicRows] = useState(25);

  useEffect(() => {
    const calc = () => {
      const vh = window.innerHeight;
      const available = vh - buffer;
      const raw = Math.floor(available / rowHeight);
      // Clamp between reasonable bounds
      const clamped = Math.min(Math.max(raw, 5), 100);
      setDynamicRows(clamped);
    };

    calc(); // initial
    window.addEventListener('resize', calc);
    return () => window.removeEventListener('resize', calc);
  }, [rowHeight, buffer]);

  // Provide sensible page-size options and the dynamic default
  const options = [5, 10, 15, 20, 25, 30, 40, 50, 100];

  return [dynamicRows, options, dynamicRows] as const;
}