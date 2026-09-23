export type Audience =
  | "Overview"
  | "PSP Edge"
  | "Console"
  | "Control Plane"
  | "CDN & Release"
  | "Local Engineer"
  | "Dual-post API";

export interface InstallDoc {
  id: string;
  slug: string;
  filename: string;
  title: string;
  shortTitle: string;
  subtitle: string;
  audiences: Audience[];
  tags: string[];
  installOrder: number;
  illustration?: string;
  content: string;
  excerpt: string;
}

export type SortKey = "title" | "installOrder" | "audience";

export interface SearchResult {
  slug: string;
  title: string;
  excerpt: string;
  score: number;
}
