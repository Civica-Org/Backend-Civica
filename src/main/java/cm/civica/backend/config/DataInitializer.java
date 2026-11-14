package cm.civica.backend.config;

import cm.civica.backend.enums.Role;
import cm.civica.backend.model.*;
import cm.civica.backend.repository.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DataInitializer {

    private static final Logger log = LoggerFactory.getLogger(DataInitializer.class);

    @Bean
    public CommandLineRunner initDefaultData(
            OrganisationRepository organisationRepo,
            ServiceAdministratifRepository serviceRepo,
            DemarcheRepository demarcheRepo,
            UtilisateurRepository utilisateurRepo,
            PasswordEncoder passwordEncoder) {

        return args -> {
            // 1. Initialisation des Organisations & Services
            if (organisationRepo.count() == 0) {
                log.info(">>> [DATA INITIALIZER] Seeding des organisations et services de référence...");

                // DGSN - CNI & Passeport
                Organisation dgsn = organisationRepo.save(Organisation.builder()
                        .code("DGSN")
                        .nom("Délégation Générale à la Sûreté Nationale")
                        .typeOrganisation("POLICE_ETAT")
                        .actif(true)
                        .build());

                ServiceAdministratif serviceCni = serviceRepo.save(ServiceAdministratif.builder()
                        .organisation(dgsn)
                        .code("SR-CNI-CE")
                        .nom("Sous-Direction de la Police Judiciaire - Section CNI Centre")
                        .actif(true)
                        .build());

                // Mairie de Yaoundé 1er - État Civil
                Organisation mairieYde1 = organisationRepo.save(Organisation.builder()
                        .code("COMMUNE-YDE-1")
                        .nom("Mairie de Yaoundé 1er")
                        .typeOrganisation("COLLECTIVITE_TERRITORIALE")
                        .actif(true)
                        .build());

                ServiceAdministratif serviceEtatCivil = serviceRepo.save(ServiceAdministratif.builder()
                        .organisation(mairieYde1)
                        .code("SR-ETAT-CIVIL")
                        .nom("Bureau Principal de l'État Civil")
                        .actif(true)
                        .build());

                // Tribunal de Première Instance - Casier Judiciaire
                Organisation tpi = organisationRepo.save(Organisation.builder()
                        .code("TPI-YDE")
                        .nom("Tribunal de Première Instance de Yaoundé")
                        .typeOrganisation("JUSTICE")
                        .actif(true)
                        .build());

                ServiceAdministratif serviceCasier = serviceRepo.save(ServiceAdministratif.builder()
                        .organisation(tpi)
                        .code("GREFFE-CASIER")
                        .nom("Greffe Central - Service du Casier Judiciaire")
                        .actif(true)
                        .build());

                // 2. Initialisation des Démarches administratives
                if (demarcheRepo.count() == 0) {
                    log.info(">>> [DATA INITIALIZER] Seeding des démarches administratives du Cameroun...");

                    demarcheRepo.save(Demarche.builder()
                            .code("DEM-CNI")
                            .titre("Délivrance de la Carte Nationale d'Identité (CNI)")
                            .description("Demande ou renouvellement de la CNI biométrique informatisée.")
                            .delaiIndicatifJours(14)
                            .actif(true)
                            .build());

                    demarcheRepo.save(Demarche.builder()
                            .code("DEM-ACTE-NAISS")
                            .titre("Extrait d'Acte de Naissance")
                            .description("Établissement ou copie certifiée conforme d'acte de naissance.")
                            .delaiIndicatifJours(3)
                            .actif(true)
                            .build());

                    demarcheRepo.save(Demarche.builder()
                            .code("DEM-CASIER-JUD")
                            .titre("Bulletin N°3 du Casier Judiciaire")
                            .description("Certificat attestant des condamnations ou absence de condamnations pénales.")
                            .delaiIndicatifJours(2)
                            .actif(true)
                            .build());
                }

                // 3. Comptes utilisateurs de test
                if (utilisateurRepo.count() == 0) {
                    log.info(">>> [DATA INITIALIZER] Seeding des comptes utilisateurs par défaut...");

                    // Super Admin
                    utilisateurRepo.save(Utilisateur.builder()
                            .email("admin@civica.cm")
                            .motDePasseHash(passwordEncoder.encode("Admin@Civica2026!"))
                            .nom("Administrateur")
                            .prenom("CIVICA")
                            .telephone("+237699000000")
                            .role(Role.ROLE_SUPER_ADMIN)
                            .actif(true)
                            .emailVerifie(true) // compte système — pas de vérification requise
                            .build());

                    // Agent DGSN
                    utilisateurRepo.save(Utilisateur.builder()
                            .email("agent.dgsn@civica.cm")
                            .motDePasseHash(passwordEncoder.encode("Agent@Civica2026!"))
                            .nom("Mbarga")
                            .prenom("Jean-Pierre")
                            .telephone("+237677112233")
                            .role(Role.ROLE_AGENT)
                            .service(serviceCni)
                            .actif(true)
                            .emailVerifie(true) // compte système — pas de vérification requise
                            .build());

                    // Responsable État Civil
                    utilisateurRepo.save(Utilisateur.builder()
                            .email("responsable.etatcivil@civica.cm")
                            .motDePasseHash(passwordEncoder.encode("Resp@Civica2026!"))
                            .nom("Ngo")
                            .prenom("Marie-Claire")
                            .telephone("+237699334455")
                            .role(Role.ROLE_RESPONSABLE)
                            .service(serviceEtatCivil)
                            .actif(true)
                            .emailVerifie(true) // compte système — pas de vérification requise
                            .build());

                    // Citoyen Démo
                    utilisateurRepo.save(Utilisateur.builder()
                            .email("citoyen.demo@civica.cm")
                            .motDePasseHash(passwordEncoder.encode("Citoyen@Civica2026!"))
                            .nom("Kamga")
                            .prenom("Arnaud")
                            .telephone("+237655443322")
                            .role(Role.ROLE_CITOYEN)
                            .actif(true)
                            .emailVerifie(true) // compte de démo — pré-vérifié
                            .build());
                }

                log.info(">>> [DATA INITIALIZER] Initialisation terminée avec succès !");
            }
        };
    }
}

