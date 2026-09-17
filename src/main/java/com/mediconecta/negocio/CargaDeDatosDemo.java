package com.mediconecta.negocio;

import com.mediconecta.datos.RepositorioPacientes;
import com.mediconecta.datos.RepositorioProfesionales;
import com.mediconecta.datos.RepositorioTurnos;
import com.mediconecta.modelo.Disponibilidad;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;

import jakarta.annotation.PostConstruct;
import jakarta.ejb.Singleton;
import jakarta.ejb.Startup;
import jakarta.ejb.TransactionAttribute;
import jakarta.ejb.TransactionAttributeType;
import jakarta.inject.Inject;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.EnumSet;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Carga un juego de datos minimo la primera vez que arranca la aplicacion.
 *
 * <p>Existe para que la demostracion tenga siempre algo que mostrar sin depender
 * de un script SQL externo. Es idempotente: si la base ya tiene datos no toca
 * nada, asi que redesplegar no duplica registros ni pisa lo que se cargo durante
 * la defensa.</p>
 *
 * <p>Escribe a traves de los repositorios y no de los componentes de negocio a
 * proposito: los componentes exigen un rol y aca no hay ningun usuario
 * autenticado, porque el que invoca es el contenedor durante el arranque.</p>
 */
@Singleton
@Startup
@TransactionAttribute(TransactionAttributeType.REQUIRED)
public class CargaDeDatosDemo {

    private static final Logger LOG = Logger.getLogger(CargaDeDatosDemo.class.getName());

    private static final Set<DayOfWeek> LUNES_A_VIERNES = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
            DayOfWeek.THURSDAY, DayOfWeek.FRIDAY);

    private static final Set<DayOfWeek> LUNES_MIERCOLES_VIERNES = EnumSet.of(
            DayOfWeek.MONDAY, DayOfWeek.WEDNESDAY, DayOfWeek.FRIDAY);

    private static final Set<DayOfWeek> MARTES_Y_JUEVES = EnumSet.of(
            DayOfWeek.TUESDAY, DayOfWeek.THURSDAY);

    @Inject
    private RepositorioProfesionales repositorioProfesionales;

    @Inject
    private RepositorioPacientes repositorioPacientes;

    @Inject
    private RepositorioTurnos repositorioTurnos;

    @Inject
    private FabricaTurnos fabricaTurnos;

    @PostConstruct
    void cargar() {
        if (!repositorioProfesionales.listarTodos().isEmpty()) {
            LOG.info("[DEMO] La base ya tiene datos: no se carga el juego de ejemplo.");
            return;
        }

        Profesional laura = crearProfesional("Dra. Laura Fernandez", "Cardiologia", "MP-10234",
                LUNES_A_VIERNES, LocalTime.of(9, 0), LocalTime.of(13, 0), 30);
        Profesional martin = crearProfesional("Dr. Martin Ruiz", "Traumatologia", "MP-20876",
                LUNES_A_VIERNES, LocalTime.of(14, 0), LocalTime.of(18, 0), 30);
        Profesional sofia = crearProfesional("Dra. Sofia Aguirre", "Pediatria", "MP-30512",
                LUNES_MIERCOLES_VIERNES, LocalTime.of(9, 0), LocalTime.of(12, 0), 20);
        crearProfesional("Dr. Julian Paz", "Clinica Medica", "MP-40199",
                MARTES_Y_JUEVES, LocalTime.of(10, 0), LocalTime.of(16, 0), 30);

        Paciente ana = crearPaciente("Ana Gomez", "30111222", "ana.gomez@mail.com",
                "11-4455-6677", "OSDE 210", "paciente");
        Paciente bruno = crearPaciente("Bruno Diaz", "28999888", "bruno.diaz@mail.com",
                "11-5566-7788", "Swiss Medical SMG02", null);
        Paciente carla = crearPaciente("Carla Lopez", "35444555", "carla.lopez@mail.com",
                "11-6677-8899", "Galeno Azul", null);
        crearPaciente("Diego Sosa", "33222111", "diego.sosa@mail.com",
                "11-7788-9900", "PAMI", null);

        agendar(ana, laura, proximoDia(LUNES_A_VIERNES, 1).atTime(9, 30), "Control anual");
        agendar(bruno, martin, proximoDia(LUNES_A_VIERNES, 2).atTime(14, 30), "Dolor lumbar");
        agendar(carla, sofia, proximoDia(LUNES_MIERCOLES_VIERNES, 1).atTime(10, 0), "Control pediatrico");

        LOG.info("[DEMO] Juego de datos de ejemplo cargado: 4 profesionales, 4 pacientes, 3 turnos.");
    }

    private Profesional crearProfesional(String nombre, String especialidad, String matricula,
                                         Set<DayOfWeek> dias, LocalTime desde, LocalTime hasta,
                                         int duracion) {
        Profesional profesional = new Profesional(nombre, especialidad, matricula);
        dias.forEach(dia -> profesional.getDisponibilidades()
                .add(new Disponibilidad(dia, desde, hasta, duracion)));
        return repositorioProfesionales.guardar(profesional);
    }

    private Paciente crearPaciente(String nombre, String documento, String email,
                                   String telefono, String cobertura, String usuario) {
        Paciente paciente = new Paciente(nombre, documento);
        paciente.setEmail(email);
        paciente.setTelefono(telefono);
        paciente.setCobertura(cobertura);
        paciente.setUsuario(usuario);
        return repositorioPacientes.guardar(paciente);
    }

    private void agendar(Paciente paciente, Profesional profesional,
                         java.time.LocalDateTime momento, String motivo) {
        repositorioTurnos.guardar(
                fabricaTurnos.crear(paciente, profesional, profesional.getEspecialidad(), momento, motivo));
    }

    /**
     * Devuelve la enesima fecha futura que caiga en alguno de esos dias, para que
     * los turnos de ejemplo siempre esten dentro de la disponibilidad real del
     * profesional y siempre en el futuro, se despliegue el dia que se despliegue.
     */
    private LocalDate proximoDia(Set<DayOfWeek> dias, int cual) {
        LocalDate fecha = LocalDate.now();
        int encontrados = 0;
        for (int i = 0; i < 30; i++) {
            fecha = fecha.plusDays(1);
            if (dias.contains(fecha.getDayOfWeek())) {
                encontrados++;
                if (encontrados == cual) {
                    return fecha;
                }
            }
        }
        return fecha;
    }
}
