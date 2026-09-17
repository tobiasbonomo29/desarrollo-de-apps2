package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioTurnos;
import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Turno;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion en memoria de {@link RepositorioTurnos}, heredada de la
 * primera version del proyecto.
 *
 * <p><b>Esta clase esta desactivada.</b> Lleva {@code @Alternative} y no figura
 * en {@code beans.xml}, asi que CDI no la usa: el sistema corre sobre
 * {@code RepositorioTurnosJpa}. Se conserva por dos motivos:</p>
 *
 * <ol>
 *   <li>es la evidencia concreta de que el patron DAO funciona: dos
 *       implementaciones del mismo contrato conviven en el proyecto y se elige
 *       una sin tocar negocio ni presentacion;</li>
 *   <li>sirve de plan B para levantar la aplicacion en una maquina sin
 *       PostgreSQL (ver README, seccion "Ejecutar sin base de datos").</li>
 * </ol>
 *
 * <p>Usa {@code ConcurrentHashMap} y {@code AtomicLong} porque el contenedor
 * atiende varios requests en paralelo sobre esta misma instancia compartida.</p>
 */
@Alternative
@ApplicationScoped
public class RepositorioTurnosEnMemoria implements RepositorioTurnos {

    private final Map<Long, Turno> turnos = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    @Override
    public List<Turno> listarTodos() {
        return turnos.values().stream()
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .toList();
    }

    @Override
    public List<Turno> listarAgenda() {
        return turnos.values().stream()
                .filter(t -> !t.isArchivado())
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .toList();
    }

    @Override
    public Optional<Turno> buscarPorId(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(turnos.get(id));
    }

    @Override
    public Turno guardar(Turno turno) {
        turno.setId(generadorDeId.incrementAndGet());
        turnos.put(turno.getId(), turno);
        return turno;
    }

    @Override
    public Turno actualizar(Turno turno) {
        turnos.put(turno.getId(), turno);
        return turno;
    }

    @Override
    public boolean existeTurnoVigente(Long profesionalId, LocalDateTime fechaHora) {
        return turnos.values().stream()
                .filter(t -> t.getEstado() != EstadoTurno.CANCELADO)
                .anyMatch(t -> t.getProfesional().getId().equals(profesionalId)
                        && t.getFechaHora().equals(fechaHora));
    }

    @Override
    public List<Turno> listarPorPaciente(String documento) {
        return turnos.values().stream()
                .filter(t -> t.getPaciente().getDocumento().equals(documento))
                .sorted(Comparator.comparing(Turno::getFechaHora).reversed())
                .toList();
    }

    @Override
    public List<Turno> listarPorProfesionalYFecha(Long profesionalId, LocalDate fecha) {
        return turnos.values().stream()
                .filter(t -> t.getProfesional().getId().equals(profesionalId))
                .filter(t -> t.getFechaHora().toLocalDate().equals(fecha))
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .toList();
    }

    @Override
    public List<Turno> listarPorFecha(LocalDate fecha) {
        return turnos.values().stream()
                .filter(t -> t.getFechaHora().toLocalDate().equals(fecha))
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .toList();
    }
}
