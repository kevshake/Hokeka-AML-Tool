import type { Audience } from "./types";

export interface DocMetaConfig {
  slug: string;
  shortTitle: string;
  audiences: Audience[];
  tags: string[];
  installOrder: number;
  illustration?: string;
  /** Hidden from the default PSP-facing docs site build (operator install runbooks). */
  operatorOnly?: boolean;
}

/** Static catalog metadata keyed by source filename under docs/install/. */
export const DOC_META: Record<string, DocMetaConfig> = {
  "README.md": {
    slug: "index",
    shortTitle: "Process Map",
    audiences: ["Overview"],
    tags: ["start-here", "topology", "roles"],
    installOrder: 0,
    illustration: "arch-overview.png",
  },
  "01-client-edge-node.md": {
    slug: "01-client-edge-node",
    shortTitle: "Edge Node",
    audiences: ["PSP Edge"],
    tags: ["edge", "aerospike", "enrollment", "install"],
    installOrder: 4,
    illustration: "edge-node.png",
  },
  "02-console-dashboard.md": {
    slug: "02-console-dashboard",
    shortTitle: "Console",
    audiences: ["Console"],
    tags: ["frontend", "dashboard", "nginx"],
    installOrder: 2,
    illustration: "console.png",
    operatorOnly: true,
  },
  "03-control-plane.md": {
    slug: "03-control-plane",
    shortTitle: "Control Plane",
    audiences: ["Control Plane"],
    tags: ["docker", "postgres", "vps", "flyway"],
    installOrder: 1,
    illustration: "control-plane.png",
    operatorOnly: true,
  },
  "04-packages-cdn-and-edge-release.md": {
    slug: "04-packages-cdn-and-edge-release",
    shortTitle: "Packages CDN",
    audiences: ["CDN & Release"],
    tags: ["cdn", "signing", "release", "artifacts"],
    installOrder: 3,
    illustration: "packages-cdn.png",
  },
  "05-local-developer-stack.md": {
    slug: "05-local-developer-stack",
    shortTitle: "Local Developer Stack",
    audiences: ["Local Engineer"],
    tags: ["development", "docker", "java", "vite"],
    installOrder: 99,
    illustration: "local-dev-stack.png",
  },
  "06-psp-api-dual-post-integration.md": {
    slug: "06-psp-api-dual-post-integration",
    shortTitle: "Dual-post API",
    audiences: ["Dual-post API"],
    tags: ["integration", "api", "webhooks", "dual-post"],
    installOrder: 5,
    illustration: "dual-post.png",
  },
};

export const ALL_AUDIENCES: Audience[] = [
  "Overview",
  "PSP Edge",
  "Console",
  "Control Plane",
  "CDN & Release",
  "Local Engineer",
  "Dual-post API",
];
