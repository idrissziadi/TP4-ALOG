package hissab.ejb;

import calculator.ICalculator;

import jakarta.ejb.EJB;
import jakarta.ejb.Stateless;
import java.util.Stack;

/**
 * EJB local HISSAB — orchestre l'évaluation d'une expression infix complète.
 *
 * Pipeline :
 *   1. Normalisation Unicode → ASCII
 *   2. InfixToPostfixConverter (Shunting-Yard)
 *   3. Évaluation postfixe token par token via @EJB ICalculator (composant TD2)
 */
@Stateless
public class HissabExpressionEJB implements IHissabExpressionLocal {

    @EJB
    private ICalculator calc;

    private static final InfixToPostfixConverter CONVERTER = new InfixToPostfixConverter();

    @Override
    public double evaluerExpression(String expressionStr) throws IllegalArgumentException {
        if (expressionStr == null || expressionStr.isBlank())
            throw new IllegalArgumentException("L'expression ne peut pas être vide");

        String normalized = expressionStr
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace("²", "")
                .replaceAll("\\s+", " ")
                .trim();

        try {
            String postfix = CONVERTER.convert(normalized);
            double result  = evaluerPostfixe(postfix);

            if (Double.isNaN(result) || Double.isInfinite(result))
                throw new IllegalArgumentException("Résultat invalide (division par zéro ?)");

            return result;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossible d'évaluer : " + expressionStr, e);
        }
    }

    private double evaluerPostfixe(String postfix) {
        Stack<Double> stack = new Stack<>();
        for (String token : postfix.trim().split("\\s+")) {
            switch (token) {
                case "+" -> { double r = stack.pop(), l = stack.pop(); stack.push(calc.sum(l, r)); }
                case "-" -> { double r = stack.pop(), l = stack.pop(); stack.push(calc.minus(l, r)); }
                case "*" -> { double r = stack.pop(), l = stack.pop(); stack.push(calc.product(l, r)); }
                case "/" -> { double r = stack.pop(), l = stack.pop(); stack.push(calc.divide(l, r)); }
                default  -> stack.push(Double.parseDouble(token));
            }
        }
        if (stack.size() != 1)
            throw new IllegalArgumentException("Expression malformée");
        return stack.pop();
    }
}
