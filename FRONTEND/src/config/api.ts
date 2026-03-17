// Environment-aware API configuration
// In both development and production, we use relative URLs
// - Local Dev: Vite dev server proxies /api/v1 to localhost:2637
// - Docker Dev: Vite dev server proxies /api/v1 to backend-dev:2637 (via VITE_PROXY_TARGET)
// - Prod: Nginx proxies /api to the backend container
export const API_BASE_URL = import.meta.env.VITE_API_URL || "";

export const API_VERSION = "/api/v1";

export const getApiUrl = (endpoint: string): string => {
  // Remove leading slash if present
  const cleanEndpoint = endpoint.startsWith("/") ? endpoint.slice(1) : endpoint;
  return `${API_BASE_URL}${API_VERSION}/${cleanEndpoint}`;
};
