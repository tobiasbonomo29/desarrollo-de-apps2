package com.mediconecta.turnos.datos;

import com.mediconecta.turnos.modelo.EstadoTurno;
import com.mediconecta.turnos.modelo.Paciente;
import com.mediconecta.turnos.modelo.Turno;

import jakarta.annotation.PostConstruct;
import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion en memoria del repositorio de turnos.
 *
 * <p>En esta entrega no usamos base de datos externa para no bloquear el
 * despliegue: los turnos viven en un {@link ConcurrentHashMap}. La consecuencia
 * conocida y aceptada es que <b>los datos se pierden al reiniciar WildFly</b>,
 * lo cual es suficiente para demostrar la separacion en capas.</p>
 *
 * <p>Es {@code @ApplicationScoped} (una unica instancia por aplicacion) porque
 * el Map <i>es</i> el almacen de datos: si hubiera una instancia por request,
 * cada llamada veria un almacen vacio. Ojo con la distincion que suele
 * preguntarse en la defensa: <b>el repositorio guarda estado de aplicacion
 * (los datos), pero el componente de negocio no guarda estado de conversacion
 * con el cliente</b>; por eso el negocio sigue siendo stateless.</p>
 *
 * <p>Se usan {@link ConcurrentHashMap} y {@link AtomicLong} y no
 * {@code HashMap}/{@code long} porque el contenedor atiende varios requests en
 * paralelo, en hilos distintos, sobre esta misma instancia compartida.</p>
 */
@ApplicationScoped
public class RepositorioTurnosEnMemoria implements RepositorioTurnos {

    /** Almacen de turnos indexado por id. Reemplaza a la tabla de la base de datos. */
    private final Map<Long, Turno> turnos = new ConcurrentHashMap<>();

    /** Generador de ids. Cumple el rol de la secuencia / auto-increment de la base. */
    private final AtomicLong generadorDeId = new AtomicLong(0);

    /**
     * Carga turnos de ejemplo apenas se crea el repositorio, para que la demo
     * muestre datos reales sin tener que cargarlos a mano.
     *
     * <p>Las fechas se calculan relativas al dia de hoy (y no fijas en el
     * codigo) para que los turnos de ejemplo siempre queden en el futuro y
     * nunca choquen con la regla de negocio que prohibe turnos en el pasado.</p>
     */
    @PostConstruct
    void cargarDatosDeEjemplo() {
        guardarComoProgramado(new Turno(
                new Paciente("Ana Gomez", "30111222"),
                "Dra. Laura Fernandez", "Cardiologia", dentroDe(1, 9, 30)));

        guardarComoProgramado(new Turno(
                new Paciente("Bruno Diaz", "28999888"),
                "Dr. Martin Ruiz", "Traumatologia", dentroDe(2, 11, 0)));

        guardarComoProgramado(new Turno(
                new Paciente("Carla Lopez", "35444555"),
                "Dra. Laura Fernandez", "Cardiologia", dentroDe(3, 15, 15)));
    }

    @Override
    public List<Turno> listarTodos() {
        // Se devuelve una copia y no el Map interno para que nadie desde afuera
        // pueda modificar el almacen sin pasar por este repositorio.
        return new ArrayList<>(turnos.values());
    }

    @Override
    public Optional<Turno> buscarPorId(Long id) {
        return Optional.ofNullable(turnos.get(id));
    }

    @Override
    public Turno guardar(Turno turno) {
        turno.setId(generadorDeId.incrementAndGet());
        turnos.put(turno.getId(), turno);
        return turno;
    }

    @Override
    public boolean existeTurnoDe(String profesional, String fechaHora) {
        return turnos.values().stream()
                .filter(t -> t.getEstado() != EstadoTurno.CANCELADO)
                .anyMatch(t -> t.getProfesional().equalsIgnoreCase(profesional)
                        && t.getFechaHora().equals(fechaHora));
    }

    /** Guarda un turno de ejemplo ya en estado PROGRAMADO. */
    private void guardarComoProgramado(Turno turno) {
        turno.setEstado(EstadoTurno.PROGRAMADO);
        guardar(turno);
    }

    /**
     * Construye una fecha a N dias de hoy, a la hora indicada, ya escrita en el
     * formato canonico {@link Turno#FORMATO_FECHA_HORA} para que las semillas
     * sean comparables con los turnos que llegan por la API.
     */
    private String dentroDe(int dias, int hora, int minuto) {
        LocalDateTime momento = LocalDateTime.of(LocalDate.now().plusDays(dias), LocalTime.of(hora, minuto));
        return momento.format(Turno.FORMATO_FECHA_HORA);
    }
}
