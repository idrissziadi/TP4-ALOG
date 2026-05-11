package hissab.web;

import jakarta.ws.rs.ApplicationPath;
import jakarta.ws.rs.core.Application;

/**
 * Active JAX-RS et monte tous les endpoints sous /api/...
 * ex: POST /HISSAB/api/hissab/calculer
 */
@ApplicationPath("/api")
public class ApplicationConfig extends Application {
}
