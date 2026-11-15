package cm.civica.backend.compiler;

import cm.civica.backend.exception.BusinessException;

import java.util.Map;
import java.util.Objects;

/**
 * Évaluateur sémantique dénotationnel de l'AST (Livrable 30).
 * Évalue de manière déterministe et sans réflexion un arbre syntaxique
 * par rapport au contexte de réponses du citoyen.
 */
public class AstEvaluator {

    public boolean evaluateCondition(AstNode root, Map<String, Object> context) {
        Object result = eval(root, context != null ? context : Map.of());
        if (result instanceof Boolean b) {
            return b;
        }
        throw BusinessException.badRequest("Erreur sémantique EBNF : l'expression ne produit pas une valeur booléenne.");
    }

    private Object eval(AstNode node, Map<String, Object> context) {
        return switch (node) {
            case AstNode.NumberLiteralNode n -> n.value();
            case AstNode.BooleanLiteralNode b -> b.value();
            case AstNode.StringLiteralNode s -> s.value();
            case AstNode.IdentifierNode id -> {
                if (!context.containsKey(id.name())) {
                    throw BusinessException.badRequest("Variable manquante dans le questionnaire citoyen : '" + id.name() + "'");
                }
                yield context.get(id.name());
            }
            case AstNode.LogicalNode log -> {
                boolean leftVal = toBoolean(eval(log.left(), context));
                if (log.operator() == TokenType.AND) {
                    yield leftVal && toBoolean(eval(log.right(), context));
                } else if (log.operator() == TokenType.OR) {
                    yield leftVal || toBoolean(eval(log.right(), context));
                }
                throw BusinessException.badRequest("Opérateur logique inconnu : " + log.operator());
            }
            case AstNode.ComparisonNode cmp -> {
                Object leftVal = eval(cmp.left(), context);
                Object rightVal = eval(cmp.right(), context);
                yield compare(cmp.operator(), leftVal, rightVal);
            }
        };
    }

    private boolean compare(TokenType op, Object left, Object right) {
        if (left instanceof Number lNum && right instanceof Number rNum) {
            double l = lNum.doubleValue();
            double r = rNum.doubleValue();
            return switch (op) {
                case LT -> l < r;
                case LE -> l <= r;
                case GT -> l > r;
                case GE -> l >= r;
                case EQ -> Double.compare(l, r) == 0;
                case NEQ -> Double.compare(l, r) != 0;
                default -> false;
            };
        }

        // Comparaison d'égalité sur booléens ou chaînes
        String lStr = String.valueOf(left);
        String rStr = String.valueOf(right);
        return switch (op) {
            case EQ -> Objects.equals(lStr, rStr);
            case NEQ -> !Objects.equals(lStr, rStr);
            default -> throw BusinessException.badRequest(
                    "Comparaison relationnelle '" + op + "' applicable uniquement aux nombres (reçu: " + left + ", " + right + ")");
        };
    }

    private boolean toBoolean(Object val) {
        if (val instanceof Boolean b) return b;
        if (val instanceof String s) return Boolean.parseBoolean(s);
        throw BusinessException.badRequest("Valeur booléenne attendue mais reçu : " + val);
    }
}
