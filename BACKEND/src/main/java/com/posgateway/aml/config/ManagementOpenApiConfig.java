package com.posgateway.aml.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springdoc.core.customizers.OperationCustomizer;
import org.springdoc.core.models.GroupedOpenApi;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.method.HandlerMethod;

import java.lang.annotation.Annotation;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Springdoc group for operator / tenant administration endpoints exposed to the Console.
 * Kept separate from transaction ingestion and screening APIs so public docs can mirror
 * Flagright-style Management vs FRAML separation.
 */
@Configuration
public class ManagementOpenApiConfig {

    private static final String SESSION_AUTH = "sessionCookie";

    /** Relative servlet paths included in the Management group (Console operator / admin API). */
    static final String[] MANAGEMENT_PATH_PREFIXES = {
            "/users/**",
            "/roles/**",
            "/auth/roles",
            "/auth/permissions",
            "/auth/role-permissions",
            "/psps/**",
            "/admin/**",
            "/edge/nodes/**",
            "/webhooks/**",
            "/rules/**",
            "/audit/logs/**",
            "/console/exports/**",
            "/settings/**"
    };

    /** Self-service profile, PSP machine login, and operator-only AI admin / investigation paths. */
    static final String[] MANAGEMENT_PATH_EXCLUSIONS = {
            "/users/me",
            "/users/me/**",
            "/psps/auth/**",
            "/ai/**"
    };

    /** Spring MVC package excluded from the public Management OpenAPI group (operator-only AI admin). */
    static final String MANAGEMENT_PACKAGES_TO_EXCLUDE =
            "com.posgateway.aml.controller.ai";

    @Value("${server.servlet.context-path:/api/v1}")
    private String contextPath;

    @Value("${server.port:2637}")
    private int serverPort;

    /**
     * Registers the {@code management} OpenAPI group served at {@code /api-docs/management}.
     */
    @Bean
    public GroupedOpenApi managementOpenApi() {
        return GroupedOpenApi.builder()
                .group("management")
                .packagesToExclude(MANAGEMENT_PACKAGES_TO_EXCLUDE)
                .pathsToMatch(MANAGEMENT_PATH_PREFIXES)
                .pathsToExclude(MANAGEMENT_PATH_EXCLUSIONS)
                .addOpenApiCustomizer(openApi -> {
                    openApi.info(new Info()
                            .title("Hokeka Management API")
                            .version("1.0.0")
                            .description("""
                                    Operator and tenant administration for the Hokeka Control Plane (Console-backed).

                                    Use this API to manage users, roles, PSP tenants, API keys, webhooks, rules, edge nodes,
                                    export jobs, audit logs, and platform toggles. Transaction monitoring, screening, and
                                    case workflows are documented separately (PSP / FRAML-style integration API).

                                    **Authentication:** Authenticate with `POST /auth/login` and send the session cookie
                                    (`JSESSIONID`) on subsequent requests. CSRF is disabled for JSON API clients in dev;
                                    production Console flows use the same session model behind nginx TLS termination.
                                    """)
                            .contact(new Contact()
                                    .name("Hokeka AML Support")
                                    .email("support@hokeka.com")
                                    .url("https://doc.hokeka.com"))
                            .license(new License()
                                    .name("Proprietary")
                                    .url("https://doc.hokeka.com")));

                    openApi.setServers(List.of(
                            new Server()
                                    .url("http://localhost:" + serverPort + contextPath)
                                    .description("Local sandbox"),
                            new Server()
                                    .url("https://testapi.hokeka.com" + contextPath)
                                    .description("Sandbox / test"),
                            new Server()
                                    .url("https://api.hokeka.com" + contextPath)
                                    .description("Production")));

                    openApi.getComponents().addSecuritySchemes(SESSION_AUTH,
                            new SecurityScheme()
                                    .name("JSESSIONID")
                                    .type(SecurityScheme.Type.APIKEY)
                                    .in(SecurityScheme.In.COOKIE)
                                    .description("Session cookie returned by POST /auth/login after successful Console authentication."));

                    openApi.addSecurityItem(new SecurityRequirement().addList(SESSION_AUTH));

                    if (openApi.getPaths() != null) {
                        openApi.getPaths().entrySet().removeIf(entry ->
                                !ManagementPathRules.isDocumentedManagementPath(entry.getKey()));
                    }
                    ManagementPublicSpecSanitizer.removeOperatorOnlyAiSurfaces(openApi);
                })
                .addOperationCustomizer(managementOperationCustomizer())
                .build();
    }

    /**
     * Ant-style rules for which servlet paths belong in the published Management API spec.
     */
    static final class ManagementPathRules {

        private ManagementPathRules() {
        }

        /** Returns true when {@code servletPath} should appear in the Management API OpenAPI group. */
        static boolean isDocumentedManagementPath(String servletPath) {
            if (servletPath == null || servletPath.isBlank()) {
                return false;
            }
            String path = servletPath.startsWith("/") ? servletPath : "/" + servletPath;
            for (String excluded : MANAGEMENT_PATH_EXCLUSIONS) {
                if (matchesAnt(path, excluded)) {
                    return false;
                }
            }
            for (String included : MANAGEMENT_PATH_PREFIXES) {
                if (matchesAnt(path, included)) {
                    return true;
                }
            }
            return false;
        }

        /** Minimal ant matcher supporting {@code *} (one segment) and {@code **} (suffix). */
        static boolean matchesAnt(String path, String pattern) {
            if (pattern.endsWith("/**")) {
                String prefix = pattern.substring(0, pattern.length() - 3);
                return path.equals(prefix) || path.startsWith(prefix + "/");
            }
            if (pattern.endsWith("/*")) {
                String prefix = pattern.substring(0, pattern.length() - 2);
                if (!path.startsWith(prefix)) {
                    return false;
                }
                String rest = path.substring(prefix.length());
                return rest.isEmpty() || (rest.startsWith("/") && !rest.substring(1).contains("/"));
            }
            return path.equals(pattern);
        }
    }

    /**
     * Adds human-readable Flagright-style operation titles and stable operationIds when controllers
     * omit explicit {@code @Operation} annotations (management group only).
     */
    private OperationCustomizer managementOperationCustomizer() {
        return (operation, handlerMethod) -> {
            if (operation.getSummary() == null || operation.getSummary().isBlank()) {
                operation.setSummary(buildSummary(handlerMethod));
            }
            if (operation.getOperationId() == null || operation.getOperationId().isBlank()) {
                operation.setOperationId(buildOperationId(handlerMethod));
            }
            return operation;
        };
    }

    /** Builds a display title such as {@code User - List} from the HTTP verb and request mapping path. */
    private static String buildSummary(HandlerMethod handlerMethod) {
        String verb = verbLabel(handlerMethod);
        String resource = resourceLabel(handlerMethod);
        return resource + " - " + verb;
    }

    /** Builds a kebab-case operation id used by the docs site for deep links. */
    private static String buildOperationId(HandlerMethod handlerMethod) {
        String verb = primaryHttpMethod(handlerMethod).name().toLowerCase(Locale.ROOT);
        String path = ManagementApiPathSupport.mappingPath(handlerMethod);
        return (verb + "-" + path.replace("/", "-").replace("{", "").replace("}", ""))
                .replaceAll("-+", "-")
                .replaceAll("^-|-$", "");
    }

    private static String verbLabel(HandlerMethod handlerMethod) {
        return switch (primaryHttpMethod(handlerMethod)) {
            case GET -> pathEndsWithId(handlerMethod) ? "Get" : "List";
            case POST -> "Create";
            case PUT -> "Update";
            case PATCH -> "Update";
            case DELETE -> "Delete";
            default -> "Invoke";
        };
    }

    private static boolean pathEndsWithId(HandlerMethod handlerMethod) {
        return ManagementApiPathSupport.mappingPath(handlerMethod).contains("{");
    }

    private static String resourceLabel(HandlerMethod handlerMethod) {
        for (String part : ManagementApiPathSupport.mappingPath(handlerMethod).split("/")) {
            if (part.isBlank() || part.startsWith("{")) {
                continue;
            }
            return ManagementApiPathSupport.RESOURCE_TITLES.getOrDefault(
                    part,
                    part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1));
        }
        return "Resource";
    }

    /** Reads the primary HTTP verb from standard Spring MVC mapping annotations on the handler method. */
    private static RequestMethod primaryHttpMethod(HandlerMethod handlerMethod) {
        var method = handlerMethod.getMethod();
        if (method.isAnnotationPresent(GetMapping.class)) {
            return RequestMethod.GET;
        }
        if (method.isAnnotationPresent(PostMapping.class)) {
            return RequestMethod.POST;
        }
        if (method.isAnnotationPresent(PutMapping.class)) {
            return RequestMethod.PUT;
        }
        if (method.isAnnotationPresent(PatchMapping.class)) {
            return RequestMethod.PATCH;
        }
        if (method.isAnnotationPresent(DeleteMapping.class)) {
            return RequestMethod.DELETE;
        }
        RequestMapping mapping = method.getAnnotation(RequestMapping.class);
        if (mapping != null && mapping.method().length > 0) {
            return mapping.method()[0];
        }
        return RequestMethod.GET;
    }

    /** Helpers for reading Spring MVC mapping metadata when generating OpenAPI operation labels. */
    static final class ManagementApiPathSupport {

        private static final Map<String, String> RESOURCE_TITLES = new LinkedHashMap<>();

        static {
            RESOURCE_TITLES.put("users", "User");
            RESOURCE_TITLES.put("roles", "Role");
            RESOURCE_TITLES.put("psps", "PSP");
            RESOURCE_TITLES.put("webhooks", "Webhook");
            RESOURCE_TITLES.put("rules", "Rule");
            RESOURCE_TITLES.put("audit", "Audit");
            RESOURCE_TITLES.put("logs", "Audit log");
            RESOURCE_TITLES.put("console", "Console export");
            RESOURCE_TITLES.put("exports", "Export");
            RESOURCE_TITLES.put("settings", "Settings");
            RESOURCE_TITLES.put("edge", "Edge");
            RESOURCE_TITLES.put("nodes", "Edge node");
            RESOURCE_TITLES.put("admin", "Admin");
            RESOURCE_TITLES.put("invites", "Onboarding invite");
            RESOURCE_TITLES.put("psp-api-keys", "PSP API key");
            RESOURCE_TITLES.put("decision-orchestrator", "Decision orchestrator");
            RESOURCE_TITLES.put("permissions", "Permission");
        }

        private ManagementApiPathSupport() {
        }

        /** Returns the class-level plus method-level {@code @RequestMapping} path for the handler. */
        static String mappingPath(HandlerMethod handlerMethod) {
            String typePath = "";
            RequestMapping typeMapping = handlerMethod.getBeanType().getAnnotation(RequestMapping.class);
            if (typeMapping != null && typeMapping.value().length > 0) {
                typePath = typeMapping.value()[0];
            }
            String methodPath = firstPathFromMethodAnnotations(handlerMethod);
            String combined = (typePath + "/" + methodPath).replaceAll("/+", "/");
            if (combined.endsWith("/")) {
                combined = combined.substring(0, combined.length() - 1);
            }
            if (combined.isBlank()) {
                return "/";
            }
            return combined.startsWith("/") ? combined : "/" + combined;
        }

        private static String firstPathFromMethodAnnotations(HandlerMethod handlerMethod) {
            for (Annotation ann : handlerMethod.getMethod().getAnnotations()) {
                String path = singlePathFromMappingAnnotation(ann);
                if (path != null) {
                    return path;
                }
            }
            return "";
        }

        private static String singlePathFromMappingAnnotation(Annotation ann) {
            if (ann instanceof GetMapping m && m.value().length > 0) {
                return m.value()[0];
            }
            if (ann instanceof PostMapping m && m.value().length > 0) {
                return m.value()[0];
            }
            if (ann instanceof PutMapping m && m.value().length > 0) {
                return m.value()[0];
            }
            if (ann instanceof PatchMapping m && m.value().length > 0) {
                return m.value()[0];
            }
            if (ann instanceof DeleteMapping m && m.value().length > 0) {
                return m.value()[0];
            }
            if (ann instanceof RequestMapping m && m.value().length > 0) {
                return m.value()[0];
            }
            return null;
        }
    }

    /**
     * Strips operator-only Hokeka AI admin paths and internal AI schema fields from the PSP-visible
     * Management API OpenAPI document (doc.hokeka.com).
     */
    static final class ManagementPublicSpecSanitizer {

        private static final String[] FORBIDDEN_PATH_PREFIXES = {
                "/ai/engines",
                "/ai/psps",
                "/ai/status"
        };

        private static final String[] FORBIDDEN_SCHEMA_NAMES = {
                "AiEngineSetting"
        };

        private static final String[] FORBIDDEN_PROPERTY_NAMES = {
                "promptVersion",
                "modelId",
                "aiProvider",
                "tokenCount",
                "inputTokens",
                "outputTokens",
                "aiCostUsd"
        };

        private ManagementPublicSpecSanitizer() {
        }

        /** Removes {@code /ai/**} admin operations and schemas that expose AI operator internals. */
        static void removeOperatorOnlyAiSurfaces(OpenAPI openApi) {
            if (openApi.getPaths() != null) {
                openApi.getPaths().entrySet().removeIf(entry ->
                        isForbiddenPublicManagementPath(entry.getKey()));
            }
            if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
                return;
            }
            var schemas = openApi.getComponents().getSchemas();
            for (String forbidden : FORBIDDEN_SCHEMA_NAMES) {
                schemas.remove(forbidden);
            }
            schemas.entrySet().removeIf(entry ->
                    schemaExposesForbiddenAiInternals(entry.getKey(), entry.getValue()));
        }

        /** Returns true when a servlet path must not appear on the public Management API docs site. */
        static boolean isForbiddenPublicManagementPath(String servletPath) {
            if (servletPath == null || servletPath.isBlank()) {
                return false;
            }
            String path = servletPath.startsWith("/") ? servletPath : "/" + servletPath;
            if (ManagementPathRules.matchesAnt(path, "/ai/**")) {
                return true;
            }
            for (String prefix : FORBIDDEN_PATH_PREFIXES) {
                if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                    return true;
                }
            }
            return false;
        }

        /** Returns true when a component schema name or property list exposes AI operator internals. */
        private static boolean schemaExposesForbiddenAiInternals(String schemaName, Object schema) {
            if (schemaName != null && schemaName.startsWith("AiEngine")) {
                return true;
            }
            String serialized = String.valueOf(schema);
            for (String property : FORBIDDEN_PROPERTY_NAMES) {
                if (serialized.contains("\"" + property + "\"")) {
                    return true;
                }
            }
            return false;
        }
    }
}
