package calculator.interpreter;

import java.util.Map;

public class Minus implements Expression {
    private final Expression leftOperand;
    private final Expression rightOperand;

    public Minus(Expression left, Expression right) {
        leftOperand  = left;
        rightOperand = right;
    }

    @Override
    public double interpret(Map<String, Expression> variables) {
        return leftOperand.interpret(variables) - rightOperand.interpret(variables);
    }
}
