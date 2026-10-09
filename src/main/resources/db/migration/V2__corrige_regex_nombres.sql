-- Los regex de nombres de V1 no incluian el ASCII minusculas (a-z),
-- solo mayusculas (A-Z) y minusculas acentuadas, por lo que valores
-- como "Mendoza" violaban el check constraint.
ALTER TABLE cliente DROP CONSTRAINT ck_cliente_nombre_letras;
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_nombre_letras
    CHECK (nombre ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+([[:space:]][A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*$');

ALTER TABLE cliente DROP CONSTRAINT ck_cliente_segundo_nombre_letras;
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_segundo_nombre_letras
    CHECK (segundo_nombre IS NULL OR segundo_nombre ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+([[:space:]][A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*$');

ALTER TABLE cliente DROP CONSTRAINT ck_cliente_apellido_paterno_letras;
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_apellido_paterno_letras
    CHECK (apellido_paterno ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+([[:space:]][A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*$');

ALTER TABLE cliente DROP CONSTRAINT ck_cliente_apellido_materno_letras;
ALTER TABLE cliente ADD CONSTRAINT ck_cliente_apellido_materno_letras
    CHECK (apellido_materno ~ '^[A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+([[:space:]][A-Za-zÁÉÍÓÚÜÑáéíóúüñ]+)*$');
