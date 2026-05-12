package hissab.ejb;

import jakarta.ejb.Remote;

/**
 * Interface @Remote du composant CALC.
 * Copie de l'interface définie dans calc-ejb — partagée via le JAR client.
 */
@Remote
public interface ICalcRemote {
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
