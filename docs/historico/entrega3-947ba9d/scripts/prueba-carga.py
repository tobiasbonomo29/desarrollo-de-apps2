#!/usr/bin/env python3
"""
MediConecta - prueba de carga de la API (Entrega final, criterio p95 < 500 ms).

Solo usa la biblioteca estandar de Python 3 (no hace falta instalar nada).
Cada usuario virtual inicia sesion una vez (JWT) y despues repite, durante la
duracion indicada, la mezcla de operaciones de una recepcion:

    GET /api/turnos            agenda
    GET /api/turnos/{id}       detalle de un turno
    GET /api/profesionales     padron
    GET /api/tablero           pantalla de inicio (fachada, 4 componentes)
    GET /api/pacientes?q=...   busqueda de pacientes
    POST /api/turnos           (opcional, --escrituras) alta con Idempotency-Key

Informa cantidad de pedidos, errores, throughput y percentiles p50/p95/p99, y
termina con codigo 1 si el p95 supera el umbral o si hay mas de 1 % de errores.

Uso:
    python scripts/carga/prueba-carga.py
    python scripts/carga/prueba-carga.py --url http://localhost:8080/mediconecta \
        --usuarios 20 --segundos 60 --umbral-p95 500 --salida resultado.json
"""
import argparse
import json
import random
import statistics
import sys
import threading
import time
import urllib.error
import urllib.request
import uuid


def pedir(url, metodo="GET", cuerpo=None, token=None, cabeceras=None):
    datos = None if cuerpo is None else json.dumps(cuerpo).encode("utf-8")
    pedido = urllib.request.Request(url, data=datos, method=metodo)
    pedido.add_header("Accept", "application/json")
    if datos is not None:
        pedido.add_header("Content-Type", "application/json")
    if token:
        pedido.add_header("Authorization", "Bearer " + token)
    for clave, valor in (cabeceras or {}).items():
        pedido.add_header(clave, valor)
    inicio = time.perf_counter()
    try:
        with urllib.request.urlopen(pedido, timeout=10) as respuesta:
            contenido = respuesta.read()
            codigo = respuesta.status
    except urllib.error.HTTPError as error:
        contenido = error.read()
        codigo = error.code
    except Exception:  # timeout, conexion rechazada
        contenido = b""
        codigo = 0
    return codigo, (time.perf_counter() - inicio) * 1000, contenido


def percentil(valores, p):
    if not valores:
        return 0.0
    ordenados = sorted(valores)
    k = (len(ordenados) - 1) * p / 100
    piso = int(k)
    techo = min(piso + 1, len(ordenados) - 1)
    return ordenados[piso] + (ordenados[techo] - ordenados[piso]) * (k - piso)


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--url", default="http://127.0.0.1:8080/mediconecta")
    parser.add_argument("--usuario", default="recepcion")
    parser.add_argument("--clave", default="Demo.2026")
    parser.add_argument("--usuarios", type=int, default=20, help="usuarios virtuales concurrentes")
    parser.add_argument("--segundos", type=int, default=60)
    parser.add_argument("--umbral-p95", type=float, default=500.0, help="milisegundos")
    parser.add_argument("--escrituras", action="store_true", help="incluir altas de turnos (POST)")
    parser.add_argument("--salida", help="guardar el resultado en JSON")
    args = parser.parse_args()
    api = args.url.rstrip("/") + "/api"

    codigo, _, cuerpo = pedir(api + "/auth/login", "POST", {"usuario": args.usuario, "clave": args.clave})
    if codigo != 200:
        print(f"No se pudo iniciar sesion ({codigo}): {cuerpo[:200]!r}")
        return 2
    token_inicial = json.loads(cuerpo)["token"]
    _, _, turnos = pedir(api + "/turnos", token=token_inicial)
    ids = [t["id"] for t in json.loads(turnos)] or [1]
    _, _, profesionales = pedir(api + "/profesionales", token=token_inicial)
    profesional = json.loads(profesionales)[0]["id"]

    operaciones = [
        ("GET /api/turnos", lambda t: pedir(api + "/turnos", token=t)),
        ("GET /api/turnos/{id}", lambda t: pedir(f"{api}/turnos/{random.choice(ids)}", token=t)),
        ("GET /api/profesionales", lambda t: pedir(api + "/profesionales", token=t)),
        ("GET /api/tablero", lambda t: pedir(api + "/tablero", token=t)),
        ("GET /api/pacientes?q=", lambda t: pedir(api + "/pacientes?q=" + random.choice(["ana", "diaz", "lo"]), token=t)),
    ]
    if args.escrituras:
        def alta(t):
            dia = time.strftime("%Y-%m-%d", time.localtime(time.time() + 86400 * random.randint(30, 300)))
            cuerpo_alta = {"documentoPaciente": "30111222", "profesionalId": profesional,
                           "fechaHora": f"{dia}T{random.choice(['09', '10', '11', '12'])}:{random.choice(['00', '30'])}",
                           "motivo": "carga"}
            return pedir(api + "/turnos", "POST", cuerpo_alta, t, {"Idempotency-Key": str(uuid.uuid4())})
        operaciones.append(("POST /api/turnos", alta))

    resultados = []          # (operacion, codigo, ms)
    candado = threading.Lock()
    fin = time.time() + args.segundos

    def usuario_virtual():
        codigo_login, _, cuerpo_login = pedir(api + "/auth/login", "POST",
                                              {"usuario": args.usuario, "clave": args.clave})
        token = json.loads(cuerpo_login)["token"] if codigo_login == 200 else token_inicial
        while time.time() < fin:
            nombre, operacion = random.choice(operaciones)
            codigo_op, ms, _ = operacion(token)
            with candado:
                resultados.append((nombre, codigo_op, ms))

    hilos = [threading.Thread(target=usuario_virtual) for _ in range(args.usuarios)]
    inicio = time.time()
    for hilo in hilos:
        hilo.start()
    for hilo in hilos:
        hilo.join()
    duracion = time.time() - inicio

    def es_error(nombre, codigo_op):
        # Un 400 en un alta aleatoria (horario ocupado o fuera de agenda) es una regla de negocio, no una falla.
        return codigo_op == 0 or codigo_op >= 500 or (codigo_op >= 400 and not nombre.startswith("POST"))

    tiempos = [ms for _, _, ms in resultados]
    errores = sum(1 for n, c, _ in resultados if es_error(n, c))
    resumen = {
        "url": args.url, "usuarios": args.usuarios, "segundos": round(duracion, 1),
        "pedidos": len(resultados), "errores": errores,
        "porcentaje_errores": round(100 * errores / max(1, len(resultados)), 2),
        "pedidos_por_segundo": round(len(resultados) / duracion, 1),
        "p50_ms": round(percentil(tiempos, 50), 1), "p95_ms": round(percentil(tiempos, 95), 1),
        "p99_ms": round(percentil(tiempos, 99), 1),
        "promedio_ms": round(statistics.mean(tiempos), 1) if tiempos else 0,
        "umbral_p95_ms": args.umbral_p95,
        "por_operacion": {},
    }
    for nombre, _ in operaciones:
        propios = [ms for n, _, ms in resultados if n == nombre]
        resumen["por_operacion"][nombre] = {
            "pedidos": len(propios), "p95_ms": round(percentil(propios, 95), 1),
            "errores": sum(1 for n, c, _ in resultados if n == nombre and es_error(n, c))}
    resumen["aprobado"] = resumen["p95_ms"] < args.umbral_p95 and resumen["porcentaje_errores"] <= 1.0

    print(json.dumps(resumen, indent=2, ensure_ascii=False))
    if args.salida:
        with open(args.salida, "w", encoding="utf-8") as archivo:
            json.dump(resumen, archivo, indent=2, ensure_ascii=False)
    return 0 if resumen["aprobado"] else 1


if __name__ == "__main__":
    sys.exit(main())
