# MediConecta — Checkpoint 3 (20/11) y preparación de la entrega final

> Estado al 18/11/2026. La auditoría posterior está en [`Informe_Auditoria.md`](Informe_Auditoria.md). Todas las cifras salen de ejecuciones reales (ver
> [plan de pruebas](plan-de-pruebas.md) y [`evidencias/entrega3`](evidencias/entrega3/)).

## 1. Diagnóstico inicial (antes de los cambios)

| Área | Situación encontrada |
|---|---|
| REST | Errores `{"error","codigo"}` propios, sin RFC 9457; sin `Idempotency-Key` ni `ETag`; errores de JAX-RS (404 de ruta, 405, JSON inválido) salían en el formato del servidor |
| Integraciones | SOAP sin timeout (y leía el WSDL en cada consulta); REST partner sin timeout; IA con 30 s de lectura; ninguna tenía fallback: cualquier falla era 502 |
| Mensajería | MDB que solo logueaban y **se tragaban los errores** (mensaje perdido); sin idempotencia, sin DLQ propia, suscripciones al tópico no durables |
| Seguridad | **BOLA**: un PACIENTE podía leer cualquier turno (`GET /api/turnos/{id}`) y cualquier ficha (`GET /api/pacientes/{dni}`), y reservando con un DNI ajeno inexistente dejaba dada de alta esa ficha. Sin límite de intentos de login. Secreto JWT de desarrollo fijo en el código. Sin cabeceras CSP / nosniff |
| Calidad | 58 tests; sin medición de cobertura ni mutación; sin pipeline de CI; Maven no fijado (sin wrapper) |
| Documentación | Faltaban plan de pruebas y matriz de trazabilidad |

## 2. Estado de cada requisito

| Requisito | Estado | Evidencia |
|---|---|---|
| Integración API REST | Implementado y verificado | smoke 119/119 en WildFly 36 |
| Integración mensajería (cola + tópico) | Implementado y verificado | smoke + log |
| Integración SOAP | Implementado y verificado | smoke (WSDL + consulta) |
| Timeout de 5 s | Implementado y verificado (unitario) | `ProteccionIntegracionTest`, `AdaptadorIaGroqHttpTest` |
| Fallback | Implementado y verificado | `CoberturaResilienteTest`, `OrientacionIaTest`, smoke |
| Fallas externas sin bloquear (circuit breaker) | Implementado y verificado (unitario) | `ProteccionIntegracionTest` |
| Consumidores idempotentes | Implementado y verificado | `MensajeriaTest`, smoke |
| DLQ | Implementado y verificado | smoke + log (3 intentos → DLQ) |
| Persistencia ante reinicio del broker | Implementado y verificado | `recuperacion-broker.txt` (cola y tópico durable) |
| Recuperación de pendientes | Implementado y verificado | ídem |
| Pruebas de falla y recuperación | Implementado y verificado | plan de pruebas §5.3 |
| Idempotency-Key | Implementado y verificado | `ApiRestTest`, `FachadaReservasTest`, smoke |
| ETag / condicionales | Implementado y verificado | `ApiRestTest`, smoke (304/412) |
| Problem Details | Implementado y verificado | `ApiRestTest`, `ManejadoresTest`, smoke |
| Códigos HTTP consistentes | Implementado y verificado | ídem |
| IA por su gateway, timeout, errores, tests sin servicio real | Implementado y verificado | `OrientacionIaTest`, `AdaptadorIaGroqHttpTest` |
| CI (compila, testea, cobertura, informa errores, sin credenciales) | Implementado; **falta la primera corrida en GitHub** | `.github/workflows/ci.yml` |
| Plan de pruebas v1 | Cumplido | `docs/plan-de-pruebas.md` |
| Matriz de trazabilidad v1 | Cumplido | `docs/matriz-trazabilidad.md` |
| Manual de usuario v1 | Cumplido (actualizado) | `docs/manual-usuario.md` |
| Build reproducible | Implementado y verificado localmente | `mvnw` (Maven 3.9.9) |
| Cobertura ≥ 80 % líneas / ≥ 70 % ramas | Implementado y verificado | 84,3 % / 83,1 % total; 94,3 % / 90,2 % capa de negocio |
| Mutation score ≥ 60 % | Implementado y verificado | 77 % total, 85 % negocio (PIT) |
| Quality Gate Sonar | Pendiente | requiere `SONAR_TOKEN` |
| Carga p95 < 500 ms | Parcial | p95 110,7 ms con plan B; falta con PostgreSQL |
| BOLA / BFLA / JWT inválidos / 429 | Implementado y verificado | tests + smoke |
| Inyección | Implementado y verificado | `pruebas-seguridad.py` + ZAP (plan B) |
| OWASP ZAP sin High | Corregido y verificado | 0 High/Medium/Low en la app (ver `Informe_Auditoria.md`) |
| Sin secretos en repo e historial | Parcial | secreto fijo eliminado; gitleaks configurado en CI, no ejecutado localmente |
| Declaración de uso de IA | Parcial | borrador en `docs/declaracion-uso-ia.md` |

## 3. Cómo ejecutar y comprobar

```powershell
# Build, 200 tests, cobertura con umbrales y WAR
.\mvnw.cmd clean verify
# Mutation testing
.\mvnw.cmd test-compile org.pitest:pitest-maven:mutationCoverage

# Servidor (perfil full obligatorio por JMS)
<wildfly>\bin\standalone.bat -c standalone-full.xml
.\scripts\wildfly\configurar-wildfly.ps1 -WildFly <wildfly>      # driver, datasource, JWT y mensajeria
copy target\mediconecta.war <wildfly>\standalone\deployments\

# Pruebas de sistema
.\scripts\smoke-test.ps1 -LogWildFly <wildfly>\standalone\log\server.log
python scripts\carga\prueba-carga.py --usuarios 20 --segundos 60 --escrituras
```

Demostración rápida de resiliencia en vivo:

1. **DLQ:** como `admin`, `POST /api/mensajeria/prueba-dlq` y luego `GET /api/mensajeria/estado`.
2. **Fallback de IA:** arrancar WildFly sin `mediconecta.ia.clave` (o con una URL inválida en
   `mediconecta.ia.url`): el asistente responde igual, con `origen: REGLAS`.
3. **Fallback de cobertura:** `-Dmediconecta.soap.wsdl=http://127.0.0.1:1/x?wsdl` y consultar
   la cobertura de un paciente de OSDE: `contingencia: true`.
4. **Recuperación del broker:** procedimiento del plan de pruebas §5.3.

## 4. Acciones manuales pendientes (equipo)

1. **Hacer push y verificar el CI en verde** en GitHub Actions (los cuatro jobs). Si el
   repositorio es de una organización, revisar que Actions esté habilitado.
2. **Sonar:** crear el proyecto en SonarCloud y cargar el secreto `SONAR_TOKEN` y las
   variables `SONAR_ORGANIZATION` y `SONAR_PROJECT_KEY`; confirmar el Quality Gate.
3. **PostgreSQL:** repetir smoke test, recuperación y carga con la base real.
4. **OWASP ZAP:** ya se corrió en local (0 High). Repetirlo sobre el servidor de la demo
   con PostgreSQL usando `scripts/seguridad/zap-api.yaml`.
5. **Secretos:** revisar el resultado del job `secretos`. El historial conserva el antiguo
   secreto JWT de desarrollo; ya no se usa, pero cualquier despliegue debe configurar
   `MEDICONECTA_JWT_SECRETO`. Si se decide limpiar el historial, es una decisión del equipo
   (reescribe commits).
6. **Clave de IA:** configurar `MEDICONECTA_IA_CLAVE` solo en el servidor de la demo.
7. **Jira:** actualizar el tablero con las tareas de esta entrega.
8. **Declaración de IA:** completar `docs/declaracion-uso-ia.md` (entregas 1 y 2 y revisión
   humana).
9. **Tag:** crear `v1.0-final` sobre `main` **antes de las 08:00 del día de la exposición**
   (no se creó automáticamente).
10. **Demo:** ensayar el guion con el servidor configurado con los cuatro scripts CLI.
