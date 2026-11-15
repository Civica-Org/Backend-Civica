package cm.civica.backend.compiler;

import cm.civica.backend.exception.BusinessException;

import java.util.ArrayList;
import java.util.List;

/**
 * Analyseur lexical (Lexer) du mini-langage de conditions EBNF (Livrable 30).
 * Transforme une expression textuelle en une séquence typée de jetons (Tokens)
 * tout en rejetant tout caractère non autorisé (prévention d'injection).
 */
public class RuleLexer {

    private final String source;
    private final int length;
    private int pos = 0;

    public RuleLexer(String source) {
        this.source = source != null ? source : "";
        this.length = this.source.length();
    }

    public List<Token> tokenize() {
        List<Token> tokens = new ArrayList<>();

        while (pos < length) {
            char c = source.charAt(pos);

            // Ignorer les espaces blancs
            if (Character.isWhitespace(c)) {
                pos++;
                continue;
            }

            int startPos = pos;

            // Parenthèses
            if (c == '(') {
                tokens.add(new Token(TokenType.LPAREN, "(", startPos));
                pos++;
                continue;
            }
            if (c == ')') {
                tokens.add(new Token(TokenType.RPAREN, ")", startPos));
                pos++;
                continue;
            }

            // Opérateurs de comparaison (<=, >=, ==, !=, <, >)
            if (c == '<') {
                if (peekNext() == '=') {
                    tokens.add(new Token(TokenType.LE, "<=", startPos));
                    pos += 2;
                } else {
                    tokens.add(new Token(TokenType.LT, "<", startPos));
                    pos++;
                }
                continue;
            }
            if (c == '>') {
                if (peekNext() == '=') {
                    tokens.add(new Token(TokenType.GE, ">=", startPos));
                    pos += 2;
                } else {
                    tokens.add(new Token(TokenType.GT, ">", startPos));
                    pos++;
                }
                continue;
            }
            if (c == '=' && peekNext() == '=') {
                tokens.add(new Token(TokenType.EQ, "==", startPos));
                pos += 2;
                continue;
            }
            if (c == '!' && peekNext() == '=') {
                tokens.add(new Token(TokenType.NEQ, "!=", startPos));
                pos += 2;
                continue;
            }

            // Littéraux chaînes ('...' ou "...")
            if (c == '\'' || c == '"') {
                char quote = c;
                pos++;
                StringBuilder sb = new StringBuilder();
                while (pos < length && source.charAt(pos) != quote) {
                    sb.append(source.charAt(pos));
                    pos++;
                }
                if (pos >= length) {
                    throw BusinessException.badRequest("Erreur lexicale EBNF : chaîne non fermée à la position " + startPos);
                }
                pos++; // consommer le guillemet fermant
                tokens.add(new Token(TokenType.STRING, sb.toString(), startPos));
                continue;
            }

            // Nombres (entiers ou décimaux, éventuellement négatifs)
            if (Character.isDigit(c) || (c == '-' && Character.isDigit(peekNext()))) {
                StringBuilder sb = new StringBuilder();
                sb.append(c);
                pos++;
                while (pos < length && (Character.isDigit(source.charAt(pos)) || source.charAt(pos) == '.')) {
                    sb.append(source.charAt(pos));
                    pos++;
                }
                tokens.add(new Token(TokenType.NUMBER, sb.toString(), startPos));
                continue;
            }

            // Identifiants et mots-clés réservés (and, or, true, false)
            if (Character.isLetter(c) || c == '_') {
                StringBuilder sb = new StringBuilder();
                while (pos < length && (Character.isLetterOrDigit(source.charAt(pos)) || source.charAt(pos) == '_')) {
                    sb.append(source.charAt(pos));
                    pos++;
                }
                String word = sb.toString();
                switch (word.toLowerCase()) {
                    case "and" -> tokens.add(new Token(TokenType.AND, word, startPos));
                    case "or" -> tokens.add(new Token(TokenType.OR, word, startPos));
                    case "true", "false" -> tokens.add(new Token(TokenType.BOOLEAN, word.toLowerCase(), startPos));
                    default -> tokens.add(new Token(TokenType.IDENTIFIER, word, startPos));
                }
                continue;
            }

            throw BusinessException.badRequest("Erreur lexicale EBNF : caractère illégal '" + c + "' à la position " + startPos);
        }

        tokens.add(new Token(TokenType.EOF, "", pos));
        return tokens;
    }

    private char peekNext() {
        return (pos + 1 < length) ? source.charAt(pos + 1) : '\0';
    }
}
