-- ============================================================
--  discordRageBait - Schéma PostgreSQL
--  Exécuter ce fichier une seule fois pour initialiser la base
--  psql -U <user> -d <database> -f database.sql
-- ============================================================

-- Soldes des joueurs du casino
CREATE TABLE IF NOT EXISTS casino_balances (
    user_id  BIGINT PRIMARY KEY,
    balance  BIGINT NOT NULL DEFAULT 1000
);

-- Timestamps du dernier /daily (en secondes unix)
CREATE TABLE IF NOT EXISTS casino_daily (
    user_id    BIGINT PRIMARY KEY,
    last_claim BIGINT NOT NULL
);

-- Paramètres généraux du bot (target, interval...)
CREATE TABLE IF NOT EXISTS config_settings (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(256) NOT NULL
);

-- Salons configurés pour le ghost ping
CREATE TABLE IF NOT EXISTS config_channels (
    channel_id BIGINT PRIMARY KEY
);

-- Utilisateurs exclus de la commande "Qui t'a demandé"
CREATE TABLE IF NOT EXISTS qui_exclusions (
    user_id BIGINT PRIMARY KEY
);

-- Statut tracker persistant
CREATE TABLE IF NOT EXISTS status_tracker (
    guild_id   BIGINT PRIMARY KEY,
    user_id    BIGINT NOT NULL,
    channel_id BIGINT NOT NULL,
    enabled    BOOLEAN NOT NULL DEFAULT TRUE
);

-- ============================================================
--  Données initiales optionnelles (décommente si besoin)
-- ============================================================
-- INSERT INTO config_settings (key, value) VALUES ('interval', '300') ON CONFLICT DO NOTHING;
