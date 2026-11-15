package cm.civica.backend.compiler;

/**
 * Jeton lexical immuable produit par l'analyseur lexical RuleLexer.
 */
public record Token(TokenType type, String lexeme, int position) {

    @Override
    public String toString() {
        return type + "(" + lexeme + "@" + position + ")";
    }
}
