/// <reference types="vite/client" />

interface ImportMetaEnv {
  readonly VITE_API_URL: string
  readonly VITE_APP_NAME: string
  readonly VITE_ENABLE_DEBUG: string
  readonly VITE_DEV_MOCK_API?: string
  readonly VITE_INCLUDE_OPERATOR_DOCS?: string
  readonly VITE_OPERATOR_DOCS_BASE_URL?: string
}

interface ImportMeta {
  readonly env: ImportMetaEnv
}
