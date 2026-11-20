package com.mediconecta.presentacion;

import com.mediconecta.negocio.SolicitudTurno;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/** Las dos formas del cuerpo de POST /api/turnos terminan en la misma solicitud. */
class PedidoTurnoTest {

    @Test
    void laFormaCortaDeLaInterfaz() {
        PedidoTurno pedido = new PedidoTurno();
        pedido.setDocumentoPaciente("30111222");
        pedido.setProfesionalId(1L);
        pedido.setEspecialidad("Cardiologia");
        pedido.setFechaHora("2026-11-24T09:30");
        pedido.setMotivo("Control");

        SolicitudTurno solicitud = pedido.aSolicitud();
        assertEquals("30111222", solicitud.getDocumentoPaciente());
        assertEquals(1L, solicitud.getProfesionalId());
        assertEquals("Cardiologia", solicitud.getEspecialidad());
        assertEquals("2026-11-24T09:30", solicitud.getFechaHora());
        assertEquals("Control", solicitud.getMotivo());
        assertNull(solicitud.getNombreProfesional());
    }

    @Test
    void laFormaDeLaEntrega1ConPacienteEmbebido() {
        PedidoTurno.PacienteBreve paciente = new PedidoTurno.PacienteBreve();
        paciente.setNombre("Ana Gomez");
        paciente.setDocumento("30111222");
        PedidoTurno pedido = new PedidoTurno();
        pedido.setPaciente(paciente);
        pedido.setProfesional("Dr. Martin Ruiz");

        SolicitudTurno solicitud = pedido.aSolicitud();
        assertEquals("30111222", solicitud.getDocumentoPaciente());
        assertEquals("Ana Gomez", solicitud.getNombrePaciente());
        assertEquals("Dr. Martin Ruiz", solicitud.getNombreProfesional());
        assertEquals("Ana Gomez", pedido.getPaciente().getNombre());
        assertEquals("Dr. Martin Ruiz", pedido.getProfesional());
    }

    @Test
    void siVienenLasDosFormasGanaLaCorta() {
        PedidoTurno.PacienteBreve paciente = new PedidoTurno.PacienteBreve();
        paciente.setDocumento("11111111");
        paciente.setNombre("Nombre Viejo");
        PedidoTurno pedido = new PedidoTurno();
        pedido.setPaciente(paciente);
        pedido.setDocumentoPaciente("30111222");
        pedido.setNombrePaciente("Nombre Nuevo");

        SolicitudTurno solicitud = pedido.aSolicitud();
        assertEquals("30111222", solicitud.getDocumentoPaciente());
        assertEquals("Nombre Nuevo", solicitud.getNombrePaciente());
        assertEquals("30111222", pedido.getDocumentoPaciente());
        assertEquals("Nombre Nuevo", pedido.getNombrePaciente());
        assertNull(pedido.getProfesionalId());
        assertNull(pedido.getEspecialidad());
        assertNull(pedido.getFechaHora());
        assertNull(pedido.getMotivo());
    }
}
