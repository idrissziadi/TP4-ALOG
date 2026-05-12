package hissab.web;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.cert.X509Certificate;
import java.time.Duration;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilitaire OCR — extrait le texte d'une image ou d'un PDF via OCR.space API.
 * Clé gratuite : https://ocr.space/ocrapi
 */
public class OcrUtil {

    private static final String API_URL = "https://api.ocr.space/parse/image";
    private static final String API_KEY = "K83607540888957";

    /**
     * Envoie l'image à OCR.space et retourne l'expression arithmétique extraite.
     * Lance RuntimeException avec un message descriptif si l'OCR échoue.
     */
    public static String extraireTexte(byte[] imageBytes) {
        try {
            // URL-encoder la valeur base64 : le body x-www-form-urlencoded interprète
            // '+' comme espace et '/' comme séparateur, ce qui corrompt le base64 brut.
            String base64Raw   = Base64.getEncoder().encodeToString(imageBytes);
            String base64Value = URLEncoder.encode(
                    "data:image/png;base64," + base64Raw, StandardCharsets.UTF_8);
            String body = "base64Image=" + base64Value
                        + "&language=eng"
                        + "&isOverlayRequired=false"
                        + "&detectOrientation=true"
                        + "&scale=true"
                        + "&OCREngine=2";

            // Le truststore GlassFish ne contient pas le CA de api.ocr.space.
            // On désactive la validation SSL (acceptable en environnement de démo).
            TrustManager[] trustAll = new TrustManager[]{
                new X509TrustManager() {
                    public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
                    public void checkClientTrusted(X509Certificate[] c, String a) {}
                    public void checkServerTrusted(X509Certificate[] c, String a) {}
                }
            };
            SSLContext sslCtx = SSLContext.getInstance("TLS");
            sslCtx.init(null, trustAll, new java.security.SecureRandom());

            HttpClient client = HttpClient.newBuilder()
                    .sslContext(sslCtx)
                    .connectTimeout(Duration.ofSeconds(15))
                    .build();

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(API_URL))
                    .header("apikey", API_KEY)
                    .header("Content-Type", "application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .timeout(Duration.ofSeconds(30))
                    .build();

            HttpResponse<String> response = client.send(request,
                    HttpResponse.BodyHandlers.ofString());

            if (response.statusCode() != 200) {
                throw new RuntimeException("OCR API — HTTP " + response.statusCode());
            }

            String json = response.body();

            // Vérifier si l'API a retourné une erreur dans le JSON
            if (json.contains("\"IsErroredOnProcessing\":true")) {
                String errMsg = extraireChampString(json, "ErrorMessage");
                throw new RuntimeException(errMsg != null ? errMsg
                        : "OCR API a retourné une erreur inconnue");
            }

            String texte = extraireChampParsedText(json);
            if (texte == null || texte.isBlank()) {
                throw new RuntimeException("Aucun texte détecté dans l'image");
            }

            String expression = nettoyerExpression(texte);
            if (expression.isBlank()) {
                throw new RuntimeException(
                        "Texte détecté mais aucune expression mathématique trouvée : \"" + texte.trim() + "\"");
            }

            return expression;

        } catch (RuntimeException e) {
            throw e;
        } catch (Exception e) {
            throw new RuntimeException("Erreur de connexion OCR : " + e.getMessage(), e);
        }
    }

    /** Extrait le champ ParsedText du JSON OCR.space (regex, sans dépendance JSON). */
    private static String extraireChampParsedText(String json) {
        Pattern p = Pattern.compile("\"ParsedText\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) {
            return m.group(1)
                    .replace("\\n", " ")
                    .replace("\\r", "")
                    .replace("\\t", " ");
        }
        return null;
    }

    /** Extrait un champ string quelconque du JSON (regex simple). */
    private static String extraireChampString(String json, String champ) {
        Pattern p = Pattern.compile("\"" + champ + "\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) return m.group(1);
        // Parfois ErrorMessage est un tableau JSON : ["msg"]
        Pattern p2 = Pattern.compile("\"" + champ + "\"\\s*:\\s*\\[\"(.*?)\"\\]", Pattern.DOTALL);
        Matcher m2 = p2.matcher(json);
        if (m2.find()) return m2.group(1);
        return null;
    }

    /** Normalise les symboles Unicode et filtre les caractères non mathématiques. */
    private static String nettoyerExpression(String texte) {
        return texte
                .replace("×", "*")
                .replace("÷", "/")
                .replace("−", "-")
                .replace("–", "-")
                .replace("x", "*")
                .replace("X", "*")
                .replaceAll("[^0-9+\\-*/().\\s]", "")
                .replaceAll("\\s+", " ")
                .trim();
    }
}
