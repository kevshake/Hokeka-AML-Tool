import { useRef, type ReactNode } from "react";
import { useVirtualizer } from "@tanstack/react-virtual";

interface Props {
  rowCount: number;
  estimateSize?: number;
  maxHeight?: number;
  renderRow: (index: number) => ReactNode;
}

/**
 * Virtualizes long table bodies so only visible rows mount DOM nodes during scroll.
 */
export default function VirtualizedTableBody({
  rowCount,
  estimateSize = 44,
  maxHeight = 520,
  renderRow,
}: Props) {
  const parentRef = useRef<HTMLDivElement>(null);

  const virtualizer = useVirtualizer({
    count: rowCount,
    getScrollElement: () => parentRef.current,
    estimateSize: () => estimateSize,
    overscan: 8,
  });

  return (
    <div ref={parentRef} style={{ maxHeight, overflow: "auto" }}>
      <div style={{ height: virtualizer.getTotalSize(), position: "relative" }}>
        {virtualizer.getVirtualItems().map((item) => (
          <div
            key={item.key}
            style={{
              position: "absolute",
              top: 0,
              left: 0,
              width: "100%",
              transform: `translateY(${item.start}px)`,
            }}
          >
            {renderRow(item.index)}
          </div>
        ))}
      </div>
    </div>
  );
}
