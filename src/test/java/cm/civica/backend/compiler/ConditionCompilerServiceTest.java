package cm.civica.backend.compiler;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Suite de tests unitaires JUnit 5 vérifiant le compilateur EBNF
 * (RuleLexer -> RuleParser -> AstEvaluator) et les 3 circuits JSON
 * de l'acte de naissance (Livrables 30 & 31).
 */
class ConditionCompilerServiceTest {

    private final ConditionCompilerService compilerService = new ConditionCompilerService();

    @Test
    void testCircuitNormaleMoinsDe90Jours() {
        Map<String, Object> res = compilerService.determinerCircuitActeNaissance(Map.of("jours_depuis_naissance", 45));
        assertEquals("NORMALE", res.get("varianteSelectionnee"));
        assertEquals(0, res.get("fraisFcfa"));
    }

    @Test
    void testCircuitTardiveEntre91Et365Jours() {
        Map<String, Object> res = compilerService.determinerCircuitActeNaissance(Map.of("jours_depuis_naissance", 180));
        assertEquals("TARDIVE", res.get("varianteSelectionnee"));
        assertEquals(1500, res.get("fraisFcfa"));
    }

    @Test
    void testCircuitJudiciairePlusDe365Jours() {
        Map<String, Object> res = compilerService.determinerCircuitActeNaissance(Map.of("jours_depuis_naissance", 720));
        assertEquals("JUDICIAIRE", res.get("varianteSelectionnee"));
        assertEquals(5000, res.get("fraisFcfa"));
    }

    @Test
    void testExpressionsLogiquesComplexesEtParentheses() {
        boolean eval1 = compilerService.evaluerCondition(
                "(jours <= 90 or urgence == true) and pays == 'CAMEROUN'",
                Map.of("jours", 120, "urgence", true, "pays", "CAMEROUN")
        );
        assertTrue(eval1);

        boolean eval2 = compilerService.evaluerCondition(
                "90 < jours and jours <= 365",
                Map.of("jours", 50)
        );
        assertFalse(eval2);
    }
}
