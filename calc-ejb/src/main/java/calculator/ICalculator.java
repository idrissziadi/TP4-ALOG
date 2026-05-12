package calculator;

import jakarta.ejb.Remote;

@Remote
public interface ICalculator {
    double sum(double a, double b);
    double minus(double a, double b);
    double product(double a, double b);
    double divide(double a, double b);
    long factorial(int n);
}
