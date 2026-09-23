import type { InstallDoc } from "@/lib/types";

interface DocHeroProps {
  doc: InstallDoc;
}

export function DocHero({ doc }: DocHeroProps) {
  if (!doc.illustration) return null;

  return (
    <figure className="doc-hero">
      <img
        src={`/illustrations/${doc.illustration}`}
        alt=""
        loading="eager"
      />
      <figcaption className="sr-only">{doc.title} illustration</figcaption>
    </figure>
  );
}
