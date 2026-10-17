-- V3: rol del usuario de acceso.
--
-- Base del control de autorizacion de la API (prevencion de IDOR):
--   CLIENTE  -> solo consulta y modifica sus propios recursos (es el valor por defecto)
--   ADMIN    -> puede consultar y administrar los recursos de cualquier cliente
--
-- Para promover a administrador a un usuario existente basta con:
--   UPDATE usuario SET rol = 'ADMIN' WHERE correo_electronico = '...';

ALTER TABLE usuario ADD COLUMN rol varchar(20) NOT NULL DEFAULT 'CLIENTE';
ALTER TABLE usuario ADD CONSTRAINT ck_usuario_rol CHECK (rol IN ('CLIENTE', 'ADMIN'));
CREATE INDEX ix_usuario_rol ON usuario (rol);

COMMENT ON COLUMN usuario.rol IS 'Rol de autorizacion: CLIENTE (solo sus recursos) o ADMIN (todos los recursos).';
