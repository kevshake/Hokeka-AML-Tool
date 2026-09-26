import type { GraphAnalysisStatus } from '../../features/api/queries'

export function graphAnalysisDisabledCopy(
  status: GraphAnalysisStatus,
  operator: boolean,
  variant: 'disabled' | 'unavailable' = 'disabled',
): { title: string; body: string } {
  const title =
    variant === 'unavailable' ? 'Graph analysis unavailable' : 'Graph analysis is turned off'

  if (variant === 'unavailable') {
    if (operator) {
      return {
        title,
        body:
          status.reason ??
          'Neo4j graph analysis is enabled in configuration but the database is not reachable. No relationship graph is shown until connectivity is restored.',
      }
    }
    return {
      title,
      body: 'Relationship graphs are temporarily unavailable. Try again later or contact your platform operator.',
    }
  }

  if (operator) {
    return {
      title,
      body:
        status.reason ??
        'Case network graphs use the Neo4j graph projection on the Control Plane (neo4j.enabled=false by default).',
    }
  }

  return {
    title,
    body: 'Relationship graphs are not available in your environment yet.',
  }
}
