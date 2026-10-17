package com.posgateway.aml.controller.edge;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.posgateway.aml.entity.edge.EdgeNode;
import com.posgateway.aml.service.edge.EdgeNodeAuthorizer;
import com.posgateway.aml.service.residency.EdgeSanctionsListService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Optional;

/**
 * Sanctions/PEP list distribution to Edge Nodes (EDGE_DATA_RESIDENCY_CONTRACT.md section 6.4):
 *
 * <table>
 *   <caption>Endpoints</caption>
 *   <tr><th>Method</th><th>Path</th><th>Returns</th></tr>
 *   <tr><td>GET</td><td>/edge/sanctions/list</td><td>HSE-1 sealed manifest of the newest snapshot,
 *       ETag = listHash; 404 before the first publish</td></tr>
 *   <tr><td>GET</td><td>/edge/sanctions/list/{listHash}/chunks/{index}</td><td>HSE-1 sealed chunk;
 *       404 when that snapshot is no longer published or the index does not exist</td></tr>
 * </table>
 *
 * <p>Machine-to-machine, authorised exactly like {@code /edge/config} (verified mTLS identity, node
 * ACTIVE), bare {@code 403} otherwise. The list is public data, but it is sealed like every other
 * cloud→Edge document so the Edge verifies the Control Plane signature before using it.
 */
@RestController
@RequestMapping("/edge/sanctions/list")
public class EdgeSanctionsListController {

    private final EdgeNodeAuthorizer authorizer;
    private final EdgeSanctionsListService lists;

    public EdgeSanctionsListController(EdgeNodeAuthorizer authorizer, EdgeSanctionsListService lists) {
        this.authorizer = authorizer;
        this.lists = lists;
    }

    /**
     * The newest snapshot's manifest, sealed to the node (context
     * {@value EdgeSanctionsListService#LIST_CONTEXT}). {@code If-None-Match} with its listHash
     * (quoted, bare or weak) answers {@code 304} without sealing.
     */
    @GetMapping(produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> manifest(HttpServletRequest request,
                                           @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                           String ifNoneMatch) {
        Optional<EdgeNode> authorized = authorizer.authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();
        Optional<EdgeSanctionsListService.Manifest> manifest = lists.latestManifest();
        authorizer.recordSeen(node, request);
        if (manifest.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        String hash = manifest.get().listHash();
        String version = manifest.get().document().get("listVersion").asText();
        if (matchesEtag(ifNoneMatch, hash)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED)
                    .eTag("\"" + hash + "\"")
                    .header("X-Sanctions-List-Version", version)
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .build();
        }
        return ResponseEntity.ok()
                .eTag("\"" + hash + "\"")
                .header("X-Sanctions-List-Version", version)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(lists.sealManifest(node, manifest.get()));
    }

    /**
     * One chunk of a published snapshot, sealed to the node (context
     * {@value EdgeSanctionsListService#CHUNK_CONTEXT}). Chunks never change, so the ETag is the
     * chunk's SHA-256 and a matching {@code If-None-Match} answers {@code 304}.
     */
    @GetMapping(path = "/{listHash}/chunks/{index}", produces = MediaType.APPLICATION_OCTET_STREAM_VALUE)
    public ResponseEntity<byte[]> chunk(HttpServletRequest request,
                                        @PathVariable("listHash") String listHash,
                                        @PathVariable("index") String index,
                                        @RequestHeader(value = HttpHeaders.IF_NONE_MATCH, required = false)
                                        String ifNoneMatch) {
        Optional<EdgeNode> authorized = authorizer.authorize(request);
        if (authorized.isEmpty()) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).build();
        }
        EdgeNode node = authorized.get();
        int chunkIndex;
        try {
            chunkIndex = Integer.parseInt(index);
        } catch (NumberFormatException e) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
        Optional<ObjectNode> chunk = lists.chunk(listHash, chunkIndex);
        if (chunk.isEmpty()) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        String sha = chunk.get().get("sha256").asText();
        if (matchesEtag(ifNoneMatch, sha)) {
            return ResponseEntity.status(HttpStatus.NOT_MODIFIED).eTag("\"" + sha + "\"")
                    .header(HttpHeaders.CACHE_CONTROL, "no-store").build();
        }
        return ResponseEntity.ok()
                .eTag("\"" + sha + "\"")
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.APPLICATION_OCTET_STREAM)
                .body(lists.sealChunk(node, chunk.get()));
    }

    /** Accepts {@code "h"}, {@code h}, {@code W/"h"} and lists of them for {@code If-None-Match}. */
    static boolean matchesEtag(String ifNoneMatch, String tag) {
        if (ifNoneMatch == null || ifNoneMatch.isBlank()) {
            return false;
        }
        for (String candidate : ifNoneMatch.split(",")) {
            String normalised = candidate.trim();
            if (normalised.startsWith("W/")) {
                normalised = normalised.substring(2).trim();
            }
            if (normalised.length() >= 2 && normalised.startsWith("\"") && normalised.endsWith("\"")) {
                normalised = normalised.substring(1, normalised.length() - 1);
            }
            if (normalised.equals(tag) || "*".equals(normalised)) {
                return true;
            }
        }
        return false;
    }
}
