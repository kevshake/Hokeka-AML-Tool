import type { ReactNode } from "react";
import VirtualizedTableBody from "./VirtualizedTableBody";

interface Props {
  rowCount: number;
  maxHeight?: number;
  loading?: boolean;
  emptyMessage?: string;
  renderRow: (index: number) => ReactNode;
  children: ReactNode;
}

/**
 * Wraps a standard table head with a virtualized body for long Console lists.
 */
export default function HokekaVirtualizedTable({
  rowCount,
  maxHeight = 520,
  loading,
  emptyMessage = "No records found.",
  renderRow,
  children,
}: Props) {
  if (loading) {
    return <div className="flex items-center justify-center py-16 text-sm text-glass-muted">Loading…</div>;
  }
  if (rowCount === 0) {
    return <div className="px-4 py-10 text-center text-sm text-glass-muted">{emptyMessage}</div>;
  }
  return (
    <table className="hokeka-table">
      {children}
      <tbody>
        <tr>
          <td colSpan={99} className="p-0">
            <VirtualizedTableBody rowCount={rowCount} maxHeight={maxHeight} renderRow={renderRow} />
          </td>
        </tr>
      </tbody>
    </table>
  );
}
