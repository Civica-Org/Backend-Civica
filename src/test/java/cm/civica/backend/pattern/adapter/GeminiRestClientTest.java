package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.ia.CivicaIaMessageDto;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

class GeminiRestClientTest {

    @Test
    void testGenerateChatPreservesBasePathAndFiltersThoughtParts() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GeminiRestClient client = new GeminiRestClient(
                builder,
                new ObjectMapper(),
                "https://generativelanguage.googleapis.com/v1beta",
                "test-api-key",
                "gemini-3.5-flash-lite",
                "gemini-flash-lite-latest",
                10000L,
                false
        );

        String mockResponse = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [
                          {"text": "internal thought", "thought": true},
                          {"text": "Réponse officielle CIVICA."}
                        ]
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("x-goog-api-key", "test-api-key"))
                .andRespond(withSuccess(mockResponse, MediaType.APPLICATION_JSON));

        Optional<GeminiRestClient.GeminiGenerationResult> result = client.generateChat(
                "Tu es Civica IA.",
                List.of(new CivicaIaMessageDto("user", "Bonjour")),
                "Pieces pour la CNI ?"
        );

        assertTrue(result.isPresent());
        assertEquals("gemini-3.5-flash-lite", result.get().modelUsed());
        assertEquals("Réponse officielle CIVICA.", result.get().text());
        server.verify();
    }

    @Test
    void testCascadeVersModeleFallbackSiPrincipalRetourne404() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

        GeminiRestClient client = new GeminiRestClient(
                builder,
                new ObjectMapper(),
                "https://generativelanguage.googleapis.com/v1beta",
                "test-api-key",
                "gemini-3.5-flash-lite",
                "gemini-flash-lite-latest",
                10000L,
                false
        );

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-3.5-flash-lite:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withResourceNotFound());

        String fallbackBody = """
                {
                  "candidates": [
                    {
                      "content": {
                        "parts": [{"text": "Réponse via fallback modèle."}]
                      }
                    }
                  ]
                }
                """;

        server.expect(requestTo("https://generativelanguage.googleapis.com/v1beta/models/gemini-flash-lite-latest:generateContent"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess(fallbackBody, MediaType.APPLICATION_JSON));

        Optional<GeminiRestClient.GeminiGenerationResult> result = client.generateChat(
                "Système",
                List.of(),
                "Question test"
        );

        assertTrue(result.isPresent());
        assertEquals("gemini-flash-lite-latest", result.get().modelUsed());
        assertEquals("Réponse via fallback modèle.", result.get().text());
        server.verify();
    }
}
