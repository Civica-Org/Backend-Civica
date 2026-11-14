# ⚙️ CIVICA — Backend API REST

> **Cœur applicatif et moteur de traitement des démarches administratives**  
> *Composant de la plateforme CIVICA (INF4027 — Master 1 Informatique · Université de Yaoundé I)*

---

## 🛠️ Stack Technologique

* **Langage :** Java 21 LTS
* **Framework :** Spring Boot 3.3.5
  * Spring Data JPA & Hibernate
  * Spring Security 6 (Authentification sans état JWT & RBAC)
  * Springdoc OpenAPI 3.0 (Documentation Swagger UI)
  * Jakarta Bean Validation
* **Base de données :** PostgreSQL 15+
* **Migrations :** Flyway
* **Gestionnaire de dépendances :** Apache Maven

---

## 📁 Architecture des Packages

```text
src/main/java/cm/civica/backend/
├── BackendCivicaApplication.java
├── common/             # Exceptions globales, DTOs partagés, utilitaires
├── security/           # Configuration Spring Security, filtres JWT
├── users/              # Citoyen, Agent, Responsable, RBAC
├── organizations/      # Organisations et Services administratifs
├── procedures/         # Démarches, Variantes de circuit
├── dossiers/           # Dossier, Pièces justificatives, Historique
├── workflow/           # Machine à états (Patron State), Transitions
├── transfers/          # Moteur de transfert inter-services (Invariants)
├── notifications/      # Patron Observer, WebSockets & Alertes
└── ai/                 # Adaptateur LLM (Contrôle complétude & Reformulation)
```

---

## 🚀 Lancement & Configuration Locale

### 1. Prérequis
* JDK 21 installé (`java -version`)
* Maven (`mvn -version`)
* Base de données PostgreSQL en cours d'exécution sur le port `5432`

### 2. Démarrer PostgreSQL avec Docker
À la racine globale du projet :
```bash
docker compose up -d
```

### 3. Exécuter l'application
```bash
mvn clean spring-boot:run
```

L'API démarre sur : `http://localhost:8080`

---

## 📖 Documentation de l'API (Swagger UI)

Une fois l'application lancée, la documentation interactive OpenAPI est consultable sur :
👉 **[http://localhost:8080/swagger-ui.html](http://localhost:8080/swagger-ui.html)**
* Spécification OpenAPI JSON : `http://localhost:8080/api-docs`

---

## 🧪 Tests

```bash
mvn test
```

