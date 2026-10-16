import { apiClient, type ApiError } from "../../lib/apiClient";

/** Matches POST /api/v1/auth/password-reset/request and UsersTab. */
export interface PasswordResetRequestBody {
  identifier: string;
}

/** Matches POST /api/v1/auth/password-reset/confirm (token only; password is reset to default). */
export interface PasswordResetConfirmBody {
  token: string;
}

export interface PasswordResetMessageResponse {
  message: string;
}

export const GENERIC_PASSWORD_RESET_REQUEST_SUCCESS =
  "If an account exists for that username or email, a reset link has been sent.";

export const PASSWORD_RESET_TRANSPORT_ERROR =
  "We could not complete this request. Please try again later.";

export const PASSWORD_RESET_CONFIRM_SUCCESS =
  "Your password has been reset to the default password. Sign in with that password, then change it.";

export const PASSWORD_RESET_MISSING_TOKEN =
  "This reset link is missing a token. Request a new password reset.";

export const PASSWORD_RESET_INVALID_TOKEN =
  "This reset link is invalid or has expired. Request a new password reset.";

/** Requests a reset link without exposing whether the account exists. */
export function requestPasswordReset(identifier: string): Promise<PasswordResetMessageResponse> {
  const body: PasswordResetRequestBody = { identifier };
  return apiClient.post<PasswordResetMessageResponse>("auth/password-reset/request", body);
}

/** Confirms a reset token using the backend's token-only reset contract. */
export function confirmPasswordReset(token: string): Promise<PasswordResetMessageResponse> {
  const body: PasswordResetConfirmBody = { token };
  return apiClient.post<PasswordResetMessageResponse>("auth/password-reset/confirm", body);
}

/** Narrows an unknown failure to the API client's structured error shape. */
export function isApiError(error: unknown): error is ApiError {
  return (
    typeof error === "object" &&
    error !== null &&
    "status" in error &&
    typeof (error as { status: unknown }).status === "number"
  );
}

/** Distinguishes connectivity and server failures from invalid reset tokens. */
export function isPasswordResetTransportFailure(error: unknown): boolean {
  if (error instanceof TypeError) {
    return true;
  }
  return isApiError(error) && error.status >= 500;
}

/** Returns a non-enumerating request failure message. */
export function passwordResetRequestErrorMessage(_error: unknown): string {
  return PASSWORD_RESET_TRANSPORT_ERROR;
}

/** Maps reset confirmation failures to safe user-facing text. */
export function confirmPasswordResetErrorMessage(error: unknown): string {
  if (isPasswordResetTransportFailure(error)) {
    return PASSWORD_RESET_TRANSPORT_ERROR;
  }
  if (isApiError(error) && error.status >= 400 && error.status < 500) {
    return PASSWORD_RESET_INVALID_TOKEN;
  }
  return PASSWORD_RESET_TRANSPORT_ERROR;
}
