-- CIVICA Database Schema V4
-- Chapitre 8 : Journal d'audit des suggestions IA (ai_suggestions)
-- Enregistre la provenance IA, le mode dégradé éventuel, et l'agent humain valideur final.

CREATE TABLE IF NOT EXISTS ai_suggestions (
    id BIGSERIAL PRIMARY KEY,
    dossier_id BIGINT REFERENCES dossiers(id) ON DELETE CASCADE,
    type_assistance VARCHAR(50) NOT NULL, -- COMPLETUDE_AUDIT, REFORMULATION_REJET, ASSISTANT_CHAT, PRECHECK_DEPOT
    fournisseur VARCHAR(60) NOT NULL, -- GEMINI_REST, FALLBACK_DETERMINISTE
    modele VARCHAR(80),
    resume_prompt_anonymise TEXT,
    reponse_json TEXT,
    suggestion_issu_ia BOOLEAN NOT NULL DEFAULT TRUE,
    mode_degrade BOOLEAN NOT NULL DEFAULT FALSE,
    demandeur_id BIGINT REFERENCES utilisateurs(id) ON DELETE SET NULL,
    validateur_id BIGINT REFERENCES utilisateurs(id) ON DELETE SET NULL,
    acceptee_par_agent BOOLEAN,
    latence_ms BIGINT,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_ai_suggestions_dossier_id ON ai_suggestions(dossier_id);
CREATE INDEX IF NOT EXISTS idx_ai_suggestions_type ON ai_suggestions(type_assistance);
CREATE INDEX IF NOT EXISTS idx_ai_suggestions_created_at ON ai_suggestions(created_at DESC);

