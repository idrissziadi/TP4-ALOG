package hissab.ejb;

import jakarta.ejb.Stateless;
import net.objecthunter.exp4j.Expression;
import net.objecthunter.exp4j.ExpressionBuilder;

/**
 * EJB #1 — Composant CALC.
 * Évalue des expressions arithmétiques infixes avec la bonne priorité des opérateurs.
 *
 * Utilise exp4j au lieu de l'Evaluator postfixe du TD car OCR produit
 * des expressions infixes (ex: "5 + 2 * 6 - 3"), incompatibles avec la
 * notation postfixe de l'Evaluator original.
 */
@Stateless
public class CalcEJB implements ICalcLocal {

    @Override
    public double evaluerExpression(String expressionStr) throws IllegalArgumentException {
        if (expressionStr == null || expressionStr.isBlank()) {
            throw new IllegalArgumentException("L'expression ne peut pas être vide");
        }

        // Normalisation : remplacer les symboles Unicode par leurs équivalents ASCII
        String normalized = expressionStr
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace("²", "^2")
                .replaceAll("\\s+", " ")
                .trim();

        try {
            Expression expression = new ExpressionBuilder(normalized).build();

            if (!expression.validate().isValid()) {
                throw new IllegalArgumentException("Expression invalide : " + expressionStr);
            }

            double result = expression.evaluate();

            if (Double.isNaN(result) || Double.isInfinite(result)) {
                throw new IllegalArgumentException("Résultat invalide (division par zéro ?)");
            }

            return result;

        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("Impossible d'évaluer : " + expressionStr, e);
        }
    }
}
