import type { HttpMethod, ManagementOperation, OpenApiSpec } from "./types";

/** Default sandbox server from the OpenAPI spec. */
export function defaultServerUrl(spec: OpenApiSpec): string {
  return spec.servers?.[0]?.url ?? "https://testapi.hokeka.com/api/v1";
}

/** Builds a full URL for an operation including path parameters replaced with placeholders. */
export function fullUrl(
  serverUrl: string,
  path: string,
  sampleParams: Record<string, string>,
): string {
  let resolved = path;
  for (const [key, value] of Object.entries(sampleParams)) {
    resolved = resolved.replace(`{${key}}`, value);
  }
  const base = serverUrl.replace(/\/$/, "");
  return `${base}${resolved.startsWith("/") ? resolved : `/${resolved}`}`;
}

const SAMPLE_PATH_PARAMS: Record<string, string> = {
  id: "1",
  pspId: "100",
  userId: "42",
  jobUuid: "550e8400-e29b-41d4-a716-446655440000",
  rateId: "10",
  engineCode: "default",
  versionId: "20",
  ruleId: "30",
};

/** Generates multi-language HTTP examples for the docs code tabs. */
export function buildCodeSamples(
  _spec: OpenApiSpec,
  op: ManagementOperation,
  serverUrl: string,
): Record<string, string> {
  const url = fullUrl(serverUrl, op.path, SAMPLE_PATH_PARAMS);
  const method = op.method.toUpperCase();
  const hasBody = op.method === "post" || op.method === "put" || op.method === "patch";
  const bodyJson = hasBody ? '{\n  "example": true\n}' : "";

  return {
    cURL: hasBody
      ? `curl -X ${method} '${url}' \\
  -H 'Content-Type: application/json' \\
  -H 'Cookie: JSESSIONID=<session-from-login>' \\
  -d '${bodyJson.replace(/\n/g, "")}'`
      : `curl -X ${method} '${url}' \\
  -H 'Cookie: JSESSIONID=<session-from-login>'`,

    Python: hasBody
      ? `import requests

url = "${url}"
headers = {"Cookie": "JSESSIONID=<session-from-login>"}
payload = {"example": True}

response = requests.${op.method}(url, json=payload, headers=headers)
print(response.status_code, response.json())`
      : `import requests

url = "${url}"
headers = {"Cookie": "JSESSIONID=<session-from-login>"}

response = requests.${op.method}(url, headers=headers)
print(response.status_code, response.json())`,

    JavaScript: hasBody
      ? `const url = '${url}';
const response = await fetch(url, {
  method: '${method}',
  headers: {
    'Content-Type': 'application/json',
    Cookie: 'JSESSIONID=<session-from-login>',
  },
  credentials: 'include',
  body: JSON.stringify({ example: true }),
});
console.log(await response.json());`
      : `const url = '${url}';
const response = await fetch(url, {
  method: '${method}',
  headers: { Cookie: 'JSESSIONID=<session-from-login>' },
  credentials: 'include',
});
console.log(await response.json());`,

    Go: `package main

import (
  "fmt"
  "net/http"
  "io"
)

func main() {
  req, _ := http.NewRequest("${method}", "${url}", nil)
  req.Header.Add("Cookie", "JSESSIONID=<session-from-login>")
  res, _ := http.DefaultClient.Do(req)
  defer res.Body.Close()
  body, _ := io.ReadAll(res.Body)
  fmt.Println(res.Status, string(body))
}`,

    Java: `HttpRequest request = HttpRequest.newBuilder()
  .uri(URI.create("${url}"))
  .header("Cookie", "JSESSIONID=<session-from-login>")
  .method("${method}", ${hasBody ? "HttpRequest.BodyPublishers.ofString(\"{\\\"example\\\":true}\")" : "HttpRequest.BodyPublishers.noBody()"})
  .build();
HttpResponse<String> response = HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
System.out.println(response.body());`,

    PHP: `<?php
$client = new \\GuzzleHttp\\Client();
$response = $client->request('${method}', '${url}', [
  'headers' => ['Cookie' => 'JSESSIONID=<session-from-login>'],
]);
echo $response->getBody();`,

    CSharp: `using var client = new HttpClient();
var request = new HttpRequestMessage(HttpMethod.${method.toUpperCase()}, "${url}");
request.Headers.Add("Cookie", "JSESSIONID=<session-from-login>");
var response = await client.SendAsync(request);
Console.WriteLine(await response.Content.ReadAsStringAsync());`,

    Ruby: `require 'net/http'
uri = URI('${url}')
req = Net::HTTP::${method === "get" ? "Get" : method === "post" ? "Post" : method === "put" ? "Put" : method === "patch" ? "Patch" : "Delete"}.new(uri)
req['Cookie'] = 'JSESSIONID=<session-from-login>'
res = Net::HTTP.start(uri.hostname, uri.port, use_ssl: uri.scheme == 'https') { |http| http.request(req) }
puts res.body`,
  };
}

/** Human-readable method badge color class. */
export function methodClass(method: HttpMethod): string {
  return `http-${method}`;
}
