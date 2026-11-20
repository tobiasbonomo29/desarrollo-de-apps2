#!/usr/bin/env python3
"""
Proveedor de IA simulado (API "chat completions") para probar y demostrar el
gateway de IA de MediConecta sin clave ni red externa.

    python scripts/ia/proveedor-ia-simulado.py --modo ok      # responde una sugerencia valida
    python scripts/ia/proveedor-ia-simulado.py --modo lento   # tarda 8 s (MediConecta corta a los 5 s)
    python scripts/ia/proveedor-ia-simulado.py --modo error   # responde 503

Despues, en el WildFly (sin reiniciar: el adapter lee la configuracion en cada pedido):

    /system-property=mediconecta.ia.url:add(value=http://127.0.0.1:9123/v1/chat/completions)
    /system-property=mediconecta.ia.clave:add(value=clave-simulada)

Con "ok" la orientacion responde origen IA; con "lento" o "error", origen REGLAS
(fallback) en unos 5 segundos como maximo.
"""
import argparse
import json
import time
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument("--modo", choices=["ok", "lento", "error"], default="ok")
    parser.add_argument("--puerto", type=int, default=9123)
    args = parser.parse_args()

    class Proveedor(BaseHTTPRequestHandler):
        def do_POST(self):
            largo = int(self.headers.get("Content-Length", 0))
            pedido = json.loads(self.rfile.read(largo) or b"{}")
            ultimo = pedido.get("messages", [{}])[-1].get("content", "")
            print(f"[proveedor simulado] modo={args.modo} auth={'si' if self.headers.get('Authorization') else 'no'} "
                  f"modelo={pedido.get('model')} ultimo mensaje={ultimo[:60]!r}", flush=True)
            if args.modo == "lento":
                time.sleep(8)
            if args.modo == "error":
                cuerpo, estado = {"error": {"message": "sobrecargado"}}, 503
            else:
                contenido = json.dumps({"mensaje": "Por lo que contas, te conviene Traumatologia.",
                                        "especialidad": "Traumatologia", "urgente": False})
                cuerpo, estado = {"choices": [{"message": {"role": "assistant", "content": contenido}}]}, 200
            datos = json.dumps(cuerpo).encode()
            try:
                self.send_response(estado)
                self.send_header("Content-Type", "application/json")
                self.send_header("Content-Length", str(len(datos)))
                self.end_headers()
                self.wfile.write(datos)
            except (BrokenPipeError, ConnectionResetError):
                print("[proveedor simulado] el cliente corto la conexion (timeout)", flush=True)

        def log_message(self, *args):
            pass

    print(f"Proveedor de IA simulado en http://127.0.0.1:{args.puerto}/v1/chat/completions (modo {args.modo})", flush=True)
    ThreadingHTTPServer(("127.0.0.1", args.puerto), Proveedor).serve_forever()


if __name__ == "__main__":
    main()
