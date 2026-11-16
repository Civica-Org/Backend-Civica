package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.ia.CivicaIaMessageDto;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.*;

/**
 * Client HTTP léger vers l'API REST gratuite Google Gemini (Google AI Studio).
 * Utilise l'en-tête x-goog-api-key (sans exposer la clé dans les URLs),
 * applique un timeout strict de 10 secondes (Chapitre 8 & 26) et bascule
 * automatiquement sur le modèle secondaire en cas de limitation de débit (429) ou d'erreur modèle.
 */
@Component
public class GeminiRestClient {

    private static final Logger log = LoggerFactory.getLogger(GeminiRestClient.class);

    public record GeminiGenerationResult(String text, String modelUsed, long latencyMs) {}

    private final RestClient restClient;
    private final ObjectMapper objectMapper;
    private final String apiKey;
    private final String primaryModel;
    private final String fallbackModel;
    private final long timeoutMs;

    @Autowired
    public GeminiRestClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            @Value("${civica.ai.gemini.baseUrl:https://generativelanguage.googleapis.com/v1beta}") String baseUrl,
            @Value("${civica.ai.gemini.apiKey:}") String apiKey,
            @Value("${civica.ai.gemini.model:gemini-3.5-flash-lite}") String primaryModel,
            @Value("${civica.ai.gemini.fallbackModel:gemini-flash-lite-latest}") String fallbackModel,
            @Value("${civica.ai.timeoutMs:10000}") long timeoutMs
    ) {
        this(builder, objectMapper, baseUrl, apiKey, primaryModel, fallbackModel, timeoutMs, true);
    }

    GeminiRestClient(
            RestClient.Builder builder,
            ObjectMapper objectMapper,
            String baseUrl,
            String apiKey,
            String primaryModel,
            String fallbackModel,
            long timeoutMs,
            boolean applyDefaultTimeouts
    ) {
        this.objectMapper = objectMapper;
        this.apiKey = resolveEffectiveApiKey(apiKey);
        this.primaryModel = primaryModel != null ? primaryModel.trim() : "gemini-3.5-flash-lite";
        this.fallbackModel = fallbackModel != null ? fallbackModel.trim() : "gemini-flash-lite-latest";
        this.timeoutMs = timeoutMs > 0 ? timeoutMs : 10000L;

        String normalizedBase = (baseUrl != null ? baseUrl.trim() : "https://generativelanguage.googleapis.com/v1beta")
                .replaceAll("/+$", "");

        RestClient.Builder configured = builder.baseUrl(normalizedBase);
        if (applyDefaultTimeouts) {
            SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
            requestFactory.setConnectTimeout(Duration.ofMillis(Math.min(4000L, this.timeoutMs)));
            requestFactory.setReadTimeout(Duration.ofMillis(this.timeoutMs));
            configured = configured.requestFactory(requestFactory);
        }

        this.restClient = configured.build();

        if (isConfigured()) {
            log.info("[CIVICA IA] Moteur Gemini initialisé (modèle principal: {}, fallback: {}).",
                    this.primaryModel, this.fallbackModel);
        } else {
            log.warn("[CIVICA IA] Clé GEMINI_API_KEY non détectée — mode assistance locale actif.");
        }
    }

    private String resolveEffectiveApiKey(String configuredKey) {
        String cleanConfigured = cleanSecret(configuredKey);
        if (!cleanConfigured.isEmpty()) {
            return cleanConfigured;
        }
        String fromEnv = cleanSecret(System.getenv("GEMINI_API_KEY"));
        if (!fromEnv.isEmpty()) {
            return fromEnv;
        }
        for (Path candidate : List.of(
                Paths.get(".env"),
                Paths.get("Backend-Civica/.env"),
                Paths.get("../Backend-Civica/.env")
        )) {
            String fromFile = readKeyFromDotEnv(candidate, "GEMINI_API_KEY");
            if (!fromFile.isEmpty()) {
                return fromFile;
            }
        }
        return "";
    }

    private String readKeyFromDotEnv(Path envFile, String keyName) {
        try {
            if (!Files.isRegularFile(envFile)) {
                return "";
            }
            for (String line : Files.readAllLines(envFile, StandardCharsets.UTF_8)) {
                String trimmed = line.trim();
                if (trimmed.isEmpty() || trimmed.startsWith("#")) continue;
                if (trimmed.startsWith("export ")) {
                    trimmed = trimmed.substring(7).trim();
                }
                int eq = trimmed.indexOf('=');
                if (eq > 0 && trimmed.substring(0, eq).trim().equals(keyName)) {
                    return cleanSecret(trimmed.substring(eq + 1));
                }
            }
        } catch (IOException ignored) {
            // Lecture .env optionnelle
        }
        return "";
    }

    private String cleanSecret(String raw) {
        if (raw == null) return "";
        String t = raw.trim();
        if ((t.startsWith("\"") && t.endsWith("\"")) || (t.startsWith("'") && t.endsWith("'"))) {
            if (t.length() >= 2) {
                t = t.substring(1, t.length() - 1).trim();
            }
        }
        return t;
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    public String getPrimaryModel() {
        return primaryModel;
    }

    public String getFallbackModel() {
        return fallbackModel;
    }

    public long getTimeoutMs() {
        return timeoutMs;
    }

    public Optional<GeminiGenerationResult> generateJson(String systemInstruction, String userPrompt) {
        return executeWithCascade(
                systemInstruction,
                List.of(Map.of("role", "user", "parts", List.of(Map.of("text", userPrompt)))),
                true,
                0.15
        );
    }

    public Optional<GeminiGenerationResult> generateChat(
            String systemInstruction,
            List<CivicaIaMessageDto> history,
            String latestUserPrompt
    ) {
        List<Map<String, Object>> contents = new ArrayList<>();
        if (history != null) {
            int start = Math.max(0, history.size() - 8);
            for (int i = start; i < history.size(); i++) {
                CivicaIaMessageDto msg = history.get(i);
                if (msg == null || msg.getContent() == null || msg.getContent().isBlank()) continue;
                String geminiRole = "assistant".equalsIgnoreCase(msg.getRole()) || "model".equalsIgnoreCase(msg.getRole())
                        ? "model"
                        : "user";
                contents.add(Map.of(
                        "role", geminiRole,
                        "parts", List.of(Map.of("text", msg.getContent().trim()))
                ));
            }
        }
        contents.add(Map.of(
                "role", "user",
                "parts", List.of(Map.of("text", latestUserPrompt))
        ));

        return executeWithCascade(systemInstruction, contents, false, 0.35);
    }

    private Optional<GeminiGenerationResult> executeWithCascade(
            String systemInstruction,
            List<Map<String, Object>> contents,
            boolean jsonResponse,
            double temperature
    ) {
        if (!isConfigured()) {
            return Optional.empty();
        }

        List<String> candidates = new ArrayList<>();
        candidates.add(primaryModel);
        if (!fallbackModel.isBlank() && !fallbackModel.equals(primaryModel)) {
            candidates.add(fallbackModel);
        }

        for (String model : candidates) {
            long start = System.currentTimeMillis();
            try {
                Map<String, Object> generationConfig = new LinkedHashMap<>();
                generationConfig.put("temperature", temperature);
                generationConfig.put("maxOutputTokens", jsonResponse ? 900 : 1200);
                if (jsonResponse) {
                    generationConfig.put("responseMimeType", "application/json");
                }

                Map<String, Object> payload = new LinkedHashMap<>();
                if (systemInstruction != null && !systemInstruction.isBlank()) {
                    payload.put("systemInstruction", Map.of(
                            "parts", List.of(Map.of("text", systemInstruction))
                    ));
                }
                payload.put("contents", contents);
                payload.put("generationConfig", generationConfig);

                String rawResponse = restClient.post()
                        .uri("/models/{model}:generateContent", model)
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("x-goog-api-key", apiKey)
                        .body(payload)
                        .retrieve()
                        .body(String.class);

                String extracted = extractTextFromResponse(rawResponse);
                long latency = System.currentTimeMillis() - start;
                if (extracted != null && !extracted.isBlank()) {
                    return Optional.of(new GeminiGenerationResult(extracted.trim(), model, latency));
                }
                log.warn("[GEMINI] Réponse vide reçue pour le modèle {} ({} ms).", model, latency);
            } catch (Exception ex) {
                long latency = System.currentTimeMillis() - start;
                log.warn("[GEMINI] Échec d'appel sur le modèle {} ({} ms) : {}", model, latency, ex.getMessage());
            }
        }

        return Optional.empty();
    }

    private String extractTextFromResponse(String rawJson) throws Exception {
        if (rawJson == null || rawJson.isBlank()) {
            return null;
        }
        JsonNode root = objectMapper.readTree(rawJson);
        JsonNode candidates = root.path("candidates");
        if (!candidates.isArray() || candidates.isEmpty()) {
            return null;
        }
        JsonNode parts = candidates.get(0).path("content").path("parts");
        if (!parts.isArray() || parts.isEmpty()) {
            return null;
        }
        StringBuilder sb = new StringBuilder();
        for (JsonNode part : parts) {
            if (part.path("thought").asBoolean(false)) {
                continue;
            }
            if (part.hasNonNull("text")) {
                sb.append(part.get("text").asText());
            }
        }
        return stripCodeFences(sb.toString());
    }

    private String stripCodeFences(String text) {
        String trimmed = text.trim();
        if (trimmed.startsWith("```")) {
            trimmed = trimmed.replaceFirst("^```(?:json)?\\s*", "");
            trimmed = trimmed.replaceFirst("\\s*```$", "");
        }
        return trimmed.trim();
    }
}

