package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioTurnos;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Componente 1 - Gestion de Turnos. EJB <b>stateless</b>.
 *
 * <p>Es el dueno de las reglas de la agenda. No conserva nada entre invocaciones:
 * cada metodo recibe en sus parametros todo lo que necesita, resuelve y termina.
 * Por eso dos requests del mismo usuario pueden ser atendidos por instancias
 * distintas del pool sin ninguna diferencia observable.</p>
 *
 * <p>La autorizacion se declara aca, sobre los metodos de negocio, y la aplica el
 * contenedor de EJB. No se declara en la capa de presentacion: asi la regla vale
 * aunque manana el mismo metodo se invoque desde SOAP o desde una cola.</p>
 */
@Stateless
public class GestionTurnos {

    @Inject
    private RepositorioTurnos repositorio;

    @Inject
    private FabricaTurnos fabricaTurnos;

    @Resource
    private SessionContext contexto;

    /**
     * Agenda operativa, ordenada cronologicamente.
     *
     * <p>Un PACIENTE no puede verla: solo accede a su propio historial a traves
     * de {@code GestionPacientes.historialDeTurnos}. No incluye los turnos
     * archivados: para eso esta {@code RepositorioTurnos.listarAgenda}.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> listarTurnos() {
        return repositorio.listarAgenda();
    }

    /**
     * Un turno por id. Un PACIENTE solo puede consultar los suyos: sin este
     * control, cualquier paciente podria recorrer los ids y leer los turnos de
     * los demas (IDOR).
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Turno obtenerTurno(Long id) {
        Turno turno = buscar(id);
        if (esSoloPaciente()) {
            String usuario = contexto.getCallerPrincipal().getName();
            if (!usuario.equals(turno.getPaciente().getUsuario())) {
                throw new AccesoDenegadoException("Solo puede consultar sus propios turnos.");
            }
        }
        return turno;
    }

    private Turno buscar(Long id) {
        return repositorio.buscarPorId(id)
                .orElseThrow(() -> NoEncontradoException.turno(id));
    }

    /** Turnos de un dia, para el tablero de inicio. */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> turnosDelDia(LocalDate fecha) {
        return repositorio.listarPorFecha(fecha);
    }

    /** Historial de un paciente. Lo consume {@code GestionPacientes}. */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> turnosDePaciente(String documento) {
        return repositorio.listarPorPaciente(documento);
    }

    /**
     * Horarios que un profesional ya tiene tomados en una fecha.
     *
     * <p>Devuelve solo momentos, sin datos del paciente, porque lo consume
     * {@code GestionProfesionales} para calcular los horarios libres y esa
     * consulta esta disponible para cualquier rol.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<LocalDateTime> horariosOcupados(Long profesionalId, LocalDate fecha) {
        return repositorio.listarPorProfesionalYFecha(profesionalId, fecha).stream()
                .filter(Turno::ocupaHorario)
                .map(Turno::getFechaHora)
                .toList();
    }

    /**
     * Da de alta un turno aplicando todas las reglas de la agenda.
     *
     * <p>Recibe el paciente y el profesional ya resueltos: quien los busca es
     * {@link FachadaReservas}, que es el componente encargado de coordinar. Aca
     * viven unicamente las reglas propias del turno.</p>
     *
     * @throws ReglaNegocioException si la fecha no es valida, esta en el pasado
     *                               o el profesional ya tiene ese horario tomado
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    public Turno agendar(Paciente paciente, Profesional profesional,
                         String especialidad, String fechaHoraTexto, String motivo) {

        LocalDateTime momento = fabricaTurnos.interpretarFecha(fechaHoraTexto);

        if (momento.isBefore(LocalDateTime.now())) {
            throw new ReglaNegocioException("No se puede agendar un turno en el pasado.");
        }

        Turno turno = fabricaTurnos.crear(paciente, profesional, especialidad, momento, motivo);

        if (repositorio.existeTurnoVigente(profesional.getId(), turno.getFechaHora())) {
            throw new ReglaNegocioException(
                    "El profesional " + profesional.getNombre() + " ya tiene un turno en ese horario.");
        }

        return repositorio.guardar(turno);
    }

    /**
     * Cancela un turno.
     *
     * <p>Reglas: el turno debe existir; no se puede cancelar dos veces; y no se
     * puede cancelar uno ya atendido. Al quedar en {@code CANCELADO} su horario
     * vuelve a estar libre, porque {@code existeTurnoVigente} ignora los
     * cancelados.</p>
     *
     * <p>Ademas de la autorizacion declarativa, hay ownership: un PACIENTE solo
     * puede cancelar turnos propios y un MEDICO solo los de su propia agenda.
     * RECEPCION y ADMIN pueden cancelar cualquiera.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Turno cancelarTurno(Long id) {
        return cancelarTurno(id, null);
    }

    /**
     * Igual que {@link #cancelarTurno(Long)}, con control de concurrencia
     * optimista: si {@code versionEsperada} no es la version actual (otro
     * usuario lo modifico despues de que el cliente lo leyo), no se toca y se
     * responde 412. La version llega en el ETag ({@code If-Match}).
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Turno cancelarTurno(Long id, Long versionEsperada) {
        Turno turno = buscar(id);
        VersionDesactualizadaException.exigir(turno.getVersion(), versionEsperada, "turno");

        exigirPuedeCancelar(turno);

        // Patron State: el estado actual decide si admite la cancelacion. Si
        // el turno ya estaba cancelado o atendido lanza
        // TransicionInvalidaException, que la API devuelve como 409.
        turno.cancelar();
        return repositorio.actualizar(turno);
    }

    /**
     * Registra que el paciente fue atendido: PROGRAMADO -> ATENDIDO.
     *
     * <p>Lo hace el MEDICO (solo sobre su propia agenda) o un ADMIN. Si el turno
     * no esta PROGRAMADO, el estado rechaza la transicion con un 409.</p>
     */
    @RolesAllowed({Roles.MEDICO, Roles.ADMIN})
    public Turno atenderTurno(Long id) {
        return atenderTurno(id, null);
    }

    /** {@link #atenderTurno(Long)} con control de concurrencia optimista (If-Match). */
    @RolesAllowed({Roles.MEDICO, Roles.ADMIN})
    public Turno atenderTurno(Long id, Long versionEsperada) {
        Turno turno = buscar(id);
        VersionDesactualizadaException.exigir(turno.getVersion(), versionEsperada, "turno");

        if (esSoloMedico()) {
            String usuario = contexto.getCallerPrincipal().getName();
            if (!usuario.equals(turno.getProfesional().getUsuario())) {
                throw new AccesoDenegadoException("Solo puede atender turnos de su propia agenda.");
            }
        }

        turno.atender();
        return repositorio.actualizar(turno);
    }

    private void exigirPuedeCancelar(Turno turno) {
        if (esSoloPaciente()) {
            String usuario = contexto.getCallerPrincipal().getName();
            if (!usuario.equals(turno.getPaciente().getUsuario())) {
                throw new AccesoDenegadoException("Solo puede cancelar sus propios turnos.");
            }
            return;
        }
        if (esSoloMedico()) {
            String usuario = contexto.getCallerPrincipal().getName();
            if (!usuario.equals(turno.getProfesional().getUsuario())) {
                throw new AccesoDenegadoException("Solo puede cancelar turnos de su propia agenda.");
            }
        }
        // RECEPCION y ADMIN pueden cancelar cualquier turno, sin restriccion adicional.
    }

    private boolean esSoloPaciente() {
        return contexto.isCallerInRole(Roles.PACIENTE)
                && !contexto.isCallerInRole(Roles.RECEPCION)
                && !contexto.isCallerInRole(Roles.MEDICO)
                && !contexto.isCallerInRole(Roles.ADMIN);
    }

    private boolean esSoloMedico() {
        return contexto.isCallerInRole(Roles.MEDICO)
                && !contexto.isCallerInRole(Roles.RECEPCION)
                && !contexto.isCallerInRole(Roles.ADMIN);
    }

    /**
     * Saca un turno cancelado de la agenda operativa sin borrarlo.
     *
     * <p>Es un archivado logico: el registro sigue existiendo en la base (sigue
     * apareciendo en el historial del paciente y se lo puede seguir consultando
     * por id) pero deja de listarse en {@link #listarTurnos()}. Solo se puede
     * archivar un turno que ya este {@code CANCELADO}, y no dos veces.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.ADMIN})
    public Turno archivarTurno(Long id) {
        return archivarTurno(id, null);
    }

    /** {@link #archivarTurno(Long)} con control de concurrencia optimista (If-Match). */
    @RolesAllowed({Roles.RECEPCION, Roles.ADMIN})
    public Turno archivarTurno(Long id, Long versionEsperada) {
        Turno turno = buscar(id);
        VersionDesactualizadaException.exigir(turno.getVersion(), versionEsperada, "turno");

        // Solo TurnoCancelado acepta archivar, y una unica vez (409 si no).
        turno.archivar();
        return repositorio.actualizar(turno);
    }
}
