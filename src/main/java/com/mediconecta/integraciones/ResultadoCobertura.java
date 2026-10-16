package com.mediconecta.integraciones;

/**
 * Resultado uniforme de validar la cobertura de un paciente, sin importar si
 * detras se consulto el sistema legado (SOAP) o el partner moderno (REST).
 */
public class ResultadoCobertura {

    private boolean vigente;

    private String plan;

    private double copago;

    /** De donde vino el dato: {@code LEGADO}, {@code PARTNER} o {@code NINGUNA}. */
    private String origen;

    public ResultadoCobertura() {
    }

    public ResultadoCobertura(boolean vigente, String plan, double copago, String origen) {
        this.vigente = vigente;
        this.plan = plan;
        this.copago = copago;
        this.origen = origen;
    }

    public boolean isVigente() {
        return vigente;
    }

    public void setVigente(boolean vigente) {
        this.vigente = vigente;
    }

    public String getPlan() {
        return plan;
    }

    public void setPlan(String plan) {
        this.plan = plan;
    }

    public double getCopago() {
        return copago;
    }

    public void setCopago(double copago) {
        this.copago = copago;
    }

    public String getOrigen() {
        return origen;
    }

    public void setOrigen(String origen) {
        this.origen = origen;
    }
}
