// =============================================================================
//  MediConecta - prueba de carga (k6)
// =============================================================================
//
//  Simula a recepcion y pacientes usando la aplicacion a la vez: agenda, padron,
//  profesionales, horarios libres, fichas y salud. Criterio de aceptacion de
//  los Criterios de Testing: p95 < 500 ms y menos de 1 % de errores.
//
//      docker run --rm -i --network host -e BASE_URL=http://localhost:8080/mediconecta \
//          -e CLAVE=Demo.2026 grafana/k6:0.54.0 run - < pruebas/carga/carga.js
//
//  (en macOS con Docker Desktop: BASE_URL=http://host.docker.internal:8080/mediconecta
//   y sin --network host)
//
//  Si algun umbral no se cumple, k6 termina con codigo distinto de 0 y el job
//  de CI falla.
// =============================================================================

import http from 'k6/http';
import { check, group, sleep } from 'k6';

const BASE = __ENV.BASE_URL || 'http://localhost:8080/mediconecta';
const CLAVE = __ENV.CLAVE || 'Demo.2026';

export const options = {
    scenarios: {
        uso_normal: {
            executor: 'ramping-vus',
            startVUs: 1,
            stages: [
                { duration: '10s', target: 20 },
                { duration: '30s', target: 20 },
                { duration: '5s', target: 0 },
            ],
        },
    },
    thresholds: {
        http_req_duration: ['p(95)<500'],
        http_req_failed: ['rate<0.01'],
        checks: ['rate>0.99'],
    },
};

function login(usuario) {
    const r = http.post(`${BASE}/api/auth/login`, JSON.stringify({ usuario, clave: CLAVE }),
        { headers: { 'Content-Type': 'application/json' } });
    if (r.status !== 200) {
        throw new Error(`login de ${usuario} fallo: ${r.status} ${r.body}`);
    }
    return r.json('token');
}

// Una sola vez: los tokens y datos de referencia que comparten todos los usuarios virtuales.
export function setup() {
    const recepcion = login('recepcion');
    const paciente = login('paciente');
    const auth = { headers: { Authorization: `Bearer ${recepcion}` } };
    const profesionales = http.get(`${BASE}/api/profesionales`, auth).json();
    return { recepcion, paciente, profesionalId: profesionales[0].id };
}

function manana() {
    const d = new Date(Date.now() + 24 * 3600 * 1000);
    return d.toISOString().substring(0, 10);
}

export default function (datos) {
    const recepcion = { headers: { Authorization: `Bearer ${datos.recepcion}` }, tags: { rol: 'recepcion' } };
    const paciente = { headers: { Authorization: `Bearer ${datos.paciente}` }, tags: { rol: 'paciente' } };

    group('recepcion', () => {
        check(http.get(`${BASE}/api/turnos`, recepcion), { 'agenda 200': (r) => r.status === 200 });
        check(http.get(`${BASE}/api/pacientes?q=go`, recepcion), { 'padron 200': (r) => r.status === 200 });
        check(http.get(`${BASE}/api/tablero`, recepcion), { 'tablero 200': (r) => r.status === 200 });
    });

    group('paciente', () => {
        check(http.get(`${BASE}/api/profesionales`, paciente), { 'profesionales 200': (r) => r.status === 200 });
        check(http.get(`${BASE}/api/profesionales/${datos.profesionalId}/horarios-libres?fecha=${manana()}`, paciente),
            { 'horarios 200': (r) => r.status === 200 });
        check(http.get(`${BASE}/api/pacientes/mi-ficha`, paciente), { 'ficha 200': (r) => r.status === 200 });
    });

    check(http.get(`${BASE}/api/salud`), { 'salud 200': (r) => r.status === 200 });
    sleep(0.5);
}
