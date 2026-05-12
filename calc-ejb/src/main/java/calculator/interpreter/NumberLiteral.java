package calculator.interpreter;

import java.util.Map;

public class NumberLiteral implements Expression {
    private final double number;

    public NumberLiteral(double number) {
        this.number = number;
    }

    @Override
    public double interpret(Map<String, Expression> variables) {
        return number;
    }
}
