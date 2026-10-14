/** Minimal OpenAPI 3 shapes used by the Management API docs site. */

export type HttpMethod = "get" | "post" | "put" | "patch" | "delete";

export interface OpenApiSpec {
  openapi: string;
  info: { title: string; description?: string; version: string };
  servers?: { url: string; description?: string }[];
  paths: Record<string, PathItem>;
  components?: {
    schemas?: Record<string, SchemaObject>;
    securitySchemes?: Record<string, SecurityScheme>;
  };
}

export interface PathItem {
  get?: Operation;
  post?: Operation;
  put?: Operation;
  patch?: Operation;
  delete?: Operation;
}

export interface Operation {
  operationId?: string;
  summary?: string;
  description?: string;
  tags?: string[];
  parameters?: Parameter[];
  requestBody?: RequestBody;
  responses?: Record<string, ResponseObject>;
  security?: Record<string, string[]>[];
}

export interface Parameter {
  name: string;
  in: "query" | "path" | "header" | "cookie";
  required?: boolean;
  description?: string;
  schema?: SchemaObject;
}

export interface RequestBody {
  required?: boolean;
  content?: Record<string, { schema?: SchemaObject; example?: unknown }>;
}

export interface ResponseObject {
  description?: string;
  content?: Record<string, { schema?: SchemaObject; example?: unknown }>;
}

export interface SchemaObject {
  type?: string;
  format?: string;
  enum?: string[];
  items?: SchemaObject;
  properties?: Record<string, SchemaObject>;
  required?: string[];
  $ref?: string;
  description?: string;
}

export interface SecurityScheme {
  type: string;
  name?: string;
  in?: string;
  description?: string;
}

export interface ManagementOperation {
  slug: string;
  method: HttpMethod;
  path: string;
  summary: string;
  description: string;
  group: string;
  operation: Operation;
}
