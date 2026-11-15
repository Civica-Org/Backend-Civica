package cm.civica.backend.compiler;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.util.*;

/**
 * Service façonnant la chaîne complète de compilation et d'évaluation
 * du mini-langage EBNF (Lexer -> Parser -> AST -> Evaluator) et sélectionnant
 * automatiquement le circuit d'instruction (Normale, Tardive, Judiciaire)
 * à partir du fichier déclaratif JSON (Livrables 30 & 31).
 */
@Service
public class ConditionCompilerService {

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AstEvaluator evaluator = new AstEvaluator();

    /**
     * Compile une condition EBNF en AST et l'évalue sur le contexte fourni.
     */
    public boolean evaluerCondition(String expressionEbnf, Map<String, Object> contexte) {
        RuleLexer lexer = new RuleLexer(expressionEbnf);
        List<Token> tokens = lexer.tokenize();
        RuleParser parser = new RuleParser(tokens);
        AstNode ast = parser.parse();
        return evaluator.evaluateCondition(ast, contexte);
    }

    /**
     * Charge la configuration JSON des trois circuits de l'acte de naissance
     * et détermine la variante applicable selon les réponses du citoyen.
     */
    public Map<String, Object> determinerCircuitActeNaissance(Map<String, Object> reponsesCitoyen) {
        try {
            ClassPathResource resource = new ClassPathResource("workflows/acte_naissance_circuits.json");
            try (InputStream is = resource.getInputStream()) {
                JsonNode root = objectMapper.readTree(is);
                JsonNode variants = root.get("variants");

                for (JsonNode variant : variants) {
                    String code = variant.get("code").asText();
                    String conditionEbnf = variant.get("condition").asText();

                    // Normalisation du contexte avec naissance_cameroun = true par défaut
                    Map<String, Object> ctx = new HashMap<>(reponsesCitoyen);
                    ctx.putIfAbsent("naissance_cameroun", true);

                    if (evaluerCondition(conditionEbnf, ctx)) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("procedure", root.get("procedure").asText());
                        result.put("varianteSelectionnee", code);
                        result.put("libelle", variant.get("libelle").asText());
                        result.put("conditionEbnfVerifiee", conditionEbnf);
                        result.put("fraisFcfa", variant.get("fraisFcfa").asInt());
                        result.put("delaiIndicatifJours", variant.get("delaiIndicatifJours").asInt());
                        result.put("piecesRequises", objectMapper.convertValue(variant.get("piecesRequises"), List.class));
                        result.put("workflow", objectMapper.convertValue(variant.get("workflow"), List.class));
                        return result;
                    }
                }
            }
        } catch (Exception e) {
            throw new IllegalStateException("Erreur lors de l'évaluation des circuits d'acte de naissance : " + e.getMessage(), e);
        }

        return Map.of("erreur", "Aucune variante éligible trouvée pour les paramètres fournis.");
    }
}
