-- ============================================================
--  discordRageBait - Schema PostgreSQL
--  Executer ce fichier une seule fois pour initialiser la base
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

-- Parametres generaux du bot (target, interval...)
CREATE TABLE IF NOT EXISTS config_settings (
    key   VARCHAR(64) PRIMARY KEY,
    value VARCHAR(256) NOT NULL
);

-- Salons configures pour le ghost ping
CREATE TABLE IF NOT EXISTS config_channels (
    channel_id BIGINT PRIMARY KEY
);

-- Utilisateurs exclus de la commande "Qui t'a demande"
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
--  Systeme de caisses (CS:GO-like)
-- ============================================================

-- Inventaire des caisses par joueur
-- case_name correspond aux noms definis dans cases/cases.json
CREATE TABLE IF NOT EXISTS case_inventory (
    user_id   BIGINT      NOT NULL,
    case_name VARCHAR(64) NOT NULL,
    quantity  INT         NOT NULL DEFAULT 1 CHECK (quantity >= 0),
    PRIMARY KEY (user_id, case_name)
);

CREATE INDEX IF NOT EXISTS idx_case_inventory_user ON case_inventory (user_id);

-- Inventaire des armes droppees par joueur
-- Chaque ouverture de caisse stocke l'arme obtenue ici
-- locked = true : arme protegee contre Sell All
CREATE TABLE IF NOT EXISTS weapon_inventory (
    id           SERIAL           PRIMARY KEY,
    user_id      BIGINT           NOT NULL,
    weapon_name  VARCHAR(128)     NOT NULL,
    case_name    VARCHAR(64)      NOT NULL,
    quality      VARCHAR(32)      NOT NULL,
    float_value  DOUBLE PRECISION NOT NULL,
    price        BIGINT           NOT NULL,
    locked       BOOLEAN          NOT NULL DEFAULT FALSE
);

CREATE INDEX IF NOT EXISTS idx_weapon_inventory_user ON weapon_inventory (user_id);

-- Migration : ajoute locked si installation existante
ALTER TABLE weapon_inventory ADD COLUMN IF NOT EXISTS locked BOOLEAN NOT NULL DEFAULT FALSE;

-- ============================================================
--  Donnees initiales optionnelles (decommente si besoin)
-- ============================================================
-- INSERT INTO config_settings (key, value) VALUES ('interval', '300') ON CONFLICT DO NOTHING;