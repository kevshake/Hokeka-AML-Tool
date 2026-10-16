import { installDevMockFetch, isDevMockEnabled } from './mockApi'

export function installDevMocks(): void {
  if (!isDevMockEnabled()) return
  installDevMockFetch()
}
