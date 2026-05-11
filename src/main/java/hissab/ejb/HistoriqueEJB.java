package hissab.ejb;

import jakarta.ejb.Stateless;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

/**
 * EJB #2 — Composant HISTORIQUE.
 * Persiste et récupère les calculs depuis la base Derby via JPA/EclipseLink.
 */
@Stateless
public class HistoriqueEJB implements IHistoriqueLocal {

    @PersistenceContext(unitName = "hissabPU")
    private EntityManager em;

    @Override
    public void sauvegarder(String expression, double resultat) {
        CalculHistorique calcul = new CalculHistorique(expression, resultat);
        em.persist(calcul);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CalculHistorique> getTout() {
        return em.createNamedQuery("CalculHistorique.findAll").getResultList();
    }
}
