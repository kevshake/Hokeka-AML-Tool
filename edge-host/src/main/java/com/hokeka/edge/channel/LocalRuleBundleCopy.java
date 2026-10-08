package com.hokeka.edge.channel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.attribute.PosixFilePermission;
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;

/**
 * On-disk copy of the last verified rule IR, kept beside the node identity.
 *
 * <p>Aerospike ({@code rules}/{@code active}) is the feature-store copy. This file is the copy that
 * still exists when that store was never configured or is down at boot — the edge must be able to
 * resume enforcing from its own premises without calling the Control Plane.
 */
public final class LocalRuleBundleCopy {

    private static final Logger log = LoggerFactory.getLogger(LocalRuleBundleCopy.class);

    private static final Set<PosixFilePermission> OWNER_RW = EnumSet.of(
            PosixFilePermission.OWNER_READ, PosixFilePermission.OWNER_WRITE);

    private final Path path;

    public LocalRuleBundleCopy(ControlPlaneProperties properties) {
        this(pathFor(properties));
    }

    LocalRuleBundleCopy(Path path) {
        this.path = path;
    }

    /** Default location: {@code rule-bundle.ir} next to {@code hokeka.controlplane.identity-file}. */
    public static Path pathFor(ControlPlaneProperties properties) {
        String configured = properties.getBundleFile();
        if (configured != null && !configured.isBlank()) {
            return Path.of(configured);
        }
        return Path.of(properties.getIdentityFile()).resolveSibling("rule-bundle.ir");
    }

    public Path path() {
        return path;
    }

    /**
     * Atomically replace the local copy. Returns false when the write fails; the caller must not
     * treat the bundle as durably stored.
     */
    public boolean save(long version, byte[] ruleIrJson) {
        if (ruleIrJson == null || ruleIrJson.length == 0) {
            return false;
        }
        Path tmp = path.resolveSibling(path.getFileName().toString() + ".tmp");
        try {
            Path parent = path.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            Files.write(tmp, ruleIrJson);
            restrictToOwner(tmp);
            try {
                Files.move(tmp, path, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
            restrictToOwner(path);
            log.info("Wrote local rule bundle v{} ({} bytes) to {}", version, ruleIrJson.length, path);
            return true;
        } catch (Exception e) {
            log.error("Could not write the local rule bundle to {}: {}", path, e.getMessage());
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
                // best-effort cleanup of the temp file
            }
            return false;
        }
    }

    public Optional<byte[]> load() {
        try {
            if (!Files.isRegularFile(path)) {
                return Optional.empty();
            }
            byte[] bytes = Files.readAllBytes(path);
            if (bytes.length == 0) {
                return Optional.empty();
            }
            log.info("Read local rule bundle ({} bytes) from {}", bytes.length, path);
            return Optional.of(bytes);
        } catch (Exception e) {
            log.warn("Could not read the local rule bundle at {}: {}", path, e.getMessage());
            return Optional.empty();
        }
    }

    private static void restrictToOwner(Path file) {
        try {
            Files.setPosixFilePermissions(file, OWNER_RW);
        } catch (UnsupportedOperationException ignored) {
            // non-POSIX filesystem
        } catch (IOException e) {
            log.warn("Could not restrict permissions on {}: {}", file, e.getMessage());
        }
    }
}
