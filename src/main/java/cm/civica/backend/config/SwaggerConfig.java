package cm.civica.backend.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI civicaOpenAPI() {
        final String securitySchemeName = "bearerAuth";

        return new OpenAPI()
                .info(new Info()
                        .title("CIVICA — Plateforme des Démarches Administratives (Cameroun)")
                        .description("""
                                API REST de la plateforme **CIVICA** pour la dématérialisation et le suivi
                                des dossiers administratifs (Actes de naissance, CNI, Casier judiciaire...).

                                ## Authentification
                                La majorité des endpoints requièrent un jeton **JWT Bearer** :
                                1. Obtenez un jeton via `POST /api/auth/signin` ou `POST /api/auth/signup`.
                                2. Cliquez sur **Authorize** (🔒) et entrez : `Bearer <votre_token>`.
                                3. Le suivi public d'un dossier par numéro est accessible sans authentification via `GET /api/citoyen/dossiers/suivi/{numeroSuivi}`.

                                ## Rôles du Système
                                - `ROLE_CITOYEN` : Dépôt et suivi de ses dossiers, téléversement des pièces.
                                - `ROLE_AGENT` : Prise en charge, vérification des pièces, validation ou rejet avec motif.
                                - `ROLE_RESPONSABLE` : Transfert inter-services, supervision et audit global.
                                - `ROLE_SUPER_ADMIN` : Configuration des organisations, services et catalogues.
                                """)
                        .version("1.0.0")
                        .contact(new Contact()
                                .name("CIVICA Engineering Team - Master 1 Info UYI")
                                .email("contact@civica.cm"))
                        .license(new License()
                                .name("Apache 2.0")
                                .url("https://www.apache.org/licenses/LICENSE-2.0")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components()
                        .addSecuritySchemes(securitySchemeName,
                                new SecurityScheme()
                                        .name(securitySchemeName)
                                        .type(SecurityScheme.Type.HTTP)
                                        .scheme("bearer")
                                        .bearerFormat("JWT")
                                        .description("Entrez votre jeton JWT sous la forme : Bearer <token>")));
    }
}

