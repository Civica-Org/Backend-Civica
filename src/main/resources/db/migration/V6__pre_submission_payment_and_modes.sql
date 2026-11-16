-- CIVICA Database Schema V6
-- Paiement obligatoire avant soumission du dossier citoyen + moyens de paiement officiels
-- (MOMO, OM, CARTE_BANCAIRE, COMPTE_BANCAIRE)

ALTER TABLE paiements
    ALTER COLUMN dossier_id DROP NOT NULL;

ALTER TABLE paiements
    ADD COLUMN IF NOT EXISTS demarche_code VARCHAR(80),
    ADD COLUMN IF NOT EXISTS service_id BIGINT REFERENCES services_administratifs(id) ON DELETE SET NULL;

-- Rétro-remplissage de demarche_code / service_id sur les paiements existants liés à un dossier
UPDATE paiements p
SET demarche_code = dm.code,
    service_id = COALESCE(p.service_id, d.service_courant_id)
FROM dossiers d
JOIN demarches dm ON d.demarche_id = dm.id
WHERE p.dossier_id = d.id
  AND p.demarche_code IS NULL;

CREATE INDEX IF NOT EXISTS idx_paiements_citoyen_demarche_statut
    ON paiements(citoyen_id, demarche_code, statut);
