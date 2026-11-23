package com.onboarding.clientes.cliente.infrastructure.persistence.adapter;

import com.onboarding.clientes.cliente.application.port.SesionRepositorio;
import com.onboarding.clientes.cliente.domain.model.CanalSesion;
import com.onboarding.clientes.cliente.domain.model.MotivoRevocacion;
import com.onboarding.clientes.cliente.domain.model.Sesion;
import com.onboarding.clientes.cliente.infrastructure.persistence.entity.SesionEntity;
import com.onboarding.clientes.cliente.infrastructure.persistence.jpa.SesionSpringDataRepository;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Adaptador JPA del puerto {@link SesionRepositorio}. */
@Component
public class SesionRepositorioAdaptador implements SesionRepositorio {

    private final SesionSpringDataRepository jpa;

    public SesionRepositorioAdaptador(SesionSpringDataRepository jpa) {
        this.jpa = jpa;
    }

    @Override
    public Sesion guardar(Sesion sesion) {
        // saveAndFlush: la restriccion ux_sesion_usuario_vigente debe evaluarse dentro de la transaccion.
        return aDominio(jpa.saveAndFlush(aEntidad(sesion)));
    }

    @Override
    public Optional<Sesion> buscarPorId(UUID id) {
        return jpa.findById(id).map(SesionRepositorioAdaptador::aDominio);
    }

    @Override
    public Optional<Sesion> buscarNoRevocadaPorUsuario(UUID usuarioId) {
        return jpa.findFirstByUsuarioIdAndRevocadaEnIsNull(usuarioId).map(SesionRepositorioAdaptador::aDominio);
    }

    private static SesionEntity aEntidad(Sesion s) {
        SesionEntity e = new SesionEntity();
        e.setId(s.id());
        e.setUsuarioId(s.usuarioId());
        e.setCanal(s.canal().name());
        e.setIpOrigen(s.ipOrigen());
        e.setDispositivo(s.dispositivo());
        e.setCreadaEn(s.creadaEn());
        e.setUltimaActividad(s.ultimaActividad());
        e.setExpiraEn(s.expiraEn());
        e.setRevocadaEn(s.revocadaEn());
        e.setMotivoRevocacion(s.motivoRevocacion() != null ? s.motivoRevocacion().name() : null);
        return e;
    }

    private static Sesion aDominio(SesionEntity e) {
        return Sesion.rehidratar(
                e.getId(),
                e.getUsuarioId(),
                CanalSesion.valueOf(e.getCanal()),
                e.getIpOrigen(),
                e.getDispositivo(),
                e.getCreadaEn(),
                e.getUltimaActividad(),
                e.getExpiraEn(),
                e.getRevocadaEn(),
                e.getMotivoRevocacion() != null ? MotivoRevocacion.valueOf(e.getMotivoRevocacion()) : null);
    }
}
