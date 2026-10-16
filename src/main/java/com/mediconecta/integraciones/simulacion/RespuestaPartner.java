package com.mediconecta.integraciones.simulacion;

/**
 * Cuerpo JSON que devuelve la API simulada del partner moderno.
 *
 * <p>La usan tanto {@link RecursoPartnerCobertura} (para serializar la
 * respuesta) como {@code com.mediconecta.integraciones.AdaptadorObraSocialPartner}
 * (para deserializarla del lado del cliente REST).</p>
 */
public class RespuestaPartner {

    private boolean vigente;

    private String plan;

    private double copago;

    public RespuestaPartner() {
    }

    public RespuestaPartner(boolean vigente, String plan, double copago) {
        this.vigente = vigente;
        this.plan = plan;
        this.copago = copago;
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
}
