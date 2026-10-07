import { DOC_META } from "./docMeta";
import type { InstallDoc } from "./types";

const rawModules = import.meta.glob("../../../docs/install/*.md", {
  query: "?raw",
  import: "default",
  eager: true,
}) as Record<string, string>;

function parseTitle(content: string): { title: string; subtitle: string } {
  const lines = content.split("\n");
  const heading = lines.find((line) => line.startsWith("# "));
  const title = heading ? heading.replace(/^#\s+/, "").trim() : "Untitled";
  const audienceLine = lines.find((line) =>
    line.startsWith("**Audience:**"),
  );
  const subtitle = audienceLine
    ? audienceLine.replace(/\*\*Audience:\*\*\s*/, "").trim()
    : "";
  return { title, subtitle };
}

function buildExcerpt(content: string): string {
  const stripped = content
    .replace(/^#.+$/gm, "")
    .replace(/```[\s\S]*?```/g, "")
    .replace(/\[([^\]]+)\]\([^)]+\)/g, "$1")
    .replace(/[*_`>|]/g, "")
    .replace(/\s+/g, " ")
    .trim();
  return stripped.slice(0, 220) + (stripped.length > 220 ? "…" : "");
}

const includeOperatorDocs =
  import.meta.env.VITE_INCLUDE_OPERATOR_DOCS === "true";

function loadDocs(): InstallDoc[] {
  const docs: InstallDoc[] = [];

  for (const [path, content] of Object.entries(rawModules)) {
    const filename = path.split("/").pop() ?? path;
    const meta = DOC_META[filename];
    if (!meta) {
      console.warn(`No metadata for install doc: ${filename}`);
      continue;
    }
    if (meta.operatorOnly && !includeOperatorDocs) {
      continue;
    }
    const { title, subtitle } = parseTitle(content);
    docs.push({
      id: filename,
      slug: meta.slug,
      filename,
      title,
      shortTitle: meta.shortTitle,
      subtitle,
      audiences: meta.audiences,
      tags: meta.tags,
      installOrder: meta.installOrder,
      illustration: meta.illustration,
      content,
      excerpt: buildExcerpt(content),
    });
  }

  return docs.sort((a, b) => a.installOrder - b.installOrder);
}

export const INSTALL_DOCS: InstallDoc[] = loadDocs();

export function getDocBySlug(slug: string): InstallDoc | undefined {
  return INSTALL_DOCS.find((doc) => doc.slug === slug);
}

export function slugFromInstallLink(href: string): string | null {
  const basename = href.split("/").pop() ?? href;
  if (!basename.endsWith(".md")) return null;
  const meta = DOC_META[basename];
  return meta?.slug ?? null;
}

/** Rewrite intra-install markdown links to in-app routes. */
export function rewriteInstallLinks(markdown: string): string {
  return markdown.replace(
    /(\[[^\]]+\]\()([^)]+)(\))/g,
    (match, prefix: string, href: string, suffix: string) => {
      const slug = slugFromInstallLink(href);
      if (slug) {
        const route = slug === "index" ? "/" : `/docs/${slug}`;
        return `${prefix}${route}${suffix}`;
      }
      return match;
    },
  );
}
