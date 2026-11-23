-- =====================================================================================
-- V5: Segundo factor TOTP (RFC 6238) y sesiones administradas en servidor
-- =====================================================================================

-- Factor TOTP del usuario (app autenticadora). El secreto se guarda cifrado con AES-256-GCM:
-- una copia de la base de datos no basta para generar codigos validos.
CREATE TABLE IF NOT EXISTS factor_totp (
    usuario_id        uuid          PRIMARY KEY,
    secreto_cifrado   varchar(200)  NOT NULL,
    confirmado        boolean       NOT NULL DEFAULT false,
    ultimo_paso       bigint        NOT NULL DEFAULT 0,
    created_at        timestamptz   NOT NULL DEFAULT now(),
    updated_at        timestamptz   NOT NULL DEFAULT now(),

    CONSTRAINT fk_factor_totp_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_factor_totp_paso CHECK (ultimo_paso >= 0)
);

COMMENT ON TABLE factor_totp IS 'Segundo factor de autenticacion (TOTP). Un factor por usuario.';
COMMENT ON COLUMN factor_totp.secreto_cifrado IS 'Secreto TOTP cifrado con AES-256-GCM (iv || ciphertext en Base64).';
COMMENT ON COLUMN factor_totp.confirmado IS 'false hasta que el usuario valida su primer codigo (enrolamiento).';
COMMENT ON COLUMN factor_totp.ultimo_paso IS 'Ultimo paso de 30 s aceptado: impide reutilizar un codigo (anti-replay).';

DROP TRIGGER IF EXISTS tg_factor_totp_updated_at ON factor_totp;
CREATE TRIGGER tg_factor_totp_updated_at BEFORE UPDATE ON factor_totp
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

-- Sesiones de acceso. El JWT de acceso lleva el id de sesion (claim sid) y cada peticion valida
-- que la sesion siga vigente: el logout, la baja o un acceso desde otro canal la invalidan en servidor.
CREATE TABLE IF NOT EXISTS sesion (
    id                  uuid          PRIMARY KEY,
    usuario_id          uuid          NOT NULL,
    canal               varchar(10)   NOT NULL,
    ip_origen           varchar(45),
    dispositivo         varchar(200),
    creada_en           timestamptz   NOT NULL,
    ultima_actividad    timestamptz   NOT NULL,
    expira_en           timestamptz   NOT NULL,
    revocada_en         timestamptz,
    motivo_revocacion   varchar(20),

    CONSTRAINT fk_sesion_usuario FOREIGN KEY (usuario_id) REFERENCES usuario (id) ON DELETE CASCADE,
    CONSTRAINT ck_sesion_canal CHECK (canal IN ('WEB', 'MOVIL')),
    CONSTRAINT ck_sesion_motivo CHECK (motivo_revocacion IS NULL
        OR motivo_revocacion IN ('LOGOUT', 'REEMPLAZADA', 'EXPIRADA', 'BAJA')),
    CONSTRAINT ck_sesion_revocacion CHECK ((revocada_en IS NULL) = (motivo_revocacion IS NULL)),
    CONSTRAINT ck_sesion_fechas CHECK (expira_en > creada_en)
);

COMMENT ON TABLE sesion IS 'Sesiones de acceso administradas en servidor (una sola vigente por usuario entre web y movil).';

-- Regla de negocio en la base de datos: a lo mas una sesion no revocada por usuario.
-- Dos inicios de sesion concurrentes no pueden dejar dos sesiones vivas.
CREATE UNIQUE INDEX IF NOT EXISTS ux_sesion_usuario_vigente ON sesion (usuario_id) WHERE revocada_en IS NULL;
CREATE INDEX IF NOT EXISTS ix_sesion_usuario_creada ON sesion (usuario_id, creada_en DESC);
