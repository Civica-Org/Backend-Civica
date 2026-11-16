package cm.civica.backend.pattern.adapter;

import cm.civica.backend.dto.AuditCompletudeDto;
import cm.civica.backend.dto.ia.CivicaIaChatResponse;
import cm.civica.backend.model.Demarche;
import cm.civica.backend.model.Dossier;
import cm.civica.backend.model.PieceJustificative;
import cm.civica.backend.model.Utilisateur;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GeminiLlmAdapterTest {

    @Mock
    private GeminiRestClient geminiRestClient;

    private PiiAnonymizer piiAnonymizer;
    private CivicaLlmAdapter fallback;
    private ObjectMapper objectMapper;
    private GeminiLlmAdapter adapter;

    private Dossier dossierCni;

    @BeforeEach
    void setUp() {
        piiAnonymizer = new PiiAnonymizer();
        fallback = new CivicaLlmAdapter();
        objectMapper = new ObjectMapper();
        adapter = new GeminiLlmAdapter(geminiRestClient, fallback, piiAnonymizer, objectMapper);

        Utilisateur citoyen = Utilisateur.builder()
                .id(10L)
                .nom("Kamga")
                .prenom("Arnaud")
                .email("arnaud.kamga@civica.cm")
                .telephone("+237677889900")
                .build();

        Demarche demCni = Demarche.builder()
                .id(1L)
                .code("DEM-CNI")
                .titre("Délivrance de la Carte Nationale d'Identité (CNI)")
                .build();

        dossierCni = Dossier.builder()
                .id(50L)
                .numeroSuivi("CIVICA-2026-11-15-DOS-009")
                .citoyen(citoyen)
                .demarche(demCni)
                .pieces(List.of(
                        PieceJustificative.builder()
                                .id(1L)
                                .intitule("Acte de naissance Arnaud Kamga")
                                .typeMime("application/pdf")
                                .tailleOctets(102400L)
                                .build()
                ))
                .build();
    }

    @Test
    void testAnonymiseurSupprimeToutesDonneesPersonnelles() {
        String raw = "Dossier CIVICA-2026-11-15-DOS-009 de Arnaud Kamga (+237677889900, arnaud.kamga@civica.cm) compte CM21-10005-00001-32145678901-88";
        String sanitized = piiAnonymizer.sanitizeText(raw, dossierCni);

        assertFalse(sanitized.contains("CIVICA-2026-11-15-DOS-009"));
        assertFalse(sanitized.contains("Arnaud"));
        assertFalse(sanitized.contains("Kamga"));
        assertFalse(sanitized.contains("677889900"));
        assertFalse(sanitized.contains("arnaud.kamga@civica.cm"));
        assertFalse(sanitized.contains("32145678901"));
        assertTrue(sanitized.contains("[DOSSIER_ANONYME]"));
        assertTrue(sanitized.contains("[USAGER_ANONYME]"));
    }

    @Test
    void testAuditGeminiJsonStructuréValide() {
        when(geminiRestClient.isConfigured()).thenReturn(true);
        String validJson = """
                {
                  "complet": false,
                  "score_completude": 33,
                  "pieces_detectees": ["Copie certifiée conforme d'acte de naissance"],
                  "pieces_manquantes": ["Certificat de nationalité camerounaise", "Justificatif de profession ou diplôme"],
                  "recommandations": ["Réclamer le certificat de nationalité"],
                  "synthese_analyse": "1 pièce sur 3 détectée.",
                  "avis_ia": "COMPLEMENT_REQUIS",
                  "niveau_confiance": "indicatif"
                }
                """;
        when(geminiRestClient.generateJson(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiRestClient.GeminiGenerationResult(validJson, "gemini-3.5-flash-lite", 410L)));

        AuditCompletudeDto result = adapter.auditerDossier(dossierCni);

        assertEquals("GEMINI_REST", result.getSourceProvider());
        assertEquals("gemini-3.5-flash-lite", result.getModeleUtilise());
        assertFalse(result.isModeDegrade());
        assertEquals(33, result.getScoreCompletude());
        assertEquals("COMPLEMENT_REQUIS", result.getAvisIa());
        assertEquals(2, result.getPiecesManquantes().size());
        verify(geminiRestClient).generateJson(anyString(), argThat(prompt -> !prompt.contains("Arnaud")));
    }

    @Test
    void testRepliDeterministeAutomatiqueSiGeminiMalformeOuIndisponible() {
        when(geminiRestClient.isConfigured()).thenReturn(true);
        when(geminiRestClient.generateJson(anyString(), anyString()))
                .thenReturn(Optional.of(new GeminiRestClient.GeminiGenerationResult("{not valid json", "gemini-3.5-flash-lite", 120L)));

        AuditCompletudeDto result = adapter.auditerDossier(dossierCni);

        assertEquals("FALLBACK_DETERMINISTE", result.getSourceProvider());
        assertTrue(result.isModeDegrade());
        assertFalse(result.isComplet());
        assertTrue(result.getPiecesManquantes().contains("Certificat de nationalité camerounaise"));
    }

    @Test
    void testChatRepliSansCleGemini() {
        when(geminiRestClient.isConfigured()).thenReturn(false);

        CivicaIaChatResponse resp = adapter.repondreAssistant(
                "Quelles pièces pour la CNI ?",
                "Citoyen",
                "",
                "Accueil",
                List.of(),
                null,
                null
        );

        assertTrue(resp.isModeDegrade());
        assertEquals("FALLBACK_DETERMINISTE", resp.getSourceProvider());
        assertTrue(resp.getReponse().contains("10 000 FCFA"));
    }

    @Test
    void testRepliAccueilSansTermesTechniquesNiGeminiFreeNiQuatreYeux() {
        when(geminiRestClient.isConfigured()).thenReturn(false);

        CivicaIaChatResponse resp = adapter.repondreAssistant(
                "Bonjour",
                "Citoyen",
                "",
                "Accueil",
                List.of(),
                null,
                null
        );

        assertTrue(resp.isModeDegrade());
        assertFalse(resp.getReponse().toLowerCase().contains("gemini free"));
        assertFalse(resp.getReponse().toLowerCase().contains("gemini gratuit"));
        assertFalse(resp.getReponse().toLowerCase().contains("4 yeux"));
        assertFalse(resp.getReponse().toLowerCase().contains("quatre yeux"));
    }
}

