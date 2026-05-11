package hissab.ejb;

import jakarta.ejb.Local;

/**
 * Interface locale du composant CALC (EJB #1).
 * @Local = même JVM, plus efficace que @Remote pour un WAR unique.
 */
@Local
public interface ICalcLocal {

    /**
     * Évalue une expression arithmétique infixe.
     * Exemples : "5 + 2 * 6 - 3" → 34.0
     *            "10 / 2 + 3"    → 8.0
     *
     * @param expression expression sous forme de chaîne infixe
     * @return résultat numérique
     * @throws IllegalArgumentException si l'expression est invalide
     */
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
