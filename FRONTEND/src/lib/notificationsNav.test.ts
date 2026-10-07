import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";

describe("in-app notifications navigation", () => {
  it("loads unread counts from the messages API and routes the bell to /messages", () => {
    const badgesSource = readFileSync(
      resolve(__dirname, "../hooks/useNavBadges.ts"),
      "utf8",
    );
    expect(badgesSource).toContain("messages/unread/count");

    const headerSource = readFileSync(
      resolve(__dirname, "../components/header/HokekaHeader.tsx"),
      "utf8",
    );
    expect(headerSource).toContain("navigate('/messages')");

    const sidebarSource = readFileSync(
      resolve(__dirname, "../components/sidebar/HokekaSidebar.tsx"),
      "utf8",
    );
    expect(sidebarSource).toContain("to: '/messages'");
  });
});
