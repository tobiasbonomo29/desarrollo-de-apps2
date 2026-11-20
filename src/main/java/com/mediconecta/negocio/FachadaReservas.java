package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioIdempotencia;
import com.mediconecta.mensajeria.NotificadorTurnos;
import com.mediconecta.modelo.ClaveIdempotencia;
import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Patron Facade: unica puerta de entrada al caso de uso "reservar un turno".
 *
 * <p><b>Problema que resuelve.</b> Reservar un turno no es una operacion de un
 * solo componente. Hay que resolver el paciente (o darlo de alta si es la
 * primera vez), resolver el profesional, verificar que la especialidad sea
 * coherente y recien entonces agendar. Si esa coreografia viviera en
 * {@code RecursoTurnos}, la capa de presentacion tendria que inyectar y ordenar
 * tres componentes de negocio; y como el asistente stateful hace exactamente lo
 * mismo, la secuencia quedaria duplicada en dos lugares que se irian
 * desincronizando.</p>
 *
 * <p><b>Como lo resuelve.</b> La fachada concentra la coordinacion. Los dos
 * clientes -el alta directa por REST y el asistente {@code @Stateful}- llaman a
 * {@link #reservar} y no saben cuantos componentes hay detras. La fachada
 * <b>coordina pero no decide</b>: cada regla la sigue aplicando su dueno
 * ({@code GestionPacientes} valida el padron, {@code GestionTurnos} valida fecha
 * y superposicion). Es la diferencia entre un Facade y un componente que se
 * convierte en el cajon de sastre del sistema.</p>
 */
@Stateless
public class FachadaReservas {

    @Inject
    private GestionTurnos gestionTurnos;

    @Inject
    private GestionPacientes gestionPacientes;

    @Inject
    private GestionProfesionales gestionProfesionales;

    @Inject
    private NotificadorTurnos notificadorTurnos;

    @Inject
    private RepositorioIdempotencia repositorioIdempotencia;

    @Resource
    private SessionContext contexto;

    /** Cuanto tiempo protege una {@code Idempotency-Key} contra reintentos. */
    public static final Duration VIGENCIA_CLAVE = Duration.ofHours(24);

    /** Formato aceptado para la clave: el de un UUID o cualquier token similar. */
    private static final Pattern FORMATO_CLAVE = Pattern.compile("[A-Za-z0-9._:-]{8,100}");

    // ---------- Consultas que alimentan la pantalla de reserva ----------

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<String> especialidades() {
        return gestionProfesionales.listarEspecialidades();
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Profesional> profesionalesDe(String especialidad) {
        return gestionProfesionales.listarPorEspecialidad(especialidad);
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<LocalDateTime> horariosLibres(Long profesionalId, LocalDate fecha) {
        return gestionProfesionales.horariosLibres(profesionalId, fecha);
    }

    /**
     * Estado general de la clinica para el tablero de inicio.
     *
     * <p>Es el otro caso donde la fachada evita que la presentacion orqueste:
     * una sola llamada en lugar de cuatro.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public ResumenAgenda resumenDelDia() {
        LocalDate hoy = LocalDate.now();
        LocalDateTime ahora = LocalDateTime.now();

        List<Turno> deHoy = gestionTurnos.turnosDelDia(hoy);

        long pacientes = gestionPacientes.listarPacientes().stream()
                .filter(Paciente::isActivo)
                .count();

        long profesionales = gestionProfesionales.listarProfesionales().stream()
                .filter(Profesional::isActivo)
                .count();

        List<Turno> proximos = gestionTurnos.listarTurnos().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PROGRAMADO)
                .filter(t -> !t.getFechaHora().isBefore(ahora))
                .sorted(Comparator.comparing(Turno::getFechaHora))
                .limit(6)
                .toList();

        long pendientes = gestionTurnos.listarTurnos().stream()
                .filter(t -> t.getEstado() == EstadoTurno.PROGRAMADO)
                .filter(t -> !t.getFechaHora().isBefore(ahora))
                .count();

        return new ResumenAgenda(
                deHoy.stream().filter(t -> t.getEstado() != EstadoTurno.CANCELADO).count(),
                pacientes, profesionales, pendientes, proximos);
    }

    // ---------- El caso de uso completo ----------

    /**
     * Reserva un turno de punta a punta.
     *
     * <p>Pasos: resolver el paciente (alta al vuelo si el documento todavia no
     * esta en el padron), resolver el profesional (por id o, para compatibilidad
     * con la API original, por nombre) y delegar el alta en
     * {@code GestionTurnos}, que es quien valida fecha y superposicion.</p>
     *
     * <p>Un usuario con rol PACIENTE solo puede reservar para si mismo: la
     * comprobacion la hace {@code GestionPacientes}, que es quien conoce la
     * asociacion entre el usuario autenticado y su ficha.</p>
     *
     * <p><b>Transaccion declarativa de varios pasos.</b> Resolver paciente,
     * resolver profesional, agendar el turno y avisar por JMS son pasos
     * distintos que tienen que confirmarse o deshacerse juntos: si cualquiera
     * de ellos lanza una excepcion de sistema (no una {@link ReglaNegocioException}
     * prevista), el contenedor deshace todo -incluida la creacion del turno en
     * la base- porque {@code @TransactionAttribute(REQUIRED)} hace explicito
     * que este metodo corre dentro de una unica transaccion JTA. Se deja
     * explicito aca porque es el flujo critico que la materia pide demostrar;
     * en el resto de los metodos de este EJB sigue aplicando el REQUIRED por
     * omision, sin necesidad de anotarlo.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public Turno reservar(SolicitudTurno solicitud) {
        if (solicitud == null) {
            throw new ReglaNegocioException("Debe enviar los datos del turno.");
        }

        Paciente paciente = resolverPaciente(solicitud);
        gestionPacientes.exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());
        Profesional profesional = resolverProfesional(solicitud);

        Turno turno = gestionTurnos.agendar(paciente, profesional,
                solicitud.getEspecialidad(), solicitud.getFechaHora(), solicitud.getMotivo());

        notificadorTurnos.turnoConfirmado(turno);

        return turno;
    }

    /**
     * Reserva con proteccion contra reintentos ({@code Idempotency-Key}).
     *
     * <p>Una red inestable o un doble clic pueden hacer que el mismo
     * {@code POST /api/turnos} llegue dos veces. Si el cliente manda la misma
     * clave en los dos, el segundo no crea otro turno: recibe el que se creo con
     * el primero. Reglas (basadas en el borrador IETF
     * "The Idempotency-Key HTTP Header Field"):</p>
     * <ul>
     *   <li>sin clave, es una reserva comun ({@link #reservar});</li>
     *   <li>misma clave y mismo pedido, dentro de {@link #VIGENCIA_CLAVE}: se
     *       devuelve el turno ya creado, marcado como repetido;</li>
     *   <li>misma clave y <b>otro</b> pedido: {@code 422}, es un error del
     *       cliente;</li>
     *   <li>dos pedidos simultaneos con la misma clave: el segundo recibe
     *       {@code 409} y su turno se deshace junto con sus mensajes JMS.</li>
     * </ul>
     *
     * <p>La clave es por usuario: dos usuarios distintos pueden usar la misma sin
     * pisarse, y nadie puede "recuperar" el turno de otro adivinando su clave.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    @TransactionAttribute(TransactionAttributeType.REQUIRED)
    public ResultadoReserva reservarConClave(SolicitudTurno solicitud, String claveRecibida) {
        String clave = normalizarClave(claveRecibida);
        if (clave == null) {
            return new ResultadoReserva(reservar(solicitud), false);
        }
        if (solicitud == null) {
            throw new ReglaNegocioException("Debe enviar los datos del turno.");
        }

        String usuario = contexto.getCallerPrincipal().getName();
        String huella = HuellaSolicitud.de(solicitud);
        LocalDateTime ahora = LocalDateTime.now();

        Optional<ClaveIdempotencia> previa = repositorioIdempotencia.buscar(usuario, clave);
        if (previa.isPresent()) {
            ClaveIdempotencia registrada = previa.get();
            if (registrada.getCreadaEn().isBefore(ahora.minus(VIGENCIA_CLAVE))) {
                repositorioIdempotencia.eliminar(registrada);
            } else if (!registrada.getHuella().equals(huella)) {
                throw new ConflictoIdempotenciaException(ConflictoIdempotenciaException.Motivo.PEDIDO_DISTINTO,
                        "Esa Idempotency-Key ya se uso para otro pedido. Genere una clave nueva para cada turno.");
            } else {
                return new ResultadoReserva(registrada.getTurno(), true);
            }
        }

        Turno turno = reservar(solicitud);
        if (!repositorioIdempotencia.registrar(new ClaveIdempotencia(clave, usuario, huella, turno, ahora))) {
            throw new ConflictoIdempotenciaException(ConflictoIdempotenciaException.Motivo.EN_CURSO,
                    "Otro pedido con la misma Idempotency-Key se esta procesando. Reintente en unos segundos.");
        }
        return new ResultadoReserva(turno, false);
    }

    /**
     * Acepta la clave tal cual o entre comillas (forma de "structured field"
     * de la especificacion). Devuelve {@code null} si no vino.
     */
    static String normalizarClave(String clave) {
        if (clave == null || clave.isBlank()) {
            return null;
        }
        String limpia = clave.trim();
        if (limpia.length() >= 2 && limpia.startsWith("\"") && limpia.endsWith("\"")) {
            limpia = limpia.substring(1, limpia.length() - 1);
        }
        if (!FORMATO_CLAVE.matcher(limpia).matches()) {
            throw new ReglaNegocioException("La cabecera Idempotency-Key debe tener entre 8 y 100 caracteres "
                    + "(letras, numeros, '.', '_', ':' o '-'). Se recomienda un UUID.");
        }
        return limpia;
    }

    private Paciente resolverPaciente(SolicitudTurno solicitud) {
        String documento = solicitud.getDocumentoPaciente();
        if (documento == null || documento.isBlank()) {
            throw new ReglaNegocioException("El paciente debe tener nombre y documento.");
        }

        try {
            return gestionPacientes.buscarPorDocumento(documento);
        } catch (NoEncontradoException noExiste) {
            if (solicitud.getNombrePaciente() == null || solicitud.getNombrePaciente().isBlank()) {
                throw new ReglaNegocioException(
                        "No hay un paciente con documento " + documento
                                + ". Registrelo primero o envie tambien su nombre.");
            }
            Paciente nuevo = new Paciente(solicitud.getNombrePaciente(), documento);
            return gestionPacientes.registrarPaciente(nuevo);
        }
    }

    private Profesional resolverProfesional(SolicitudTurno solicitud) {
        if (solicitud.getProfesionalId() != null) {
            return gestionProfesionales.obtener(solicitud.getProfesionalId());
        }

        String nombre = solicitud.getNombreProfesional();
        if (nombre == null || nombre.isBlank()) {
            throw new ReglaNegocioException("Debe indicar el profesional que atiende.");
        }

        return gestionProfesionales.listarProfesionales().stream()
                .filter(p -> p.getNombre().equalsIgnoreCase(nombre.trim()))
                .findFirst()
                .orElseThrow(() -> new ReglaNegocioException(
                        "No hay ningun profesional llamado " + nombre + "."));
    }
}
