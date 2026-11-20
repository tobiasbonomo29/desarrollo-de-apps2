// Genera las figuras SVG/HTML del informe final a partir de datos del código.
// Uso: node generar-figuras.js  (luego render-figuras.ps1 las pasa a PNG con Edge headless)
const fs = require("fs");
const path = require("path");
const dir = __dirname;

const DEFS = `<defs><marker id="a" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0L10,5L0,10z" fill="#0F2A44"/></marker><marker id="ag" viewBox="0 0 10 10" refX="9" refY="5" markerWidth="7" markerHeight="7" orient="auto"><path d="M0,0L10,5L0,10z" fill="#0F9D76"/></marker></defs>`;
const pagina = (w, h, cuerpo) => `<html><head><meta charset="utf-8"><link rel="stylesheet" href="_base.css"></head><body><svg width="${w}" height="${h}" viewBox="0 0 ${w} ${h}">${DEFS}<rect x="5" y="5" width="${w - 10}" height="${h - 10}" rx="14" fill="#F5F8FC" stroke="#C9D6E3"/>${cuerpo}</svg></body></html>`;
const esc = (s) => String(s).replace(/&/g, "&amp;").replace(/</g, "&lt;").replace(/>/g, "&gt;");
const caja = (x, y, w, h, titulo, lineas = [], clase = "box") => {
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="10" class="${clase}"/>`;
  const oscuro = clase === "dark";
  s += `<text x="${x + 14}" y="${y + 27}" class="t${oscuro ? " w" : ""}">${esc(titulo)}</text>`;
  lineas.forEach((l, i) => { s += `<text x="${x + 14}" y="${y + 48 + 17 * i}" class="s"${oscuro ? ' style="fill:#C9D6E3"' : ""}>${esc(l)}</text>`; });
  return s;
};
const ent = (x, y, w, titulo, lineas) => {
  const h = 36 + 18 * lineas.length;
  let s = `<rect x="${x}" y="${y}" width="${w}" height="${h}" rx="8" class="box"/><path d="M${x} ${y + 28}V${y + 8}a8 8 0 0 1 8-8H${x + w - 8}a8 8 0 0 1 8 8V${y + 28}z" class="dark"/>`;
  s += `<text x="${x + 10}" y="${y + 19}" class="t w" style="font-size:14px">${esc(titulo)}</text>`;
  lineas.forEach((l, i) => { s += `<text x="${x + 10}" y="${y + 47 + 18 * i}" class="s">${esc(l)}</text>`; });
  return s;
};
const flecha = (x1, y1, x2, y2, cl = "arrow") => `<line x1="${x1}" y1="${y1}" x2="${x2}" y2="${y2}" class="${cl}"/>`;
const texto = (x, y, t, extra = "") => `<text x="${x}" y="${y}" class="s" ${extra}>${esc(t)}</text>`;

// ---------- Modelo de datos ----------
let m = "";
m += ent(40, 40, 250, "pacientes", ["PK id", "documento UNIQUE (6–10 dígitos)", "nombre · email · telefono", "cobertura · usuario", "activo · version (ETag)"]);
m += ent(480, 40, 270, "turnos", ["PK id", "FK paciente_id · FK profesional_id", "fecha_hora · especialidad · motivo", "estado CHECK (PROGRAMADO | ATENDIDO |", "  CANCELADO)", "archivado · version (ETag)"]);
m += ent(900, 40, 260, "profesionales", ["PK id", "matricula UNIQUE · usuario UNIQUE", "nombre · especialidad · email", "activo"]);
m += ent(900, 270, 260, "disponibilidades", ["PK id · FK profesional_id", "dia_semana CHECK (MONDAY..SUNDAY)", "hora_desde · hora_hasta", "duracion_minutos"]);
m += ent(480, 320, 270, "notificaciones", ["PK id · FK turno_id", "UNIQUE (evento_id, tipo) → idempotencia", "tipo · canal (CHECK)", "destinatario · mensaje", "creada_en · programada_para"]);
m += ent(40, 320, 270, "claves_idempotencia", ["PK id · FK turno_id", "UNIQUE (usuario, clave)", "huella (SHA-256 del pedido)", "creada_en"]);
m += ent(40, 540, 250, "usuarios", ["PK id · usuario UNIQUE", "hash_clave (PBKDF2) · activo"]);
m += ent(340, 540, 220, "usuario_roles", ["FK usuario_id · rol"]);
m += ent(900, 500, 260, "mensajes_fallidos (DLQ)", ["PK id · mensaje_id UNIQUE", "evento_id · destino_original", "cuerpo · recibido_en"]);
m += flecha(290, 100, 478, 100) + texto(305, 92, "1 paciente — N turnos");
m += flecha(898, 100, 752, 100) + texto(765, 92, "1 profesional — N turnos");
m += flecha(1030, 148, 1030, 268) + texto(1040, 215, "1 — N franjas");
m += flecha(615, 318, 615, 182) + texto(625, 260, "N avisos — 1 turno");
m += flecha(310, 360, 478, 160) + texto(330, 280, "clave → turno creado");
m += flecha(290, 575, 338, 575);
m += texto(40, 680, "Fuente: db/migraciones V1__esquema_inicial, V2__indices_mensajeria_e_idempotencia y V3__version_para_etag (Flyway). Hibernate solo valida (hbm2ddl=validate).");
fs.writeFileSync(path.join(dir, "modelo-datos.html"), pagina(1200, 700, m));

// ---------- Mensajería ----------
let j = "";
j += caja(30, 60, 210, 80, "FachadaReservas", ["transacción JTA REQUIRED"]);
j += caja(290, 60, 230, 80, "NotificadorTurnos", ["@Stateless · envío XA", "mensajes persistentes"]);
j += flecha(240, 100, 288, 100);
j += caja(570, 30, 260, 70, "Cola de confirmaciones", ["punto a punto"], "soft");
j += caja(570, 130, 260, 70, "Tópico de recordatorios", ["pub/sub · suscripciones durables"], "soft");
j += flecha(520, 90, 568, 65) + flecha(520, 110, 568, 165);
j += caja(880, 30, 290, 62, "ReceptorConfirmacion", ["@MessageDriven → confirmación"]);
j += caja(880, 105, 290, 62, "SuscriptorAvisoPaciente", ["@MessageDriven → recordatorio"]);
j += caja(880, 180, 290, 62, "SuscriptorAvisoProfesional", ["@MessageDriven → agenda del médico"]);
j += flecha(830, 65, 878, 60) + flecha(830, 160, 878, 136) + flecha(830, 170, 878, 210);
j += caja(880, 275, 290, 100, "ConsumoDeEventos", ["GestionNotificaciones: UNIQUE (evento_id,", "tipo) → un evento repetido genera un", "solo aviso (consumidor idempotente)"], "green");
j += flecha(1025, 242, 1025, 273);
j += caja(570, 275, 260, 100, "Reintentos (Artemis)", ["max-delivery-attempts = 4", "espera 1 s → 2 s → 4 s", "(setRollbackOnly ante una falla)"], "warn");
j += flecha(878, 325, 832, 325, "darrow");
j += caja(570, 405, 260, 80, "DLQ: MensajesFallidos", ["después del 4.º intento fallido"], "warn");
j += flecha(700, 375, 700, 403);
j += caja(880, 405, 290, 80, "MonitorMensajesFallidos", ["@MessageDriven → mensajes_fallidos", "GET /api/mensajeria/mensajes-fallidos"]);
j += flecha(830, 445, 878, 445);
j += texto(30, 230, "Si un paso de la reserva falla, el", 'style="font-size:13px"') + texto(30, 248, "contenedor deshace el turno y el envío", 'style="font-size:13px"') + texto(30, 266, "JMS (misma transacción XA).", 'style="font-size:13px"');
j += texto(30, 505, "Fuente: mensajeria/*.java · scripts/wildfly/configurar-mensajeria.cli · docker/configurar-wildfly-docker.cli");
fs.writeFileSync(path.join(dir, "mensajeria.html"), pagina(1200, 520, j));

// ---------- Gateway de IA ----------
let g = "";
g += caja(30, 60, 180, 90, "Chat (paso 1)", ["POST /api/reserva/", "orientacion"], "soft");
g += caja(250, 50, 300, 110, "GestorOrientacion", ["@Stateless · valida ≤ 20 mensajes / 1000 car.", "limita 10 consultas/min por usuario (429)", "solo especialidades con médicos activos"]);
g += caja(590, 50, 300, 110, "AdaptadorIaGroq", ["@Stateless · API chat completions", "timeout conexión 2 s · lectura 5 s", "clave solo en el servidor"]);
g += caja(930, 60, 240, 90, "Proveedor LLM", ["Groq (externo)"], "dark");
g += flecha(210, 105, 248, 105) + flecha(550, 105, 588, 105) + flecha(890, 105, 928, 105);
g += caja(590, 225, 300, 95, "OrientacionDeRespaldo", ["palabras clave · detecta urgencias", "respuesta con \"contingencia\": true"], "green");
g += flecha(740, 160, 740, 223, "darrow") + texto(750, 198, "si falla, tarda o falta la clave", 'style="fill:#0F9D76"');
g += `<text x="30" y="225" class="t" style="font-size:14px">Reglas del prompt de sistema</text>`;
["solo sugiere especialidad, no diagnostica", "urgencia → 107 (SAME) / 911", "responde JSON {mensaje, especialidad, urgente}", "no se envían nombre ni DNI del paciente"].forEach((l, i) => { g += texto(30, 248 + 19 * i, "· " + l); });
g += texto(30, 362, "Fuente: integraciones/GestorOrientacion.java, AdaptadorIaGroq.java, OrientacionDeRespaldo.java");
fs.writeFileSync(path.join(dir, "ia-gateway.html"), pagina(1200, 380, g));

// ---------- Pipeline ----------
let p = "";
p += caja(30, 120, 130, 60, "git push / PR", [], "dark");
const jobs = [["calidad", "enforcer · Checkstyle · JUnit · JaCoCo · WAR", "box"], ["mutacion", "PIT negocio + State · falla < 60 %", "box"], ["secretos", "gitleaks sobre todo el historial", "box"], ["sonar", "se omite: falta configurar SONAR_TOKEN", "warn"], ["migraciones", "Flyway sobre PostgreSQL vacío (2 veces)", "box"]];
jobs.forEach(([t, d, c], i) => { p += caja(210, 22 + 62 * i, 320, 54, t, [d], c); p += flecha(160, 150, 208, 48 + 62 * i); });
p += caja(610, 105, 560, 120, "punta-a-punta (necesita calidad + migraciones)", ["docker compose up: PostgreSQL + Flyway + WildFly → /api/salud", "scripts/smoke-test.sh: humo + seguridad en vivo", "k6 (p95 < 500 ms) → OWASP ZAP baseline (falla con High)"], "soft");
p += flecha(530, 48, 608, 140) + flecha(530, 296, 608, 200);
fs.writeFileSync(path.join(dir, "pipeline.html"), pagina(1200, 340, p));

// ---------- Gráfico de cobertura y mutación (una serie + umbral) ----------
for (const datos of JSON.parse(fs.readFileSync(path.join(dir, "metricas.json"), "utf8"))) {
const W = 1100, filaH = 46, x0 = 330, ancho = 640;
let c = `<text x="30" y="40" class="t" style="font-size:18px">${esc(datos.titulo)}</text>`;
c += texto(30, 62, datos.subtitulo);
datos.filas.forEach((f, i) => {
  const y = 90 + i * filaH;
  c += `<text x="${x0 - 14}" y="${y + 21}" text-anchor="end" style="font-size:14px;fill:#0F2A44">${esc(f.nombre)}</text>`;
  c += `<rect x="${x0}" y="${y + 6}" width="${ancho}" height="22" rx="4" fill="#EEF2F7"/>`;
  c += `<rect x="${x0}" y="${y + 6}" width="${(ancho * f.valor) / 100}" height="22" rx="4" fill="#2563EB"/>`;
  if (f.umbral != null) {
    const ux = x0 + (ancho * f.umbral) / 100;
    c += `<line x1="${ux}" y1="${y + 1}" x2="${ux}" y2="${y + 33}" stroke="#0F2A44" stroke-width="2.5" stroke-dasharray="4 3"/>`;
  }
  c += `<text x="${x0 + ancho + 14}" y="${y + 22}" style="font-size:14px;font-weight:700;fill:#0F2A44">${f.valor.toFixed(1).replace(".", ",")} %</text>`;
  c += `<text x="${x0 + ancho + 80}" y="${y + 22}" style="font-size:12px;fill:#4A6178">${esc(f.nota || "")}</text>`;
});
const yl = 90 + datos.filas.length * filaH + 20;
c += `<line x1="${x0}" y1="${yl}" x2="${x0 + 26}" y2="${yl}" stroke="#0F2A44" stroke-width="2.5" stroke-dasharray="4 3"/>` + texto(x0 + 34, yl + 4, "umbral mínimo de los Criterios de Testing (sin marca: la cátedra no fija umbral para esa medición)");
fs.writeFileSync(path.join(dir, datos.archivo), pagina(W + 160, yl + 40, c));
}
console.log("figuras generadas");
