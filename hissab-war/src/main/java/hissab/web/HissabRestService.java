package hissab.web;

import hissab.ejb.CalculHistorique;
import hissab.ejb.IHissabExpressionLocal;
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
 */
@Path("/hissab")
@RequestScoped
public class HissabRestService {

    @EJB
    private IHissabExpressionLocal expressionEJB;

    @EJB
    private IHistoriqueLocal historique;

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

        if (fichierBytes != null) {
            try {
                expression = OcrUtil.extraireTexte(fichierBytes);
            } catch (RuntimeException e) {
                ocrErreur = e.getMessage();
            }
        }

        if ((expression == null || expression.isBlank()) &&
                expressionTexte != null && !expressionTexte.isBlank()) {
            expression = expressionTexte.trim();
            ocrErreur  = null;
        }

        if (expression == null || expression.isBlank()) {
            String msg = (ocrErreur != null)
                    ? "OCR échoué — " + ocrErreur + ". Saisissez l'expression manuellement."
                    : "Aucune expression trouvée. Veuillez saisir l'expression manuellement.";
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResultatDTO.erreur(msg))
                    .build();
        }

        double resultat;
        try {
            resultat = expressionEJB.evaluerExpression(expression);
        } catch (EJBException | IllegalArgumentException ex) {
            Throwable cause = ex;
            while (cause.getCause() != null) cause = cause.getCause();
            return Response.status(Response.Status.BAD_REQUEST)
                    .entity(ResultatDTO.erreur("Expression invalide : " + cause.getMessage()))
                    .build();
        }

        historique.sauvegarder(expression, resultat);
        return Response.ok(ResultatDTO.ok(expression, resultat)).build();
    }

    @GET
    @Path("/historique")
    @Produces(MediaType.APPLICATION_JSON)
    public Response getHistorique() {
        List<CalculHistorique> liste = historique.getTout();
        return Response.ok(liste).build();
    }
}
