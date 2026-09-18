package com.hokeka.edge.activation;

/**
 * Whether this node has been authorized by the control plane. Until it has, the engine must not
 * return an ALLOW decision (channel contract §2) — everything holds.
 */
public interface AuthorizationGate {

    String STATE_ACTIVE = "active";
    String STATE_UNAUTHORIZED = "unauthorized";

    boolean authorized();

    /** {@link #STATE_ACTIVE} or {@link #STATE_UNAUTHORIZED}, as reported by {@code /edge/status}. */
    String state();

    /** Human-readable reason the node is not yet authorized; empty when active. */
    String reason();

    /** A gate that is always open — for unit tests and the non-Spring reference use of the engine. */
    static AuthorizationGate alwaysAuthorized() {
        return new AuthorizationGate() {
            @Override
            public boolean authorized() {
                return true;
            }

            @Override
            public String state() {
                return STATE_ACTIVE;
            }

            @Override
            public String reason() {
                return "";
            }
        };
    }
}
