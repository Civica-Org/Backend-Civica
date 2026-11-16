-- Référentiel dynamique des types d'organisations administratives (Super Admin)
CREATE TABLE IF NOT EXISTS organisation_types (
    id BIGSERIAL PRIMARY KEY,
    code VARCHAR(50) NOT NULL UNIQUE,
    libelle VARCHAR(150) NOT NULL,
    description VARCHAR(300),
    actif BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP
);
