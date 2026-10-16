import { readFileSync } from "node:fs";
import { resolve } from "node:path";
import { describe, expect, it } from "vitest";
import {
  confirmPasswordResetErrorMessage,
  GENERIC_PASSWORD_RESET_REQUEST_SUCCESS,
  isPasswordResetTransportFailure,
  PASSWORD_RESET_INVALID_TOKEN,
  PASSWORD_RESET_TRANSPORT_ERROR,
} from "../features/api/passwordReset";

describe("password reset console wiring", () => {
  it("posts identifier to request and token-only body to confirm", () => {
    const source = readFileSync(resolve(__dirname, "../features/api/passwordReset.ts"), "utf8");
    expect(source).toContain('auth/password-reset/request');
    expect(source).toContain('auth/password-reset/confirm');
    expect(source).toContain("identifier: string");
    expect(source).toContain("token: string");
  });

  it("UsersTab reuses the shared request helper", () => {
    const source = readFileSync(resolve(__dirname, "../pages/Users/UsersTab.tsx"), "utf8");
    expect(source).toContain("requestPasswordReset");
    expect(source).not.toContain("auth/password-reset/request");
  });

  it("login forgot-password link is a real route", () => {
    const login = readFileSync(resolve(__dirname, "../pages/Auth/LoginPage.tsx"), "utf8");
    expect(login).toContain('to="/forgot-password"');
    expect(login).not.toMatch(/Forgot password\?[\s\S]{0,80}href="#"/);
  });

  it("App wires public reset routes without wrapping them in ProtectedRoute", () => {
    const app = readFileSync(resolve(__dirname, "../App.tsx"), "utf8");
    const protectedJsx = app.indexOf("<ProtectedRoute>");
    const publicBlock = protectedJsx >= 0 ? app.slice(0, protectedJsx) : "";
    expect(publicBlock).toContain('path="/forgot-password"');
    expect(publicBlock).toContain('path="/password-reset/confirm"');
    expect(publicBlock).toContain('path="/password-reset.html"');
  });

  it("classifies transport failures without enumerating users", () => {
    expect(isPasswordResetTransportFailure(new TypeError("Failed to fetch"))).toBe(true);
    expect(isPasswordResetTransportFailure({ status: 503, error: "unavailable", message: "down" })).toBe(true);
    expect(isPasswordResetTransportFailure({ status: 400, error: "bad_request", message: "Invalid reset token" })).toBe(
      false,
    );
    expect(confirmPasswordResetErrorMessage(new TypeError("Failed to fetch"))).toBe(PASSWORD_RESET_TRANSPORT_ERROR);
    expect(
      confirmPasswordResetErrorMessage({ status: 400, error: "bad_request", message: "Invalid reset token" }),
    ).toBe(PASSWORD_RESET_INVALID_TOKEN);
    expect(GENERIC_PASSWORD_RESET_REQUEST_SUCCESS.toLowerCase()).toContain("if an account exists");
  });
});
