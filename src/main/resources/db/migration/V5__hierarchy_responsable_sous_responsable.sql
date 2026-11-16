-- Hiérarchie Organisation (1..1 Responsable, 0..N Services)
-- et Service Administratif (0..1 Sous-Responsable, 0..N Agents)

ALTER TABLE utilisateurs
    ADD COLUMN IF NOT EXISTS organisation_id BIGINT REFERENCES organisations(id) ON DELETE SET NULL;

CREATE INDEX IF NOT EXISTS idx_utilisateurs_organisation_id
    ON utilisateurs(organisation_id);

-- Rétro-migration des comptes ROLE_RESPONSABLE rattachés à un service :
-- un Responsable pilote désormais directement son Organisation et tous ses services.
UPDATE utilisateurs u
SET organisation_id = s.organisation_id,
    service_id = NULL
FROM services_administratifs s
WHERE u.service_id = s.id
  AND u.role = 'ROLE_RESPONSABLE'
  AND u.organisation_id IS NULL;

