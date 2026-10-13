import { useQuery } from "@tanstack/react-query";
import { Link2, LoaderCircle } from "lucide-react";
import { Link } from "react-router-dom";
import { apiClient } from "../../lib/apiClient";

interface LinkedRecord {
  recordType: string;
  recordId: string;
  label: string;
  relationship?: string;
}

interface ComplianceLinksResponse {
  anchorType: string;
  anchorId: string;
  alerts: LinkedRecord[];
  cases: LinkedRecord[];
  transactions: LinkedRecord[];
  sars: LinkedRecord[];
  disputes: LinkedRecord[];
  monitoringAlerts: LinkedRecord[];
  riskProfiles: LinkedRecord[];
  screeningActivities: LinkedRecord[];
}

interface Props {
  subjectType: "TRANSACTION" | "MERCHANT" | "ALERT" | "CASE" | "SAR";
  subjectId: string;
}

/** Renders cross-domain compliance links (alerts, cases, SARs) for a transaction or merchant. */
export default function ComplianceLinksPanel({ subjectType, subjectId }: Props) {
  const path = (() => {
    switch (subjectType) {
      case "TRANSACTION":
        return `compliance/links/transaction/${subjectId}`;
      case "MERCHANT":
        return `compliance/links/merchant/${subjectId}`;
      case "ALERT":
        return `compliance/links/alert/${subjectId}`;
      case "CASE":
        return `compliance/links/case/${subjectId}`;
      case "SAR":
        return `compliance/links/sar/${subjectId}`;
      default:
        return "";
    }
  })();

  const query = useQuery<ComplianceLinksResponse>({
    queryKey: ["compliance-links", subjectType, subjectId],
    queryFn: () => apiClient.get<ComplianceLinksResponse>(path),
    enabled: Boolean(subjectId),
  });

  if (query.isLoading) {
    return (
      <p className="flex items-center gap-2 px-4 py-6 text-sm text-white/50">
        <LoaderCircle className="animate-spin" size={16} /> Loading compliance links…
      </p>
    );
  }

  if (query.isError || !query.data) {
    return (
      <p className="px-4 py-6 text-sm text-white/45">
        Compliance links could not be loaded for this {subjectType.toLowerCase()}.
      </p>
    );
  }

  const sections: { title: string; items: LinkedRecord[] }[] = [
    { title: "Linked alerts", items: query.data.alerts ?? [] },
    { title: "Linked cases", items: query.data.cases ?? [] },
    { title: "Linked transactions", items: query.data.transactions ?? [] },
    { title: "Linked SARs", items: query.data.sars ?? [] },
    { title: "Disputes", items: query.data.disputes ?? [] },
    { title: "Monitoring alerts", items: query.data.monitoringAlerts ?? [] },
    { title: "Risk profiles", items: query.data.riskProfiles ?? [] },
    { title: "Screening activity", items: query.data.screeningActivities ?? [] },
  ];

  const total = sections.reduce((sum, s) => sum + s.items.length, 0);

  return (
    <section>
      <h2 className="mb-3 flex items-center gap-2 text-sm font-semibold">
        <Link2 size={16} className="text-gold" /> Compliance links
      </h2>
      {!total ? (
        <p className="border border-white/10 px-4 py-8 text-center text-sm text-white/45">
          No linked alerts, cases, or regulatory records yet.
        </p>
      ) : (
        <div className="space-y-4 border border-white/10">
          {sections.map(
            (section) =>
              section.items.length > 0 && (
                <div key={section.title} className="border-b border-white/8 last:border-b-0">
                  <p className="bg-white/[0.03] px-4 py-2 text-xs font-semibold uppercase tracking-wide text-white/40">
                    {section.title}
                  </p>
                  <ul className="divide-y divide-white/8">
                    {section.items.map((item) => (
                      <li key={`${item.recordType}-${item.recordId}`}>
                        <Link
                          to={`/records/${item.recordType}/${item.recordId}`}
                          className="block px-4 py-3 text-sm text-white/80 transition hover:bg-white/5 hover:text-gold"
                        >
                          {item.label}
                          <span className="mt-0.5 block text-[11px] uppercase text-white/35">
                            {item.relationship?.replace(/_/g, " ") ?? item.recordType.replace(/_/g, " ")}
                          </span>
                        </Link>
                      </li>
                    ))}
                  </ul>
                </div>
              ),
          )}
        </div>
      )}
    </section>
  );
}
