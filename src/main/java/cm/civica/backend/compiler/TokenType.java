package cm.civica.backend.compiler;

/**
 * Types de jetons lexicaux (Tokens) de la grammaire formelle EBNF
 * du mini-langage de sélection des variantes de démarches (Livrable 30).
 */
public enum TokenType {
    NUMBER,         // Littéral numérique (ex: 90, 365)
    BOOLEAN,        // Littéral booléen (true, false)
    STRING,         // Littéral chaîne ('CAMEROUN')
    IDENTIFIER,     // Variable du contexte citoyen (ex: jours_depuis_naissance)
    AND,            // Opérateur logique conjonctif ("and")
    OR,             // Opérateur logique disjonctif ("or")
    LT,             // <
    LE,             // <=
    GT,             // >
    GE,             // >=
    EQ,             // ==
    NEQ,            // !=
    LPAREN,         // (
    RPAREN,         // )
    EOF             // Fin de flux
}
