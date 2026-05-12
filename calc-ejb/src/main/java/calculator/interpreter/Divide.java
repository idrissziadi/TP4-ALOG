package calculator.interpreter;

import java.util.Map;

public class Divide implements Expression {
    private final Expression leftOperand;
    private final Expression rightOperand;

    public Divide(Expression left, Expression right) {
        leftOperand  = left;
        rightOperand = right;
    }

    @Override
    public double interpret(Map<String, Expression> variables) {
        double diviseur = rightOperand.interpret(variables);
        if (diviseur == 0.0)
            throw new ArithmeticException("Division par zéro");
        return leftOperand.interpret(variables) / diviseur;
    }
}
