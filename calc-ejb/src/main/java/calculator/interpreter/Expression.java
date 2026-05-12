package calculator.interpreter;

import java.util.Map;

public interface Expression {
    double interpret(Map<String, Expression> variables);
}
