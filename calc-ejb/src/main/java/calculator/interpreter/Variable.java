package calculator.interpreter;

import java.util.Map;

public class Variable implements Expression {
    private final String name;

    public Variable(String name) {
        this.name = name;
    }

    @Override
    public double interpret(Map<String, Expression> variables) {
        Expression expr = variables.get(name);
        if (expr == null) return 0;
        return expr.interpret(variables);
    }
}
