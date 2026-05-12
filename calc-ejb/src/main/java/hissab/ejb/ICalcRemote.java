package hissab.ejb;

import jakarta.ejb.Remote;

/**
 * Interface @Remote du composant CALC (EJB #1).
 *
 * @Remote permet l'accès depuis n'importe quelle application
 * déployée sur le même GlassFish — c'est ce qui rend le composant
 * réutilisable entre projets différents.
 */
@Remote
public interface ICalcRemote {

    /**
     * Évalue une expression arithmétique infixe.
     *
     * Exemples : "5 + 2 * 6 - 3" → 14.0
     *            "10 / 4"         → 2.5
     *            "(5 + 2) * 6"    → 42.0
     *
     * @param expression expression infixe (opérateurs : +, -, *, /, parenthèses)
     * @return résultat numérique (double)
     * @throws IllegalArgumentException si l'expression est invalide
     */
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
