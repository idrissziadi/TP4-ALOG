package hissab.ejb;

import jakarta.ejb.Local;

@Local
public interface IHissabExpressionLocal {
    double evaluerExpression(String expression) throws IllegalArgumentException;
}
