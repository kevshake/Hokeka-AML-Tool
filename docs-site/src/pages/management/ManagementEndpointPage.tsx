import { Link, useOutletContext, useParams } from "react-router-dom";
import { useMemo, useState } from "react";
import { findOperation } from "@/lib/managementApi/loadSpec";
import {
  buildCodeSamples,
  defaultServerUrl,
  fullUrl,
  methodClass,
} from "@/lib/managementApi/codeSamples";
import type { ManagementOperation, OpenApiSpec, SchemaObject } from "@/lib/managementApi/types";

interface MgmtContext {
  spec: OpenApiSpec | null;
  operations: ManagementOperation[];
}

const LANGS = [
  "cURL",
  "Python",
  "JavaScript",
  "Go",
  "Java",
  "PHP",
  "CSharp",
  "Ruby",
] as const;

/** Renders a single Management API operation page (Flagright-style reference). */
export function ManagementEndpointPage() {
  const { slug = "" } = useParams();
  const { spec, operations } = useOutletContext<MgmtContext>();
  const [serverUrl, setServerUrl] = useState("");
  const [activeLang, setActiveLang] = useState<(typeof LANGS)[number]>("cURL");
  const [tryStatus, setTryStatus] = useState<string | null>(null);
  const [tryBody, setTryBody] = useState<string>("");

  const op = useMemo(
    () => findOperation(operations, slug),
    [operations, slug],
  );

  const resolvedServer = serverUrl || (spec ? defaultServerUrl(spec) : "");
  const samples = useMemo(() => {
    if (!spec || !op) return null;
    return buildCodeSamples(spec, op, resolvedServer);
  }, [spec, op, resolvedServer]);

  if (!op) {
    return (
      <article className="mgmt-doc">
        <h1>Endpoint not found</h1>
        <p>
          Unknown reference slug <code>{slug}</code>.{" "}
          <Link to="/management-api">Return to overview</Link>.
        </p>
      </article>
    );
  }

  const displayUrl = fullUrl(resolvedServer, op.path, {
    id: "1",
    pspId: "100",
    jobUuid: "550e8400-e29b-41d4-a716-446655440000",
  });

  /** Sends a browser request to the selected server (may fail on CORS — expected outside Console origin). */
  async function runTryIt() {
    if (!op) return;
    setTryStatus("Sending…");
    setTryBody("");
    try {
      const response = await fetch(displayUrl, {
        method: op.method.toUpperCase(),
        credentials: "include",
        headers: { Accept: "application/json" },
      });
      const text = await response.text();
      setTryStatus(`${response.status} ${response.statusText}`);
      setTryBody(text.slice(0, 4000));
    } catch (err) {
      setTryStatus("Request blocked (CORS or network). Use code samples with a session cookie.");
      setTryBody(String(err));
    }
  }

  return (
    <article className="mgmt-doc mgmt-endpoint">
      <p className="mgmt-eyebrow">{op.group}</p>
      <h1>{op.summary}</h1>

      <div className="mgmt-method-url">
        <span className={`http-badge ${methodClass(op.method)}`}>
          {op.method.toUpperCase()}
        </span>
        <code className="mgmt-url">{displayUrl}</code>
      </div>

      <section>
        <h2>Description</h2>
        <p>{op.description || op.summary}</p>
      </section>

      <section>
        <h2>Authentication</h2>
        <p>
          Session cookie <code>JSESSIONID</code> from{" "}
          <code>POST /auth/login</code> (Console operator). Include on every request.
        </p>
      </section>

      <section>
        <h2>Server</h2>
        <select
          className="mgmt-server-select"
          value={resolvedServer}
          onChange={(e) => setServerUrl(e.target.value)}
          aria-label="API server base URL"
        >
          {(spec?.servers ?? [{ url: resolvedServer, description: "Default" }]).map(
            (s) => (
              <option key={s.url} value={s.url}>
                {s.description ? `${s.description} — ${s.url}` : s.url}
              </option>
            ),
          )}
        </select>
      </section>

      {op.operation.parameters && op.operation.parameters.length > 0 && (
        <section>
          <h2>Parameters</h2>
          <table className="mgmt-table">
            <thead>
              <tr>
                <th>Name</th>
                <th>In</th>
                <th>Type</th>
                <th>Required</th>
                <th>Description</th>
              </tr>
            </thead>
            <tbody>
              {op.operation.parameters.map((p) => (
                <tr key={`${p.in}-${p.name}`}>
                  <td>
                    <code>{p.name}</code>
                  </td>
                  <td>{p.in}</td>
                  <td>{schemaLabel(p.schema)}</td>
                  <td>{p.required ? "yes" : "no"}</td>
                  <td>{p.description ?? "—"}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </section>
      )}

      {op.operation.requestBody && (
        <section>
          <h2>Request body</h2>
          <SchemaBlock
            schema={
              op.operation.requestBody.content?.["application/json"]?.schema
            }
          />
        </section>
      )}

      <section>
        <h2>Responses</h2>
        {Object.entries(op.operation.responses ?? {}).map(([code, resp]) => (
          <div key={code} className="mgmt-response-block">
            <h3>
              <code>{code}</code> — {resp.description ?? "Response"}
            </h3>
            <SchemaBlock
              schema={resp.content?.["application/json"]?.schema}
            />
          </div>
        ))}
      </section>

      <section>
        <h2>Code samples</h2>
        <div className="mgmt-lang-tabs" role="tablist">
          {LANGS.map((lang) => (
            <button
              key={lang}
              type="button"
              role="tab"
              className={activeLang === lang ? "active" : ""}
              onClick={() => setActiveLang(lang)}
            >
              {lang === "CSharp" ? "C#" : lang}
            </button>
          ))}
        </div>
        <pre className="mgmt-code">
          <code>{samples?.[activeLang] ?? ""}</code>
        </pre>
      </section>

      <section className="mgmt-try-panel">
        <h2>Try it</h2>
        <p className="mgmt-muted">
          Runs in your browser against the selected server. Cross-origin calls require
          CORS allowlisting or run the samples from a tool with your session cookie.
        </p>
        <button type="button" className="mgmt-try-btn" onClick={() => void runTryIt()}>
          Send {op.method.toUpperCase()} request
        </button>
        {tryStatus && <p className="mgmt-try-status">{tryStatus}</p>}
        {tryBody && (
          <pre className="mgmt-code mgmt-try-body">
            <code>{tryBody}</code>
          </pre>
        )}
      </section>
    </article>
  );
}

/** Renders a compact schema type label for parameter tables. */
function schemaLabel(schema?: SchemaObject): string {
  if (!schema) return "object";
  if (schema.$ref) return schema.$ref.split("/").pop() ?? schema.$ref;
  if (schema.enum) return `enum(${schema.enum.join("|")})`;
  if (schema.type === "array" && schema.items) {
    return `array<${schemaLabel(schema.items)}>`;
  }
  return schema.type ?? "object";
}

/** Renders schema properties for Types section. */
function SchemaBlock({ schema }: { schema?: SchemaObject }) {
  if (!schema) {
    return <p className="mgmt-muted">No JSON schema published for this content type.</p>;
  }
  if (schema.$ref) {
    return (
      <p>
        Schema ref: <code>{schema.$ref}</code>
      </p>
    );
  }
  if (schema.properties) {
    return (
      <div>
        <h3 className="mgmt-types-heading">Types</h3>
        <ul className="mgmt-type-list">
          {Object.entries(schema.properties).map(([name, prop]) => (
            <li key={name}>
              <code>{name}</code> ({schemaLabel(prop)}
              {schema.required?.includes(name) ? ", required" : ", optional"})
              {prop.description ? ` — ${prop.description}` : ""}
            </li>
          ))}
        </ul>
      </div>
    );
  }
  return <p>Type: {schemaLabel(schema)}</p>;
}
