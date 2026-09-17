package com.mediconecta.negocio;

import com.mediconecta.modelo.Turno;

import java.util.List;

/**
 * Foto del estado de la clinica para el tablero de inicio.
 *
 * <p>Lo arma {@link FachadaReservas} juntando datos de los tres componentes
 * stateless. Existe para que la pantalla de inicio se resuelva con <b>una</b>
 * llamada y no con cuatro.</p>
 */
public class ResumenAgenda {

    private long turnosDeHoy;

    private long pacientesActivos;

    private long profesionalesActivos;

    private long pendientes;

    private List<Turno> proximosTurnos;

    public ResumenAgenda() {
    }

    public ResumenAgenda(long turnosDeHoy, long pacientesActivos, long profesionalesActivos,
                         long pendientes, List<Turno> proximosTurnos) {
        this.turnosDeHoy = turnosDeHoy;
        this.pacientesActivos = pacientesActivos;
        this.profesionalesActivos = profesionalesActivos;
        this.pendientes = pendientes;
        this.proximosTurnos = proximosTurnos;
    }

    public long getTurnosDeHoy() {
        return turnosDeHoy;
    }

    public void setTurnosDeHoy(long turnosDeHoy) {
        this.turnosDeHoy = turnosDeHoy;
    }

    public long getPacientesActivos() {
        return pacientesActivos;
    }

    public void setPacientesActivos(long pacientesActivos) {
        this.pacientesActivos = pacientesActivos;
    }

    public long getProfesionalesActivos() {
        return profesionalesActivos;
    }

    public void setProfesionalesActivos(long profesionalesActivos) {
        this.profesionalesActivos = profesionalesActivos;
    }

    public long getPendientes() {
        return pendientes;
    }

    public void setPendientes(long pendientes) {
        this.pendientes = pendientes;
    }

    public List<Turno> getProximosTurnos() {
        return proximosTurnos;
    }

    public void setProximosTurnos(List<Turno> proximosTurnos) {
        this.proximosTurnos = proximosTurnos;
    }
}
