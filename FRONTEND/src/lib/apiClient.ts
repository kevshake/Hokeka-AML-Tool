import { getApiUrl } from "../config/api";

export interface ApiResponse<T> {
  success?: boolean;
  data?: T;
  timestamp?: string;
  [key: string]: any;
}

export interface ApiError {
  timestamp?: string;
  status: number;
  error: string;
  errorCode?: string;
  message: string;
  details?: string[];
  traceId?: string;
}

class ApiClient {
  // Store PSP ID in memory only (not in localStorage to avoid exposure)
  private pspIdCache: number | null = null;

  /**
   * Get current user's PSP ID from in-memory cache
   * All users must have a PSP ID: 0 for Super Admin, >0 for PSP users
   * PSP ID is never exposed in URLs, logs, or localStorage
   */
  private getCurrentPspId(): number {
    // Return cached value if available
    if (this.pspIdCache !== null) {
      return this.pspIdCache;
    }
    
    // Try to get from sessionStorage (more secure than localStorage)
    try {
      const pspIdStr = sessionStorage.getItem("_psp");
      if (pspIdStr) {
        const pspId = Number(pspIdStr);
        if (!isNaN(pspId)) {
          this.pspIdCache = pspId;
          return pspId;
        }
      }
    } catch (e) {
      // Silently fail - don't log PSP ID
    }
    
    // Default to Super Admin PSP ID (0)
    return 0;
  }

  /**
   * Set PSP ID in memory cache and sessionStorage
   * PSP ID is never logged or exposed in URLs
   */
  setPspId(pspId: number): void {
    this.pspIdCache = pspId;
    try {
      sessionStorage.setItem("_psp", String(pspId));
    } catch (e) {
      // Silently fail - don't log
    }
  }

  /**
   * Clear PSP ID from cache
   */
  clearPspId(): void {
    this.pspIdCache = null;
    try {
      sessionStorage.removeItem("_psp");
    } catch (e) {
      // Silently fail
    }
  }

  private async request<T>(
    endpoint: string,
    options: RequestInit = {}
  ): Promise<T> {
    const url = getApiUrl(endpoint);
    const pspId = this.getCurrentPspId();
    
    // Add PSP ID to HTTP header (not URL query parameter) for security
    // Headers are not visible in browser address bar or view-source
    const defaultOptions: RequestInit = {
      credentials: "include", // Include cookies for session auth
      headers: {
        "Content-Type": "application/json",
        "X-PSP-ID": String(pspId), // Send PSP ID in header, not URL
        ...options.headers,
      },
    };

    const response = await fetch(url, {
      ...defaultOptions,
      ...options,
      headers: {
        ...defaultOptions.headers,
        ...options.headers,
      },
    });

    if (!response.ok) {
      const error: ApiError = await response.json().catch(() => ({
        status: response.status,
        error: response.statusText,
        message: "An error occurred",
      }));
      throw error;
    }

    const data: ApiResponse<T> = await response.json();
    return (data.data ?? data) as T;
  }

  async get<T>(endpoint: string, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "GET" });
  }

  async post<T>(endpoint: string, body?: any, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "POST",
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async put<T>(endpoint: string, body?: any, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, {
      ...options,
      method: "PUT",
      body: body ? JSON.stringify(body) : undefined,
    });
  }

  async delete<T>(endpoint: string, options?: RequestInit): Promise<T> {
    return this.request<T>(endpoint, { ...options, method: "DELETE" });
  }
}

export const apiClient = new ApiClient();

// Expose apiClient to window for AuthContext access (only for setPspId/clearPspId)
if (typeof window !== "undefined") {
  (window as any).apiClient = apiClient;
}
