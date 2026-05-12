package hissab.web;

import hissab.ejb.CalculHistorique;
import hissab.ejb.ICalcRemote;
import hissab.ejb.IHistoriqueLocal;

import jakarta.ejb.EJB;
import jakarta.ejb.EJBException;
import jakarta.enterprise.context.RequestScoped;
import jakarta.ws.rs.*;
import jakarta.ws.rs.core.EntityPart;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * Service REST HISSAB — deux endpoints :
 *   POST /api/hissab/calculer   → reçoit image ou texte, retourne résultat
 *   GET  /api/hissab/historique → retourne la liste des calculs
 *
 * Multipart géré via l'API standard JAX-RS 3.1 (EntityPart),
 * incluse dans jakarta.jakartaee-api:10.0.0 — aucune dépendance Jersey spécifique.
 */
@Path("/hissab")
@RequestScoped
public class HissabRestService {

    @EJB
    private ICalcRemote calc;

    @EJB
    private IHistoriqueLocal historique;

    /**
     * Endpoint 1 : calculer une expression.
     * Accepte soit un fichier image/PDF (OCR), soit du texte directement.
     */
    @POST
    @Path("/calculer")
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    @Produces(MediaType.APPLICATION_JSON)
    public Response calculer(List<EntityPart> parts) throws IOException {

        String expressionTexte = null;
        byte[] fichierBytes    = null;

        for (EntityPart part : parts) {
            String nom = part.getName();
            if (nom == null) continue;
            switch (nom) {
                case "expression" -> expressionTexte = part.getContent(String.class);
                case "fichier"    -> {
                    try (InputStream is = part.getContent(InputStream.class)) {
                        byte[] bytes = is.readAllBytes();
                        if (bytes.length > 0) fichierBytes = bytes;
                    }
                }
            }
        }

        String expression = null;
        String ocrErreur  = null;

        // Étape 1 : OCR si fichier fourni
        if (fichierBytes != null) {
            try {
                expression = OcrUtil.extraireTexte(fichierBytes);
            } catch (RuntimeException e) {
                ocrErreur = e.getMessage();   // message descriptif transmis au client
            }
        }

        // Étape 2 : fallback sur la saisie manuelle
        if ((expression == null || expression.isBlank()) &&
                expressionTexte != null && !expressionTexte.isBlank()) {
            expression = expressionTexte.trim();
            ocrErreur  = null;   // la saisie manuelle a réussi, pas d'erreur à montrer
        }

        // Étape 3 : rien du tout → 400 avec raison OCR si disponible
        if (expression == null || expression.isBlank()) {
            String msg = (ocrErreur != null)
                    ? "OCR échoué — " + ocrErreur + ". Saisissez l'expression manuellement."
                    : "Aucune expression trouvée. Veuillez saisir l'expression manuellement.";
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResultatDTO.erreur(msg))
                    .build();
        }

        // Étape 4 : évaluer via CalcEJB
        // EJB wraps RuntimeExceptions in EJBException — on unwrappe pour avoir le vrai message.
        double resultat;
        try {
            resultat = calc.evaluerExpression(expression);
        } catch (EJBException | IllegalArgumentException ex) {
            Throwable cause = ex;
            while (cause.getCause() != null) cause = cause.getCause();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResultatDTO.erreur("Expression invalide : " + cause.getMessage()))
                    .build();
        }

        // Étape 5 : persister + répondre
        historique.sauvegarder(expression, resultat);
        return Response.ok(ResultatDTO.ok(expression, resultat)).build();
    }

    /**
     * Endpoint 2 : récupérer l'historique des calculs.
     */
    @GET
    @Path("/historique")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistorique() {
        List<CalculHistorique> liste = historique.getTout();
        return Response.ok(liste).build();
    }
}
