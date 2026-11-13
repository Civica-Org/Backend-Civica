-- CIVICA Database Schema V1
-- PostgreSQL 15+

-- 1. Table des Organisations administratives
CREATE TABLE IF NOT EXISTS organisations (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    nom VARCHAR(255) NOT NULL,
    type_organisation VARCHAR(50) NOT NULL,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Table des Services Administratifs
CREATE TABLE IF NOT EXISTS services_administratifs (
    id BIGSERIAL PRIMARY KEY,
    organisation_id BIGINT NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    code VARCHAR(50) NOT NULL,
    nom VARCHAR(255) NOT NULL,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_service_org_code UNIQUE (organisation_id, code)
);

-- 3. Table des Utilisateurs (Citoyen, Agent, Responsable, Admin)
CREATE TABLE IF NOT EXISTS utilisateurs (
    id BIGSERIAL PRIMARY KEY,
    email VARCHAR(255) UNIQUE NOT NULL,
    mot_de_passe_hash VARCHAR(255) NOT NULL,
    nom VARCHAR(100) NOT NULL,
    prenom VARCHAR(100) NOT NULL,
    telephone VARCHAR(50),
    role VARCHAR(50) NOT NULL, -- ROLE_CITOYEN, ROLE_AGENT, ROLE_RESPONSABLE, ROLE_SUPER_ADMIN
    service_id BIGINT REFERENCES services_administratifs(id) ON DELETE SET NULL,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Table des Démarches administratives (Catalogue)
CREATE TABLE IF NOT EXISTS demarches (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) UNIQUE NOT NULL,
    titre VARCHAR(255) NOT NULL,
    description TEXT,
    delai_indicatif_jours INT DEFAULT 7,
    actif BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 5. Table des Dossiers
CREATE TABLE IF NOT EXISTS dossiers (
    id BIGSERIAL PRIMARY KEY,
    numero_suivi VARCHAR(64) UNIQUE NOT NULL,
    citoyen_id BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE RESTRICT,
    demarche_id BIGINT NOT NULL REFERENCES demarches(id) ON DELETE RESTRICT,
    service_courant_id BIGINT REFERENCES services_administratifs(id) ON DELETE SET NULL,
    statut VARCHAR(50) NOT NULL DEFAULT 'SOUMIS', -- BROUILLON, SOUMIS, EN_VERIFICATION, TRANSFERE, VALIDE, REJETE, RETIRE
    priorite VARCHAR(20) DEFAULT 'NORMALE',
    motif_rejet TEXT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 6. Table des Pièces Jointes / Documents
CREATE TABLE IF NOT EXISTS pieces_justificatives (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossiers(id) ON DELETE CASCADE,
    intitule VARCHAR(255) NOT NULL,
    type_mime VARCHAR(100) NOT NULL,
    nom_fichier_stocke VARCHAR(255) NOT NULL,
    taille_octets BIGINT NOT NULL,
    statut_validation VARCHAR(50) DEFAULT 'EN_ATTENTE', -- EN_ATTENTE, CONFORME, NON_CONFORME
    observations_ia TEXT,
    uploaded_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 7. Table d'Historique et Traçabilité (Audit Log)
CREATE TABLE IF NOT EXISTS historique_dossiers (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT NOT NULL REFERENCES dossiers(id) ON DELETE CASCADE,
    agent_id BIGINT REFERENCES utilisateurs(id) ON DELETE SET NULL,
    service_source_id BIGINT REFERENCES services_administratifs(id) ON DELETE SET NULL,
    service_cible_id BIGINT REFERENCES services_administratifs(id) ON DELETE SET NULL,
    statut_precedent VARCHAR(50),
    nouveau_statut VARCHAR(50) NOT NULL,
    action_effectuee VARCHAR(100) NOT NULL,
    commentaire TEXT,
    timestamp TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- Index pour optimiser les recherches fréquentes
CREATE INDEX idx_dossiers_numero_suivi ON dossiers(numero_suivi);
CREATE INDEX idx_dossiers_citoyen_id ON dossiers(citoyen_id);
CREATE INDEX idx_dossiers_service_courant ON dossiers(service_courant_id);
CREATE INDEX idx_dossiers_statut ON dossiers(statut);
CREATE INDEX idx_pieces_dossier_id ON pieces_justificatives(dossier_id);
CREATE INDEX idx_historique_dossier_id ON historique_dossiers(dossier_id);
