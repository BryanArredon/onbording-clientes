-- =====================================================================================
-- V4: Control de intentos atomicos de PIN, retenciones de saldo (Holds) y Ledger contable
-- =====================================================================================

ALTER TABLE usuario
    ADD COLUMN IF NOT EXISTS intentos_fallidos_pin integer NOT NULL DEFAULT 0,
    ADD COLUMN IF NOT EXISTS pin_bloqueado_hasta timestamptz;

COMMENT ON COLUMN usuario.intentos_fallidos_pin IS 'Intentos fallidos consecutivos de autorizacion con PIN (maximo 3 antes de bloqueo temporal).';
COMMENT ON COLUMN usuario.pin_bloqueado_hasta IS 'Instante hasta el cual el uso del PIN transaccional permanece bloqueado.';

CREATE TABLE IF NOT EXISTS retencion_cuenta (
    id                  uuid            PRIMARY KEY,
    cuenta_id           uuid            NOT NULL,
    cliente_id          uuid            NOT NULL,
    numero_cuenta       varchar(10)     NOT NULL,
    idempotency_key     varchar(64)     NOT NULL,
    payload_hash        varchar(64)     NOT NULL,
    monto               numeric(18, 2)  NOT NULL,
    comision            numeric(18, 2)  NOT NULL DEFAULT 0.00,
    monto_total         numeric(18, 2)  NOT NULL,
    moneda              varchar(3)      NOT NULL DEFAULT 'MXN',
    concepto            varchar(160)    NOT NULL,
    estado              varchar(25)     NOT NULL,
    referencia_externa  varchar(80),
    motivo_reverso      varchar(200),
    expira_en           timestamptz     NOT NULL,
    version             bigint          NOT NULL DEFAULT 0,
    created_at          timestamptz     NOT NULL DEFAULT now(),
    updated_at          timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT fk_retencion_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id) ON DELETE RESTRICT,
    CONSTRAINT fk_retencion_cliente FOREIGN KEY (cliente_id) REFERENCES cliente (id) ON DELETE RESTRICT,
    CONSTRAINT uq_retencion_cuenta_idempotencia UNIQUE (cuenta_id, idempotency_key),
    CONSTRAINT ck_retencion_estado CHECK (estado IN ('RETENIDO', 'LIQUIDADO', 'REVERSANDO', 'REVERSADO', 'REQUIERE_REVISION')),
    CONSTRAINT ck_retencion_montos CHECK (monto > 0 AND comision >= 0 AND monto_total = monto + comision)
);

CREATE INDEX IF NOT EXISTS ix_retencion_cuenta_estado_expira
    ON retencion_cuenta (estado, expira_en)
    WHERE estado = 'RETENIDO';

CREATE INDEX IF NOT EXISTS ix_retencion_cliente
    ON retencion_cuenta (cliente_id, created_at DESC);

DROP TRIGGER IF EXISTS tg_retencion_cuenta_updated_at ON retencion_cuenta;
CREATE TRIGGER tg_retencion_cuenta_updated_at BEFORE UPDATE ON retencion_cuenta
    FOR EACH ROW EXECUTE FUNCTION set_updated_at();

CREATE TABLE IF NOT EXISTS movimiento_contable (
    id              uuid            PRIMARY KEY,
    retencion_id    uuid            NOT NULL,
    cuenta_id       uuid            NOT NULL,
    tipo_movimiento varchar(40)     NOT NULL,
    monto           numeric(18, 2)  NOT NULL,
    moneda          varchar(3)      NOT NULL DEFAULT 'MXN',
    saldo_posterior numeric(18, 2)  NOT NULL,
    correlation_id  varchar(64),
    created_at      timestamptz     NOT NULL DEFAULT now(),

    CONSTRAINT fk_movimiento_retencion FOREIGN KEY (retencion_id) REFERENCES retencion_cuenta (id) ON DELETE RESTRICT,
    CONSTRAINT fk_movimiento_cuenta FOREIGN KEY (cuenta_id) REFERENCES cuenta (id) ON DELETE RESTRICT,
    CONSTRAINT ck_movimiento_tipo CHECK (tipo_movimiento IN ('CARGO_RETENCION', 'LIQUIDACION_DEFINITIVA', 'ABONO_REVERSO_COMPENSATORIO')),
    CONSTRAINT ck_movimiento_monto_positivo CHECK (monto > 0),
    CONSTRAINT ck_movimiento_saldo_no_negativo CHECK (saldo_posterior >= 0)
);

CREATE INDEX IF NOT EXISTS ix_movimiento_cuenta_fecha
    ON movimiento_contable (cuenta_id, created_at DESC);
CREATE INDEX IF NOT EXISTS ix_movimiento_retencion
    ON movimiento_contable (retencion_id);
