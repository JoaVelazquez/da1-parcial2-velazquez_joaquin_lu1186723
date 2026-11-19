# CAMBIOS

Proyecto: `android/Jornadas`. Se mantuvo la arquitectura (Compose → ViewModel → Repository → Room/Retrofit) y las versiones de Gradle, AGP y Kotlin.

## Build

- Se agregó `com.squareup.okhttp3:okhttp:4.12.0`. Retrofit 2.11 trae OkHttp 3.x, que no tiene `toMediaType()` ni `toResponseBody()`, y `lab/ServicioLocal.kt` no compilaba. Se mantiene `converter-gson` porque lo usa `Servicios.kt`.

## Defectos encontrados en la versión entregada

| Defecto | Dónde | Efecto |
|---|---|---|
| `@Insert(REPLACE)` reemplaza la fila entera con `favorita = false` | `data/Programa.kt` | Cada sincronización vaciaba Mi agenda |
| `REPLACE` no borra lo que dejó de venir | `data/Programa.kt` | Con v3 la 11 seguía en el catálogo y en Mi agenda |
| El aviso y la grilla estaban en un `if / else` | `ui/Catalogo.kt` | Sin señal se escondía el programa guardado |
| Recarga en `onStart` y en `LaunchedEffect(Unit)`, sin guarda | `MainActivity.kt`, `ui/Catalogo.kt` | 2 peticiones al abrir, +1 al volver a Programa, +2 al rotar |
| Solo se atrapaba `IOException` | `ui/ProgramaModel.kt` | Un error HTTP, un JSON incompleto o un error de base cerraba la app |
| `tema` en un `remember` y `dia` en memoria | `ui/Catalogo.kt`, `ui/ProgramaModel.kt` | El tema se perdía al abrir una ficha o rotar; ninguno sobrevivía a la muerte del proceso |
| `Servicios(app)` se creaba dentro del ViewModel | `ui/ProgramaModel.kt` | Cada ViewModel nuevo abría otra base y reiniciaba el contador de la Mesa |
| `legacy/AgendaImpresa.kt` | `legacy/` | Código muerto, solo lo usaba un `@Preview` |

## 41 · Programa sin señal

- `ProgramaModel.recargar()` atrapa cualquier error (menos la cancelación) y marca `fallo`. Lo guardado en Room no se toca.
- Nueva tabla `edicion` que se escribe en la misma transacción que el programa. Distingue "nunca se descargó" de "se descargó una edición vacía" (v4).
- La UI decide con esos dos datos:
  - Con edición y falla: lista, detalle y favoritas siguen visibles, con el aviso «Sin conexión: se están usando los datos guardados».
  - Sin edición y falla: mensaje y botón **Reintentar**.
  - Edición vacía: «Sin charlas programadas.», también al reabrir sin señal.
- Migración `1 → 2` (`MIGRACION_1_2`): crea `edicion` y, si la instalación ya tenía charlas, la marca como descargada. No borra datos del usuario.

## 42 · Agenda personal

`AgendaRepository.sincronizar()`:

1. Descarga y mapea **antes** de tocar la base.
2. Dentro de `withTransaction`: lee las favoritas, borra el programa, inserta la instantánea nueva con las marcas aplicadas y registra la edición.

Así:
- v1 → v2: la 11 conserva la marca y muestra «Compose en producción avanzada» / «Sala Puerto».
- v3: la 11 desaparece del catálogo y de Mi agenda, entra la 90 sin marcar y las demás conservan su marca.
- Una charla retirada que vuelve, vuelve sin marcar (su fila se borró).
- Una marca agregada o quitada durante la petición ya está en la base cuando se leen las favoritas, así que se respeta.
- Si la respuesta falla o viene mal formada, la transacción no se ejecuta y queda la edición anterior.

## 43 · Volver al punto de lectura

- `dia` y `tema` viven en `SavedStateHandle` (`ProgramaModel`). Sobreviven a abrir una ficha, rotar y a que Android mate el proceso. Una tarea nueva arranca sin filtros.
- La ficha abierta y la pestaña ya estaban en `rememberSaveable` (`Portada`).
- Si la ficha abierta se retira del programa, se muestra «Esta charla ya no está en el programa».

## 44 · Control de descargas

- `App` crea `Servicios` una vez por proceso (Room, Retrofit y la Mesa).
- `MainActivity.onCreate` llama a `cargaAutomatica()`, que solo descarga la primera vez en el proceso (`AtomicBoolean` en el repositorio). Rotar no suma.
- Se quitaron la recarga de `onStart` y el `LaunchedEffect(Unit)` de `Catalogo`: entrar a una ficha, cambiar de pestaña o volver de Inicio no suma.
- `recargar()` guarda el `Job`; si hay uno activo, los toques extra se ignoran. Al terminar (éxito o falla) se puede volver a pedir.
- Los filtros no dependen de la carga, así que se pueden usar durante la espera.

## Refactorizaciones

1. **Un contenedor por proceso** (`App`): el ViewModel ya no crea sus dependencias, y el contador de la Mesa refleja el proceso.
2. **Sincronización atómica** con transacción.
3. **Se borró `legacy/AgendaImpresa.kt`**, código muerto.

### Rama `joa/refactor` (no va a `main`)

4. **Filtrado en el ViewModel.** La regla de día y tema estaba dentro de `Catalogo`. Ahora es una función pura, `filtrar()` (`ui/Filtros.kt`), y el ViewModel expone `visibles`. La pantalla solo dibuja. Test: `FiltrosTest` (Día 2 + Datos da 23 y 24).
5. **Dependencias por factory.** `ProgramaModel` era un `AndroidViewModel` que casteaba la `Application` para buscar sus servicios. Ahora es un `ViewModel` común que recibe el repositorio, la Mesa y el `SavedStateHandle` por constructor (`ProgramaModel.Factory`). Así se puede crear en un test sin Android.
6. **Mi agenda en una tabla propia.** `charlas` (que pasa a llamarse `FichaGuardada` en el código) solo guarda lo que manda el servidor, y las marcas del usuario van a `agenda(charlaId)`. La UI recibe `Charla` con `favorita` calculada por un `LEFT JOIN`, así que las pantallas no cambian.
   - Sincronizar queda más simple: reemplaza `charlas` y borra las marcas de las charlas retiradas. Ya no lee ni vuelve a aplicar favoritas, y una marca hecha durante la descarga queda intacta porque esa tabla no se toca.
   - Solo se puede marcar una charla que está en el programa (`INSERT ... SELECT`). Así no quedan marcas huérfanas que reaparezcan si la charla vuelve.
   - Migración `2 → 3`: crea `agenda`, copia las marcas y rehace `charlas` sin la columna `favorita` (el SQLite de API 26 no tiene `DROP COLUMN`). Room encadena `1 → 2 → 3`.
   - Test `MigracionAgendaTest`: corre la migración real contra SQLite (`sqlite-jdbc`, solo para tests) y compara el esquema con el de Room v3. También prueba v3 (se va la 11 y entra la 90 sin marca), que una charla retirada vuelva sin marcar y que no se puedan marcar charlas inexistentes. Reemplaza a `AplicarMarcasTest`.
   - **Sin probar en un dispositivo:** la apertura real de Room después de migrar.

## Evidencia de validación

**Tests unitarios** (`app/src/test/.../AplicarMarcasTest.kt`): v1→v2 conserva la marca con título y sala nuevos; v3 retira la 11 y la 90 entra sin marca; v4 vacía es válida; una marca quitada durante la petición no vuelve. En `joa/refactor` lo reemplazan `MigracionAgendaTest` y `FiltrosTest`.

```
gradlew.bat testDebugUnitTest
```

**Pasos manuales con la Mesa:**

| Paso | Esperado |
|---|---|
| Abrir con v1 | Contador 1 |
| Rotar, abrir una ficha, cambiar de pestaña, Inicio y volver | Contador sigue en 1 |
| Demora larga, tocar Actualizar 2 veces | Suma 1 |
| Marcar 11 y 24, pasar a v2 y Actualizar | 11 sigue marcada con «Compose en producción avanzada» / «Sala Puerto» |
| Demora larga, Actualizar y desmarcar 24 durante la espera | 24 queda sin marcar |
| v3 y Actualizar | 11 desaparece del programa y de Mi agenda, entra la 90, 24 sigue marcada si lo estaba |
| v4 y Actualizar | «Sin charlas programadas.» |
| Servicio apagado, cerrar la app y reabrir | «Sin charlas programadas.» + aviso de datos guardados |
| v1, servicio apagado y Actualizar | Lista, detalle y Mi agenda visibles + aviso |
| `adb shell pm clear ar.jornadas` + `--ez servicio_disponible false` | Mensaje sin descarga previa + Reintentar |
| Día 2 + Datos, abrir «Cachés para viajeros», volver y rotar | 2 resultados de Datos |
| Ficha abierta, Inicio, `adb shell am kill ar.jornadas`, volver desde Recientes | Se recupera la ficha y, al volver, ambos filtros |
| Instalar este APK sobre la versión 1.2 con favoritas | Las favoritas se conservan (migración 1 → 2) |

## Decisiones y mejoras futuras

- Una respuesta vacía se toma como válida porque la organización publica instantáneas completas. Por eso v4 borra también las marcas.
- Mejora futura: guardar Mi agenda en una tabla aparte (`agenda(charlaId)`) para no mezclar datos del servidor con datos del usuario. Hecho en `joa/refactor` (refactor 6).

## Uso de IA y notas adicionales

Claude Code. Revisé cada cambio, compilé y corrí los tests.
Mi Laptop tiene pocos recursos y correr el emulador causa crashes de forma muy seguida. Por ello opte guiarme con unit tests mas que nada, ya que no puedo jugar tanto con la aplicación como me gustaría.
Nota de los PRs, commits y control de versiones en general: Es mi forma usual de desarrollo, genero branches especificas para cada feature que luego mergeo a dev.
Una vez termino un grupo de features que consideraría "estable", paso los cambios a Main. A pesar de ser el único contribuidor del repositorio, son prácticas que tengo muy arraigadas y no afectan al resultado final. Misma explicacion para el formato de mis PRs, para el cual tengo un template personal.
