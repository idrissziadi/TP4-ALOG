package hissab.web;

/**
 * DTO (Data Transfer Object) pour les réponses JSON du service REST.
 * Découple la couche REST de l'entité JPA CalculHistorique.
 */
public class ResultatDTO {
    public String  expression;
    public double  resultat;
    public boolean succes;
    public String  message;

    public ResultatDTO() {}

    public static ResultatDTO ok(String expression, double resultat) {
        ResultatDTO dto = new ResultatDTO();
        dto.expression = expression;
        dto.resultat   = resultat;
        dto.succes     = true;
        dto.message    = "Calcul réussi";
        return dto;
    }

    public static ResultatDTO erreur(String message) {
        ResultatDTO dto = new ResultatDTO();
        dto.succes  = false;
        dto.message = message;
        return dto;
    }
}
