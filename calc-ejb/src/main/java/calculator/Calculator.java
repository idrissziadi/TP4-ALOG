package calculator;

import calculator.interpreter.Evaluator;
import calculator.interpreter.Expression;
import calculator.interpreter.NumberLiteral;

import jakarta.ejb.Stateless;
import java.util.Collections;
import java.util.Map;

@Stateless
public class Calculator implements ICalculator {

    @Override
    public double sum(double a, double b) {
        Map<String, Expression> ctx = Map.of(
            "a", new NumberLiteral(a),
            "b", new NumberLiteral(b)
        );
        return new Evaluator("a b +").interpret(ctx);
    }

    @Override
    public double minus(double a, double b) {
        Map<String, Expression> ctx = Map.of(
            "a", new NumberLiteral(a),
            "b", new NumberLiteral(b)
        );
        return new Evaluator("a b -").interpret(ctx);
    }

    @Override
    public double product(double a, double b) {
        Map<String, Expression> ctx = Map.of(
            "a", new NumberLiteral(a),
            "b", new NumberLiteral(b)
        );
        return new Evaluator("a b *").interpret(ctx);
    }

    @Override
    public double divide(double a, double b) {
        Map<String, Expression> ctx = Map.of(
            "a", new NumberLiteral(a),
            "b", new NumberLiteral(b)
        );
        return new Evaluator("a b /").interpret(ctx);
    }

    @Override
    public long factorial(int n) {
        if (n < 0) throw new IllegalArgumentException("n doit être >= 0");
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }
}
