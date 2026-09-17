package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioProfesionales;
import com.mediconecta.modelo.Profesional;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion en memoria de {@link RepositorioProfesionales}.
 * Desactivada por defecto: ver {@link RepositorioTurnosEnMemoria}.
 */
@Alternative
@ApplicationScoped
public class RepositorioProfesionalesEnMemoria implements RepositorioProfesionales {

    private final Map<Long, Profesional> profesionales = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    @Override
    public List<Profesional> listarTodos() {
        return profesionales.values().stream()
                .sorted(Comparator.comparing(Profesional::getNombre))
                .toList();
    }

    @Override
    public Optional<Profesional> buscarPorId(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(profesionales.get(id));
    }

    @Override
    public Optional<Profesional> buscarPorMatricula(String matricula) {
        return profesionales.values().stream()
                .filter(p -> p.getMatricula().equals(matricula))
                .findFirst();
    }

    @Override
    public Optional<Profesional> buscarPorUsuario(String usuario) {
        if (usuario == null) {
            return Optional.empty();
        }
        return profesionales.values().stream()
                .filter(p -> usuario.equals(p.getUsuario()))
                .findFirst();
    }

    @Override
    public List<Profesional> listarPorEspecialidad(String especialidad) {
        return profesionales.values().stream()
                .filter(p -> p.getEspecialidad().equalsIgnoreCase(especialidad))
                .sorted(Comparator.comparing(Profesional::getNombre))
                .toList();
    }

    @Override
    public List<String> listarEspecialidades() {
        return profesionales.values().stream()
                .filter(Profesional::isActivo)
                .map(Profesional::getEspecialidad)
                .distinct()
                .sorted()
                .toList();
    }

    @Override
    public Profesional guardar(Profesional profesional) {
        profesional.setId(generadorDeId.incrementAndGet());
        profesionales.put(profesional.getId(), profesional);
        return profesional;
    }

    @Override
    public Profesional actualizar(Profesional profesional) {
        profesionales.put(profesional.getId(), profesional);
        return profesional;
    }
}
