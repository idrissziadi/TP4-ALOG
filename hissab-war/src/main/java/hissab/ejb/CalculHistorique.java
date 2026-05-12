package hissab.ejb;

import jakarta.persistence.*;
import java.io.Serializable;
import java.util.Date;

/**
 * Entité JPA — représente un calcul enregistré dans Derby.
 * Table : CALC_HISTORIQUE
 */
@Entity
@Table(name = "CALC_HISTORIQUE")
@NamedQuery(
    name  = "CalculHistorique.findAll",
    query = "SELECT c FROM CalculHistorique c ORDER BY c.dateCalcul DESC"
)
public class CalculHistorique implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 500)
    private String expression;

    @Column(nullable = false)
    private double resultat;

    @Column(name = "DATE_CALCUL")
    @Temporal(TemporalType.TIMESTAMP)
    private Date dateCalcul;

    @PrePersist
    public void preInsert() {
        this.dateCalcul = new Date();
    }

    // Constructeur vide requis par JPA
    public CalculHistorique() {}

    public CalculHistorique(String expression, double resultat) {
        this.expression = expression;
        this.resultat   = resultat;
    }

    public Long getId()             { return id; }
    public String getExpression()   { return expression; }
    public double getResultat()     { return resultat; }
    public Date getDateCalcul()     { return dateCalcul; }

    public void setId(Long id)                  { this.id = id; }
    public void setExpression(String expression){ this.expression = expression; }
    public void setResultat(double resultat)    { this.resultat = resultat; }
    public void setDateCalcul(Date dateCalcul)  { this.dateCalcul = dateCalcul; }
}
