package com.mediconecta.integraciones.simulacion;

/**
 * Permite simular un sistema externo lento para demostrar el timeout de 5
 * segundos y el fallback de cobertura, sin tocar datos ni codigo:
 *
 * <pre>
 * jboss-cli --connect --command="/system-property=mediconecta.simulacion.demoraMs:add(value=8000)"
 * # ... GET /api/pacientes/30111222/cobertura responde a los ~5 s con "contingencia": true
 * jboss-cli --connect --command="/system-property=mediconecta.simulacion.demoraMs:remove"
 * </pre>
 *
 * <p>Solo la usan los sistemas simulados de este paquete; los adaptadores no la
 * conocen.</p>
 */
final class DemoraSimulada {

    static final String PROPIEDAD = "mediconecta.simulacion.demoraMs";

    private DemoraSimulada() {
    }

    static void aplicar() {
        String valor = System.getProperty(PROPIEDAD);
        if (valor == null || valor.isBlank()) {
            return;
        }
        try {
            long ms = Long.parseLong(valor.trim());
            if (ms > 0) {
                Thread.sleep(Math.min(ms, 60_000));
            }
        } catch (NumberFormatException ignorado) {
            // valor invalido: sin demora
        } catch (InterruptedException interrumpido) {
            Thread.currentThread().interrupt();
        }
    }
}
