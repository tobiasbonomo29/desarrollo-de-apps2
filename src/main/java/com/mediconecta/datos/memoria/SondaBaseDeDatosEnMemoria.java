package com.mediconecta.datos.memoria;

import com.mediconecta.datos.SondaBaseDeDatos;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

/** Sin base de datos (plan B) no hay nada que pueda caerse: siempre responde. */
@Alternative
@ApplicationScoped
public class SondaBaseDeDatosEnMemoria implements SondaBaseDeDatos {

    @Override
    public boolean responde() {
        return true;
    }
}
