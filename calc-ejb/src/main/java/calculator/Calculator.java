package calculator;

import calculator.interpreter.Evaluator;
import calculator.interpreter.Expression;
import calculator.interpreter.NumberLiteral;

import java.util.Collections;
import java.util.Map;

/**
 * Composant CALCULATEUR (TD2).
 * Réalise les opérations arithmétiques élémentaires.
 * Réutilise le pattern Interpreter (TD2) via l'Evaluator étendu.
 */
public class Calculator implements ICalculator {

    @Override
    public int sum(int a, int b) {
        Map<String, Expression> ctx = Map.of(
            "a", new NumberLiteral(a),
            "b", new NumberLiteral(b)
        );
        Evaluator eval = new Evaluator("a b +");
        return (int) eval.interpret(ctx);
    }

    @Override
    public int product(int a, int b) {
        return a * b;
    }

    @Override
    public long factorial(int n) {
        if (n < 0) throw new IllegalArgumentException("n doit être >= 0");
        long result = 1;
        for (int i = 2; i <= n; i++) result *= i;
        return result;
    }
}
