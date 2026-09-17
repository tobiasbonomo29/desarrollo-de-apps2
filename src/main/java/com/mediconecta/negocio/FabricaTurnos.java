package com.mediconecta.negocio;

import com.mediconecta.modelo.EstadoTurno;
import com.mediconecta.modelo.Paciente;
import com.mediconecta.modelo.Profesional;
import com.mediconecta.modelo.Turno;

import jakarta.enterprise.context.ApplicationScoped;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Patron Factory: unico lugar del sistema donde se construye un {@link Turno}.
 *
 * <p><b>Problema que resuelve.</b> Un turno no queda bien formado con
 * {@code new Turno()}: hay que fijarle el estado inicial, llevar la fecha al
 * formato canonico (sin segundos, porque si no dos textos distintos representan
 * el mismo instante y la deteccion de superposicion falla) y completar la
 * especialidad a partir del profesional. Hoy hay tres caminos distintos que
 * crean turnos: el alta directa por REST, el asistente stateful y la carga de
 * datos de demostracion. Si cada uno armara el objeto a mano, alcanzaria con que
 * uno se olvidara de truncar la fecha para que la regla de superposicion dejara
 * de funcionar, y el bug no se veria hasta la demo.</p>
 *
 * <p><b>Como lo resuelve.</b> Los tres caminos pasan por {@link #crear}. La
 * invariante "todo turno nace PROGRAMADO, con fecha truncada al minuto y con
 * especialidad coherente con su profesional" queda garantizada por construccion
 * en un unico metodo.</p>
 */
@ApplicationScoped
public class FabricaTurnos {

    /**
     * Interpreta el texto de fecha y hora que llego desde afuera.
     *
     * <p>Esta aca y no en la presentacion porque el formato aceptado es una
     * decision del dominio, y porque el mensaje de error tiene que ser el mismo
     * venga el pedido por REST o desde el asistente.</p>
     */
    public LocalDateTime interpretarFecha(String fechaHora) {
        return Fechas.momento(fechaHora);
    }

    /**
     * Crea un turno consistente listo para guardar.
     *
     * @param paciente     paciente ya existente en el sistema
     * @param profesional  profesional ya existente en el sistema
     * @param especialidad especialidad pedida; si viene vacia se toma la del profesional
     * @param fechaHora    momento del turno
     * @param motivo       texto libre opcional
     */
    public Turno crear(Paciente paciente, Profesional profesional, String especialidad,
                       LocalDateTime fechaHora, String motivo) {

        if (paciente == null) {
            throw new ReglaNegocioException("El paciente debe tener nombre y documento.");
        }
        if (profesional == null) {
            throw new ReglaNegocioException("Debe indicar el profesional que atiende.");
        }

        String especialidadFinal = (especialidad == null || especialidad.isBlank())
                ? profesional.getEspecialidad()
                : especialidad.trim();

        if (!especialidadFinal.equalsIgnoreCase(profesional.getEspecialidad())) {
            throw new ReglaNegocioException("La especialidad " + especialidadFinal
                    + " no corresponde al profesional seleccionado.");
        }

        Turno turno = new Turno();
        turno.setPaciente(paciente);
        turno.setProfesional(profesional);
        turno.setEspecialidad(profesional.getEspecialidad());
        turno.setFechaHora(fechaHora.truncatedTo(ChronoUnit.MINUTES));
        turno.setEstado(EstadoTurno.PROGRAMADO);
        turno.setMotivo(motivo == null || motivo.isBlank() ? null : motivo.trim());
        return turno;
    }
}
