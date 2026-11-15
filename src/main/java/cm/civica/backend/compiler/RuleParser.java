package cm.civica.backend.compiler;

import cm.civica.backend.exception.BusinessException;

import java.util.List;

/**
 * Analyseur syntaxique par descente récursive (Recursive Descent Parser)
 * implémentant strictement la grammaire formelle EBNF (Livrable 30) :
 *
 *   condition   = disjonction ;
 *   disjonction = conjonction , { "or" , conjonction } ;
 *   conjonction = comparaison , { "and" , comparaison } ;
 *   comparaison = terme , [ operateur , terme ] ;
 *   operateur   = "<" | "<=" | ">" | ">=" | "==" | "!=" ;
 *   terme       = NOMBRE | BOOLEEN | CHAINE | IDENTIFIANT | "(" , condition , ")" ;
 */
public class RuleParser {

    private final List<Token> tokens;
    private int current = 0;

    public RuleParser(List<Token> tokens) {
        this.tokens = tokens;
    }

    public AstNode parse() {
        AstNode root = parseDisjonction();
        if (peek().type() != TokenType.EOF) {
            throw BusinessException.badRequest("Erreur syntaxique EBNF : jeton inattendu '" + peek().lexeme()
                    + "' à la position " + peek().position());
        }
        return root;
    }

    // disjonction = conjonction , { "or" , conjonction } ;
    private AstNode parseDisjonction() {
        AstNode node = parseConjonction();
        while (match(TokenType.OR)) {
            AstNode right = parseConjonction();
            node = new AstNode.LogicalNode(TokenType.OR, node, right);
        }
        return node;
    }

    // conjonction = comparaison , { "and" , comparaison } ;
    private AstNode parseConjonction() {
        AstNode node = parseComparaison();
        while (match(TokenType.AND)) {
            AstNode right = parseComparaison();
            node = new AstNode.LogicalNode(TokenType.AND, node, right);
        }
        return node;
    }

    // comparaison = terme , [ operateur , terme ] ;
    private AstNode parseComparaison() {
        AstNode left = parseTerme();
        if (isComparisonOperator(peek().type())) {
            Token op = advance();
            AstNode right = parseTerme();
            return new AstNode.ComparisonNode(op.type(), left, right);
        }
        return left;
    }

    // terme = NOMBRE | BOOLEEN | CHAINE | IDENTIFIANT | "(" , condition , ")" ;
    private AstNode parseTerme() {
        Token token = peek();
        switch (token.type()) {
            case NUMBER -> {
                advance();
                return new AstNode.NumberLiteralNode(Double.parseDouble(token.lexeme()));
            }
            case BOOLEAN -> {
                advance();
                return new AstNode.BooleanLiteralNode(Boolean.parseBoolean(token.lexeme()));
            }
            case STRING -> {
                advance();
                return new AstNode.StringLiteralNode(token.lexeme());
            }
            case IDENTIFIER -> {
                advance();
                return new AstNode.IdentifierNode(token.lexeme());
            }
            case LPAREN -> {
                advance(); // consommer '('
                AstNode inner = parseDisjonction();
                if (!match(TokenType.RPAREN)) {
                    throw BusinessException.badRequest("Erreur syntaxique EBNF : parenthèse fermante ')' attendue à la position " + peek().position());
                }
                return inner;
            }
            default -> throw BusinessException.badRequest(
                    "Erreur syntaxique EBNF : terme valide attendu mais trouvé '" + token.lexeme() + "' à la position " + token.position());
        }
    }

    private boolean isComparisonOperator(TokenType type) {
        return type == TokenType.LT || type == TokenType.LE
                || type == TokenType.GT || type == TokenType.GE
                || type == TokenType.EQ || type == TokenType.NEQ;
    }

    private boolean match(TokenType expected) {
        if (peek().type() == expected) {
            advance();
            return true;
        }
        return false;
    }

    private Token peek() {
        return tokens.get(current);
    }

    private Token advance() {
        if (current < tokens.size() - 1) {
            current++;
        }
        return tokens.get(current - 1);
    }
}
