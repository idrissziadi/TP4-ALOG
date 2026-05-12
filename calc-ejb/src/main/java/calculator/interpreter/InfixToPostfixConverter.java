package calculator.interpreter;

import java.util.ArrayList;
import java.util.List;
import java.util.Stack;

/**
 * Convertit une expression infixe en notation postfixe (RPN).
 * Algorithme : Shunting-Yard de Dijkstra.
 *
 * Exemples :
 *   "5 + 2 * 6 - 3"  →  "5 2 6 * + 3 -"
 *   "(5 + 2) * 6"    →  "5 2 + 6 *"
 *   "12 + 3"         →  "12 3 +"
 */
public class InfixToPostfixConverter {

    private int priority(String op) {
        return (op.equals("*") || op.equals("/")) ? 2 : 1;
    }

    private boolean isOperator(String s) {
        return s.equals("+") || s.equals("-") || s.equals("*") || s.equals("/");
    }

    /**
     * Convertit une expression infixe en postfixe.
     *
     * @param infix expression infixe normalisée (opérateurs ASCII, espaces optionnels)
     * @return expression postfixe avec tokens séparés par des espaces
     * @throws IllegalArgumentException si l'expression contient un token inconnu
     *                                  ou des parenthèses non équilibrées
     */
    public String convert(String infix) {
        List<String> output  = new ArrayList<>();
        Stack<String> opStack = new Stack<>();

        List<String> tokens = tokenize(infix);

        for (String token : tokens) {
            if (token.matches("-?\\d+(\\.\\d+)?")) {
                output.add(token);
            } else if (isOperator(token)) {
                while (!opStack.isEmpty()
                        && isOperator(opStack.peek())
                        && priority(opStack.peek()) >= priority(token)) {
                    output.add(opStack.pop());
                }
                opStack.push(token);
            } else if (token.equals("(")) {
                opStack.push(token);
            } else if (token.equals(")")) {
                while (!opStack.isEmpty() && !opStack.peek().equals("(")) {
                    output.add(opStack.pop());
                }
                if (opStack.isEmpty())
                    throw new IllegalArgumentException("Parenthèse fermante sans ouvrante");
                opStack.pop();
            } else {
                throw new IllegalArgumentException("Token inconnu : " + token);
            }
        }

        while (!opStack.isEmpty()) {
            String op = opStack.pop();
            if (op.equals("("))
                throw new IllegalArgumentException("Parenthèse ouvrante non fermée");
            output.add(op);
        }

        return String.join(" ", output);
    }

    private List<String> tokenize(String infix) {
        List<String> tokens = new ArrayList<>();
        StringBuilder num   = new StringBuilder();

        for (char c : infix.toCharArray()) {
            if (Character.isDigit(c) || c == '.') {
                num.append(c);
            } else {
                if (num.length() > 0) {
                    tokens.add(num.toString());
                    num.setLength(0);
                }
                if (c != ' ') {
                    tokens.add(String.valueOf(c));
                }
            }
        }
        if (num.length() > 0) {
            tokens.add(num.toString());
        }
        return tokens;
    }
}
