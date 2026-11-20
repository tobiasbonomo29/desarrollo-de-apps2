package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioPacientes;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Turno;

import jakarta.annotation.Resource;
import jakarta.annotation.security.RolesAllowed;
import jakarta.ejb.SessionContext;
import jakarta.ejb.Stateless;
import jakarta.inject.Inject;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Componente 2 - Gestion de Pacientes. EJB <b>stateless</b>.
 *
 * <p>Dueno de las reglas del padron de pacientes: documento obligatorio y unico,
 * nombre obligatorio, y coherencia de los datos que se actualizan.</p>
 */
@Stateless
public class GestionPacientes {

    /**
     * DNI: solo digitos, de 6 a 10, sin puntos (asi lo pide la pantalla de alta).
     * El documento forma parte de la URL de la ficha
     * ({@code /api/pacientes/{documento}}): antes se aceptaba cualquier texto y el
     * escaneo de OWASP ZAP dejo en el padron documentos con {@code <script>} y
     * {@code {}} que ademas provocaban un 500 al armar el {@code Location}.
     * (Correccion incorporada desde el commit 947ba9d en la integracion final.)
     */
    static final Pattern FORMATO_DOCUMENTO = Pattern.compile("[0-9]{6,10}");

    @Inject
    private RepositorioPacientes repositorio;

    @Inject
    private GestionTurnos gestionTurnos;

    @Resource
    private SessionContext contexto;

    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Paciente> listarPacientes() {
        return repositorio.listarTodos();
    }

    @RolesAllowed({Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Paciente> buscar(String texto) {
        if (texto == null || texto.isBlank()) {
            return listarPacientes();
        }
        return repositorio.buscar(texto.trim());
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Paciente buscarPorDocumento(String documento) {
        if (documento == null || documento.isBlank()) {
            throw new ReglaNegocioException("Debe indicar el documento del paciente.");
        }
        return repositorio.buscarPorDocumento(documento.trim())
                .orElseThrow(() -> NoEncontradoException.paciente(documento));
    }

    /**
     * Registra un paciente nuevo.
     *
     * <p>El rol PACIENTE esta habilitado porque el alta al vuelo es parte del
     * flujo de reserva: si alguien pide un turno con un documento que todavia no
     * esta en el padron, {@link FachadaReservas} lo da de alta en el momento.</p>
     */
    /**
     * La ficha de un paciente para mostrarla. A diferencia de
     * {@link #buscarPorDocumento} (que usan los otros componentes), un PACIENTE
     * solo puede consultar la propia: sin este control podria leer la ficha de
     * cualquiera cambiando el documento de la URL (BOLA / IDOR).
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Paciente consultarFicha(String documento) {
        Paciente paciente = buscarPorDocumento(documento);
        exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());
        return paciente;
    }

    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.ADMIN})
    public Paciente registrarPaciente(Paciente paciente) {
        if (paciente == null) {
            throw new ReglaNegocioException("Debe enviar los datos del paciente.");
        }
        if (estaVacio(paciente.getDocumento())) {
            throw new ReglaNegocioException("El documento del paciente es obligatorio.");
        }
        if (estaVacio(paciente.getNombre())) {
            throw new ReglaNegocioException("El nombre del paciente es obligatorio.");
        }

        String documento = paciente.getDocumento().trim();
        if (!FORMATO_DOCUMENTO.matcher(documento).matches()) {
            throw new ReglaNegocioException("El documento debe ser un numero de 6 a 10 digitos, sin puntos.");
        }
        if (repositorio.buscarPorDocumento(documento).isPresent()) {
            throw new ReglaNegocioException("Ya existe un paciente con documento " + documento + ".");
        }

        paciente.setId(null);
        paciente.setDocumento(documento);
        paciente.setNombre(paciente.getNombre().trim());
        paciente.setActivo(true);
        return repositorio.guardar(paciente);
    }

    /**
     * Actualiza los datos de contacto de un paciente.
     *
     * <p>El documento identifica al paciente y no se puede cambiar: cambiarlo
     * seria dar de alta a otra persona.</p>
     */
    @RolesAllowed({Roles.RECEPCION, Roles.ADMIN})
    public Paciente actualizarDatos(String documento, Paciente datos) {
        return actualizarDatos(documento, datos, null);
    }

    /** {@link #actualizarDatos(String, Paciente)} con control de concurrencia optimista (If-Match). */
    @RolesAllowed({Roles.RECEPCION, Roles.ADMIN})
    public Paciente actualizarDatos(String documento, Paciente datos, Long versionEsperada) {
        Paciente existente = buscarPorDocumento(documento);
        VersionDesactualizadaException.exigir(existente.getVersion(), versionEsperada, "paciente");

        if (datos == null) {
            throw new ReglaNegocioException("Debe enviar los datos del paciente.");
        }
        if (estaVacio(datos.getNombre())) {
            throw new ReglaNegocioException("El nombre del paciente es obligatorio.");
        }
        if (!estaVacio(datos.getDocumento()) && !datos.getDocumento().trim().equals(existente.getDocumento())) {
            throw new ReglaNegocioException("El documento de un paciente no se puede modificar.");
        }

        existente.setNombre(datos.getNombre().trim());
        existente.setEmail(datos.getEmail());
        existente.setTelefono(datos.getTelefono());
        existente.setCobertura(datos.getCobertura());
        existente.setActivo(datos.isActivo());
        return repositorio.actualizar(existente);
    }

    /**
     * Historial de turnos de un paciente.
     *
     * <p>Combina las dos formas de autorizacion: la <b>declarativa</b> deja
     * pasar a los cuatro roles, y despues una verificacion <b>programatica</b>
     * comprueba que un usuario con rol PACIENTE solo pueda consultar su propia
     * ficha. Sin esa segunda comprobacion cualquier paciente podria leer el
     * historial de otro cambiando el documento de la URL.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public List<Turno> historialDeTurnos(String documento) {
        Paciente paciente = buscarPorDocumento(documento);
        exigirPropioDocumentoSiEsPaciente(paciente.getDocumento());
        return gestionTurnos.turnosDePaciente(paciente.getDocumento());
    }

    /**
     * Si quien llama tiene <b>unicamente</b> el rol PACIENTE, exige que el
     * documento indicado sea el de su propia ficha.
     *
     * <p>La usan otros componentes que reservan o cancelan turnos a nombre de un
     * paciente puntual (la fachada de reservas, el asistente de reserva, la
     * cancelacion), para que un usuario con rol PACIENTE no pueda operar sobre
     * la ficha de otro cambiando el documento que envia. Los demas roles no
     * tienen esta restriccion: pasan sin ninguna comprobacion.</p>
     *
     * <p>Necesita su propio {@code @RolesAllowed}: al ser un metodo publico
     * invocado desde otro EJB ({@code FachadaReservas}, el asistente stateful),
     * la llamada pasa por el interceptor de seguridad del contenedor igual que
     * cualquier invocacion externa, y un metodo sin anotacion queda denegado por
     * defecto en este servidor.</p>
     */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public void exigirPropioDocumentoSiEsPaciente(String documento) {
        if (!esSoloPaciente()) {
            return;
        }
        String usuario = contexto.getCallerPrincipal().getName();
        boolean esElPropio = repositorio.listarTodos().stream()
                .anyMatch(p -> usuario.equals(p.getUsuario()) && p.getDocumento().equals(documento));
        if (!esElPropio) {
            throw new AccesoDenegadoException("Solo puede operar sobre sus propios datos.");
        }
    }

    private boolean esSoloPaciente() {
        return contexto.isCallerInRole(Roles.PACIENTE)
                && !contexto.isCallerInRole(Roles.RECEPCION)
                && !contexto.isCallerInRole(Roles.MEDICO)
                && !contexto.isCallerInRole(Roles.ADMIN);
    }

    /** Ficha del paciente asociada al usuario que tiene la sesion abierta. */
    @RolesAllowed({Roles.PACIENTE, Roles.RECEPCION, Roles.MEDICO, Roles.ADMIN})
    public Paciente miFicha() {
        String usuario = contexto.getCallerPrincipal().getName();
        return repositorio.listarTodos().stream()
                .filter(p -> usuario.equals(p.getUsuario()))
                .findFirst()
                .orElseThrow(() -> new NoEncontradoException(
                        "El usuario " + usuario + " no tiene una ficha de paciente asociada."));
    }

    private boolean estaVacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
