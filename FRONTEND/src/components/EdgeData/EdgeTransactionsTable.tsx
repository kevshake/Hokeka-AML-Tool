import { Link } from "react-router-dom";
import TwBadge from "../Common/TwBadge";
import type { Transaction } from "../../types";
import { formatMinorAmount, transactionLinkId } from "../../lib/edgeFormat";

interface Props {
  items: Partial<Transaction>[];
  emptyText?: string;
  /** Extra column rendered per row (e.g. the link type of a related transaction). */
  extra?: { header: string; render: (item: Partial<Transaction>, index: number) => React.ReactNode };
}

/** Badge colour of a decision. */
function decisionVariant(decision: string | undefined): "danger" | "warning" | "success" | "default" {
  if (decision === "BLOCK") return "danger";
  if (decision === "HOLD" || decision === "ALERT") return "warning";
  if (decision === "ALLOW") return "success";
  return "default";
}

/** Local date-time of an evaluation. */
function when(value: string | undefined): string {
  if (!value) return "-";
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? value : date.toLocaleString();
}

/**
 * Table of transactions pulled live from the Edge (or legacy cloud rows, flagged as such). Used by
 * the monitoring list and every workflow view that shows transaction details.
 */
export default function EdgeTransactionsTable({ items, emptyText = "No transactions found", extra }: Props) {
  return (
    <div className="hokeka-table-wrap">
      <table className="hokeka-table">
        <thead>
          <tr>
            <th>Reference</th>
            <th>Merchant</th>
            <th>Amount</th>
            <th>Decision</th>
            <th>Score</th>
            <th>Evaluated</th>
            <th>Site</th>
            {extra && <th>{extra.header}</th>}
          </tr>
        </thead>
        <tbody>
          {items.length === 0 ? (
            <tr>
              <td colSpan={extra ? 8 : 7} className="px-4 py-8 text-center text-sm text-glass-muted">{emptyText}</td>
            </tr>
          ) : (
            items.map((item, index) => {
              const id = transactionLinkId(item);
              return (
                <tr key={`${id ?? "row"}-${index}`}>
                  <td className="font-mono text-xs">
                    {id ? (
                      <Link className="text-gold hover:underline" to={`/records/TRANSACTION/${id}`}>
                        {item.txnRef || id}
                      </Link>
                    ) : (
                      item.txnRef || "-"
                    )}
                    {item.source === "CLOUD_LEGACY" && (
                      <span className="ml-2 rounded border border-hairline px-1 text-[10px] uppercase text-glass-muted">legacy</span>
                    )}
                  </td>
                  <td>
                    {item.merchantId ? (
                      <Link className="text-gold hover:underline" to={`/records/MERCHANT/${item.merchantId}`}>{item.merchantId}</Link>
                    ) : (
                      "-"
                    )}
                  </td>
                  <td className="tabular-nums">{formatMinorAmount(item.amountCents ?? item.amountMinor, item.currency)}</td>
                  <td><TwBadge variant={decisionVariant(item.decision)}>{item.decision || "-"}</TwBadge></td>
                  <td className="tabular-nums">{item.score ?? "-"}</td>
                  <td className="whitespace-nowrap text-xs text-glass-muted">{when(item.txnTs ?? item.evaluatedAt)}</td>
                  <td className="font-mono text-xs text-glass-muted">{item.siteId || "-"}</td>
                  {extra && <td>{extra.render(item, index)}</td>}
                </tr>
              );
            })
          )}
        </tbody>
      </table>
    </div>
  );
}
