package hissab.ejb;

import jakarta.ejb.Local;
import java.util.List;

/**
 * Interface locale du composant HISTORIQUE (EJB #2).
 */
@Local
public interface IHistoriqueLocal {

    /**
     * Persiste un calcul dans la base de données Derby.
     *
     * @param expression expression évaluée (ex: "5 + 2 * 6 - 3")
     * @param resultat   résultat numérique (ex: 34.0)
     */
    void sauvegarder(String expression, double resultat);

    /**
     * Retourne tous les calculs enregistrés, du plus récent au plus ancien.
     */
    List<CalculHistorique> getTout();
}
