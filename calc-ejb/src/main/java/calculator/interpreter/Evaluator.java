package calculator.interpreter;

import java.util.Map;
import java.util.Stack;

/**
 * Évalue une expression en notation postfixe (ex: "5 2 6 * + 3 -").
 *
 * Étendu par rapport au TD2 :
 *   - Supporte * et / (en plus de + et -)
 *   - Supporte les literals numériques directement ("5", "3.14")
 *     sans avoir à les passer dans un Map<String, Expression>
 */
public class Evaluator implements Expression {

    private final Expression syntaxTree;

    public Evaluator(String postfixExpression) {
        Stack<Expression> stack = new Stack<>();

        for (String token : postfixExpression.trim().split("\\s+")) {
            switch (token) {
                case "+": {
                    Expression right = stack.pop();
                    Expression left  = stack.pop();
                    stack.push(new Plus(left, right));
                    break;
                }
                case "-": {
                    Expression right = stack.pop();
                    Expression left  = stack.pop();
                    stack.push(new Minus(left, right));
                    break;
                }
                case "*": {
                    Expression right = stack.pop();
                    Expression left  = stack.pop();
                    stack.push(new Multiply(left, right));
                    break;
                }
                case "/": {
                    Expression right = stack.pop();
                    Expression left  = stack.pop();
                    stack.push(new Divide(left, right));
                    break;
                }
                default: {
                    try {
                        stack.push(new NumberLiteral(Double.parseDouble(token)));
                    } catch (NumberFormatException e) {
                        stack.push(new Variable(token));
                    }
                    break;
                }
            }
        }

        syntaxTree = stack.pop();
    }

    @Override
    public double interpret(Map<String, Expression> context) {
        return syntaxTree.interpret(context);
    }
}
