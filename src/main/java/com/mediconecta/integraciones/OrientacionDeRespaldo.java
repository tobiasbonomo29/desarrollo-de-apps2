package com.mediconecta.integraciones;

import java.text.Normalizer;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Orientacion por palabras clave: el <b>fallback</b> del gateway de IA.
 *
 * <p>Si el proveedor de IA no esta configurado, falla o no responde dentro de
 * los 5 segundos, el asistente no deja a la persona sin respuesta: busca
 * palabras clave en lo que escribio y sugiere una especialidad de la lista (o
 * Clinica Medica). Mantiene la regla mas importante del asistente: ante un
 * signo de emergencia, marca {@code urgente} y deriva al 107 / 911.</p>
 *
 * <p>Es deliberadamente simple: no reemplaza al modelo, solo evita que su caida
 * corte el flujo de la reserva.</p>
 */
final class OrientacionDeRespaldo {

    static final String CLINICA = "Clinica Medica";

    private static final List<String> EMERGENCIAS = List.of(
            "dolor fuerte en el pecho", "dolor de pecho fuerte", "falta de aire", "no puedo respirar",
            "me ahogo", "desmay", "convulsion", "sangrado abundante", "sangra mucho", "acv",
            "boca torcida", "no puedo mover", "quiero morir", "hacerme dano", "suicid");

    /** Especialidad -> palabras (sin tildes, en minuscula). El orden importa: gana la primera que coincide. */
    private static final Map<String, List<String>> PALABRAS = new LinkedHashMap<>();

    static {
        PALABRAS.put("Pediatria", List.of("mi hijo", "mi hija", "bebe", "nene", "nena", "nino", "nina", "mi chico"));
        PALABRAS.put("Cardiologia", List.of("corazon", "pecho", "palpitacion", "presion alta", "hipertension",
                "taquicardia", "arritmia"));
        PALABRAS.put("Traumatologia", List.of("hueso", "rodilla", "tobillo", "espalda", "lumbar", "cintura",
                "fractura", "esguince", "golpe", "caida", "me cai", "muneca", "hombro", "columna", "cadera"));
        PALABRAS.put("Dermatologia", List.of("piel", "mancha", "lunar", "acne", "picazon", "sarpullido", "granito"));
        PALABRAS.put(CLINICA, List.of("fiebre", "gripe", "tos", "dolor de cabeza", "cansancio", "control",
                "chequeo", "analisis", "estomago", "mareo"));
    }

    private OrientacionDeRespaldo() {
    }

    static SugerenciaEspecialidad orientar(List<MensajeChat> conversacion, List<String> especialidades) {
        StringBuilder relato = new StringBuilder();
        for (MensajeChat mensaje : conversacion) {
            if (MensajeChat.USUARIO.equals(mensaje.getRol())) {
                relato.append(' ').append(normalizar(mensaje.getTexto()));
            }
        }
        String texto = relato.toString();

        SugerenciaEspecialidad sugerencia;
        if (EMERGENCIAS.stream().anyMatch(texto::contains)) {
            sugerencia = new SugerenciaEspecialidad("Lo que contas puede ser una emergencia: no esperes un turno. "
                    + "Llama al 107 (SAME) o al 911, o anda a la guardia mas cercana.", null, true);
        } else {
            String elegida = elegir(texto, especialidades);
            sugerencia = elegida == null
                    ? new SugerenciaEspecialidad("El asistente de IA no esta disponible en este momento. "
                            + "Elegi la especialidad de la lista.", null, false)
                    : new SugerenciaEspecialidad("El asistente de IA no esta disponible en este momento. "
                            + "Por lo que contas, podes sacar turno en " + elegida + ".", elegida, false);
        }
        sugerencia.setContingencia(true);
        return sugerencia;
    }

    private static String elegir(String texto, List<String> especialidades) {
        for (Map.Entry<String, List<String>> entrada : PALABRAS.entrySet()) {
            if (entrada.getValue().stream().anyMatch(texto::contains)) {
                String enLista = GestorOrientacion.especialidadDeLaLista(entrada.getKey(), especialidades);
                if (enLista != null) {
                    return enLista;
                }
            }
        }
        return GestorOrientacion.especialidadDeLaLista(CLINICA, especialidades);
    }

    static String normalizar(String texto) {
        return Normalizer.normalize(texto == null ? "" : texto, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT);
    }
}
