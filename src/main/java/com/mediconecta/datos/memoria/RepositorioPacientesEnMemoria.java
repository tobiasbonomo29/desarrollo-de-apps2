package com.mediconecta.datos.memoria;

import com.mediconecta.datos.RepositorioPacientes;
import com.mediconecta.modelo.Paciente;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Alternative;

import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Implementacion en memoria de {@link RepositorioPacientes}.
 * Desactivada por defecto: ver {@link RepositorioTurnosEnMemoria}.
 */
@Alternative
@ApplicationScoped
public class RepositorioPacientesEnMemoria implements RepositorioPacientes {

    private final Map<Long, Paciente> pacientes = new ConcurrentHashMap<>();

    private final AtomicLong generadorDeId = new AtomicLong(0);

    @Override
    public List<Paciente> listarTodos() {
        return pacientes.values().stream()
                .sorted(Comparator.comparing(Paciente::getNombre))
                .toList();
    }

    @Override
    public Optional<Paciente> buscarPorId(Long id) {
        return id == null ? Optional.empty() : Optional.ofNullable(pacientes.get(id));
    }

    @Override
    public Optional<Paciente> buscarPorDocumento(String documento) {
        return pacientes.values().stream()
                .filter(p -> p.getDocumento().equals(documento))
                .findFirst();
    }

    @Override
    public List<Paciente> buscar(String texto) {
        String buscado = texto.toLowerCase();
        return pacientes.values().stream()
                .filter(p -> p.getNombre().toLowerCase().contains(buscado)
                        || p.getDocumento().contains(buscado))
                .sorted(Comparator.comparing(Paciente::getNombre))
                .toList();
    }

    @Override
    public Paciente guardar(Paciente paciente) {
        paciente.setId(generadorDeId.incrementAndGet());
        pacientes.put(paciente.getId(), paciente);
        return paciente;
    }

    @Override
    public Paciente actualizar(Paciente paciente) {
        pacientes.put(paciente.getId(), paciente);
        return paciente;
    }
}
