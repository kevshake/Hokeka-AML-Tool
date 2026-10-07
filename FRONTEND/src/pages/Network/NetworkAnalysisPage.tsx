import HokekaPageShell from '../../components/Layout/HokekaPageShell'
import CasesNetworkGraph from '../Cases/CasesNetworkGraph'

/** Top-level network analysis — case graph backed by `/api/v1/cases/{id}/network`. */
export default function NetworkAnalysisPage() {
  return (
    <HokekaPageShell
      title="Network analysis"
      subtitle="Case-linked entity graph — transactions, merchants, SARs, and related cases"
      noCard
    >
      <CasesNetworkGraph />
    </HokekaPageShell>
  )
}
