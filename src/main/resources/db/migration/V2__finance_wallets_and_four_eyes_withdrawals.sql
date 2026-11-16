-- CIVICA Database Schema V2
-- Livrable 14 : Paiements simulés, Allocations financières (PaymentAllocation) et Portefeuilles (InstitutionWallet)
-- Livrable 15 : Retraits financiers selon le principe des quatre yeux (WithdrawalRequest)

-- 1. Portefeuilles financiers internes par Organisation (InstitutionWallet)
CREATE TABLE IF NOT EXISTS institution_wallets (
    id BIGSERIAL PRIMARY KEY,
    organisation_id BIGINT UNIQUE NOT NULL REFERENCES organisations(id) ON DELETE CASCADE,
    numero_compte_tresor VARCHAR(100) UNIQUE NOT NULL,
    solde_disponible NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (solde_disponible >= 0),
    solde_bloque NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (solde_bloque >= 0),
    total_encaisse NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (total_encaisse >= 0),
    total_decaisse NUMERIC(15, 2) NOT NULL DEFAULT 0.00 CHECK (total_decaisse >= 0),
    devise VARCHAR(10) NOT NULL DEFAULT 'XAF',
    updated_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 2. Paiements simulés des droits légaux par dossier (Paiement)
CREATE TABLE IF NOT EXISTS paiements (
    id BIGSERIAL PRIMARY KEY,
    reference_transaction VARCHAR(80) UNIQUE NOT NULL,
    quittance_numero VARCHAR(80) UNIQUE,
    dossier_id BIGINT NOT NULL REFERENCES dossiers(id) ON DELETE CASCADE,
    citoyen_id BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE RESTRICT,
    montant_total NUMERIC(15, 2) NOT NULL CHECK (montant_total > 0),
    devise VARCHAR(10) NOT NULL DEFAULT 'XAF',
    mode_paiement VARCHAR(50) NOT NULL, -- MTN_MOMO, ORANGE_MONEY, CARTE_BANCAIRE, GUICHET_TRESOR
    instrument_masque VARCHAR(80),
    variante_circuit VARCHAR(80),
    statut VARCHAR(30) NOT NULL DEFAULT 'CONFIRME', -- EN_ATTENTE, CONFIRME, ECHOUE
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    confirmed_at TIMESTAMP WITH TIME ZONE
);

-- 3. Répartition financière inter-institutions (PaymentAllocation)
CREATE TABLE IF NOT EXISTS payment_allocations (
    id BIGSERIAL PRIMARY KEY,
    paiement_id BIGINT NOT NULL REFERENCES paiements(id) ON DELETE CASCADE,
    wallet_id BIGINT NOT NULL REFERENCES institution_wallets(id) ON DELETE RESTRICT,
    organisation_id BIGINT NOT NULL REFERENCES organisations(id) ON DELETE RESTRICT,
    libelle_quote_part VARCHAR(255) NOT NULL,
    pourcentage NUMERIC(5, 2) NOT NULL CHECK (pourcentage > 0 AND pourcentage <= 100),
    montant_alloue NUMERIC(15, 2) NOT NULL CHECK (montant_alloue >= 0),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP
);

-- 4. Demandes de retrait financier avec Principe des Quatre Yeux (WithdrawalRequest)
CREATE TABLE IF NOT EXISTS withdrawal_requests (
    id BIGSERIAL PRIMARY KEY,
    reference_demande VARCHAR(80) UNIQUE NOT NULL,
    wallet_id BIGINT NOT NULL REFERENCES institution_wallets(id) ON DELETE RESTRICT,
    organisation_id BIGINT NOT NULL REFERENCES organisations(id) ON DELETE RESTRICT,
    montant NUMERIC(15, 2) NOT NULL CHECK (montant > 0),
    compte_beneficiaire VARCHAR(150) NOT NULL,
    motif VARCHAR(500) NOT NULL,
    statut VARCHAR(30) NOT NULL DEFAULT 'DEMANDE', -- DEMANDE, EXECUTE, REFUSE
    requested_by_id BIGINT NOT NULL REFERENCES utilisateurs(id) ON DELETE RESTRICT,
    authorized_by_id BIGINT REFERENCES utilisateurs(id) ON DELETE RESTRICT,
    commentaire_decision VARCHAR(500),
    created_at TIMESTAMP WITH TIME ZONE DEFAULT CURRENT_TIMESTAMP,
    decided_at TIMESTAMP WITH TIME ZONE,
    CONSTRAINT chk_four_eyes_principle CHECK (authorized_by_id IS NULL OR authorized_by_id <> requested_by_id)
);

CREATE INDEX IF NOT EXISTS idx_paiements_dossier_id ON paiements(dossier_id);
CREATE INDEX IF NOT EXISTS idx_paiements_citoyen_id ON paiements(citoyen_id);
CREATE INDEX IF NOT EXISTS idx_allocations_paiement_id ON payment_allocations(paiement_id);
CREATE INDEX IF NOT EXISTS idx_allocations_wallet_id ON payment_allocations(wallet_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_wallet_id ON withdrawal_requests(wallet_id);
CREATE INDEX IF NOT EXISTS idx_withdrawals_statut ON withdrawal_requests(statut);

