import ReactMarkdown from "react-markdown";
import remarkGfm from "remark-gfm";
import { Link } from "react-router-dom";
import { rewriteInstallLinks, slugFromInstallLink } from "@/lib/docs";

interface MarkdownRendererProps {
  content: string;
}

function isExternal(href: string): boolean {
  return /^https?:\/\//i.test(href) || href.startsWith("mailto:");
}

export function MarkdownRenderer({ content }: MarkdownRendererProps) {
  const processed = rewriteInstallLinks(content);

  return (
    <article className="markdown-body">
      <ReactMarkdown
        remarkPlugins={[remarkGfm]}
        components={{
          a({ href = "", children, ...props }) {
            const slug = slugFromInstallLink(href);
            if (slug) {
              const to = slug === "index" ? "/" : `/docs/${slug}`;
              return (
                <Link to={to} {...props}>
                  {children}
                </Link>
              );
            }
            if (isExternal(href)) {
              return (
                <a href={href} target="_blank" rel="noopener noreferrer" {...props}>
                  {children}
                </a>
              );
            }
            return (
              <a href={href} {...props}>
                {children}
              </a>
            );
          },
          table({ children, ...props }) {
            return (
              <div className="table-scroll">
                <table {...props}>{children}</table>
              </div>
            );
          },
        }}
      >
        {processed}
      </ReactMarkdown>
    </article>
  );
}
