"""Resume el CSV de JaCoCo como una tabla Markdown (para el resumen de GitHub Actions).

Uso:  python3 scripts/ci/resumen-cobertura.py target/site/jacoco/jacoco.csv
"""
import csv
import sys
from collections import defaultdict


def main(ruta):
    try:
        with open(ruta, newline="", encoding="utf-8") as archivo:
            filas = list(csv.DictReader(archivo))
    except FileNotFoundError:
        print("No se genero el informe de cobertura (fallo la compilacion o los tests).")
        return

    por_paquete = defaultdict(lambda: [0, 0])
    for fila in filas:
        paquete = fila["PACKAGE"].replace("com.mediconecta.", "") or "(raiz)"
        cubiertas = int(fila["LINE_COVERED"])
        perdidas = int(fila["LINE_MISSED"])
        por_paquete[paquete][0] += cubiertas
        por_paquete[paquete][1] += perdidas

    print("### Cobertura de lineas (JaCoCo)\n")
    print("| Paquete | Lineas cubiertas | Cobertura |")
    print("|---|---:|---:|")
    total_cubiertas = total = 0
    for paquete in sorted(por_paquete):
        cubiertas, perdidas = por_paquete[paquete]
        lineas = cubiertas + perdidas
        total_cubiertas += cubiertas
        total += lineas
        porcentaje = 100.0 * cubiertas / lineas if lineas else 0.0
        print(f"| {paquete} | {cubiertas}/{lineas} | {porcentaje:.1f} % |")
    porcentaje_total = 100.0 * total_cubiertas / total if total else 0.0
    print(f"| **Total** | **{total_cubiertas}/{total}** | **{porcentaje_total:.1f} %** |")


if __name__ == "__main__":
    main(sys.argv[1] if len(sys.argv) > 1 else "target/site/jacoco/jacoco.csv")
