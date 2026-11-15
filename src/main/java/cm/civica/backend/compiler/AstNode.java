package cm.civica.backend.compiler;

/**
 * Hiérarchie de l'Arbre Syntaxique Abstrait (AST — Abstract Syntax Tree)
 * du mini-langage de conditions EBNF (Java 21 sealed interfaces & records).
 */
public sealed interface AstNode permits
        AstNode.LogicalNode,
        AstNode.ComparisonNode,
        AstNode.NumberLiteralNode,
        AstNode.BooleanLiteralNode,
        AstNode.StringLiteralNode,
        AstNode.IdentifierNode {

    /** Nœud logique binaire (AND / OR) */
    record LogicalNode(TokenType operator, AstNode left, AstNode right) implements AstNode {}

    /** Nœud de comparaison relationnelle (<, <=, >, >=, ==, !=) */
    record ComparisonNode(TokenType operator, AstNode left, AstNode right) implements AstNode {}

    /** Littéral numérique */
    record NumberLiteralNode(double value) implements AstNode {}

    /** Littéral booléen */
    record BooleanLiteralNode(boolean value) implements AstNode {}

    /** Littéral chaîne de caractères */
    record StringLiteralNode(String value) implements AstNode {}

    /** Identifiant de variable du contexte citoyen */
    record IdentifierNode(String name) implements AstNode {}
}
