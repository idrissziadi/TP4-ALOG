package hissab.ejb;

import calculator.interpreter.Evaluator;
import calculator.interpreter.InfixToPostfixConverter;

import jakarta.ejb.Stateless;
import java.util.Collections;

/**
 * EJB #1 — Composant CALC.
 *
 * Transforme la calculatrice du TD2 (pattern Interpreter, notation postfixe)
 * en composant JEE réutilisable @Remote.
 *
 * Pipeline : infixe → normalisation → postfixe (Shunting-Yard) → Evaluator (Interpreter TD2)
 */
@Stateless
public class CalcEJB implements ICalcRemote {

    private static final InfixToPostfixConverter CONVERTER = new InfixToPostfixConverter();

    @Override
    public double evaluerExpression(String expressionStr) throws IllegalArgumentException {
        if (expressionStr == null || expressionStr.isBlank())
            throw new IllegalArgumentException("L'expression ne peut pas être vide");

        // 1. Normalisation des symboles Unicode vers ASCII
        String normalized = expressionStr
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace("²", "")
                .replaceAll("\\s+", " ")
                .trim();

        try {
            // 2. Conversion infixe → postfixe (Shunting-Yard)
            String postfix = CONVERTER.convert(normalized);

            // 3. Évaluation via pattern Interpreter du TD2 (étendu : + - * /)
            Evaluator evaluator = new Evaluator(postfix);
            double result = evaluator.interpret(Collections.emptyMap());

            if (Double.isNaN(result) || Double.isInfinite(result))
                throw new IllegalArgumentException("Résultat invalide (division par zéro ?)");

            return result;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (ArithmeticException e) {
            throw new IllegalArgumentException(e.getMessage(), e);
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossible d'évaluer : " + expressionStr, e);
        }
    }
}
