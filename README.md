# 📚 Universal Novel Scraper & EPUB Suite

> **Suite de ingeniería editorial automatizada: Extracción, Limpieza, Maquetación y Gestión de Novelas Ligeras.**

Este proyecto es una solución integral escrita en **Java 21** y **Spring Boot** para automatizar el flujo de trabajo de archivado digital. Transforma novelas web dispersas en miles de páginas HTML en libros electrónicos **EPUB 3.0** profesionales, limpios y validados, listos para dispositivos como Kindle, Kobo o Apple Books.

![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.5-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Selenium](https://img.shields.io/badge/Selenium-4.52-43B02A?style=for-the-badge&logo=selenium&logoColor=white)
![Jsoup](https://img.shields.io/badge/Jsoup-Parser-blue?style=for-the-badge)
![EPUB](https://img.shields.io/badge/EPUB-3.0-standard?style=for-the-badge)
![Docker](https://img.shields.io/badge/Docker-Ready-2496ED?style=for-the-badge&logo=docker&logoColor=white)

## ✨ Novedades

* **📖 EPUB 3 nativo:** creación de EPUB 3 válidos desde la carpeta `OEBPS` (OPF 3.0, `nav.xhtml`, `toc.ncx` de respaldo, `mimetype` sin comprimir) **sin Calibre ni librerías externas**, y conversión **EPUB 2 → EPUB 3** individual o en lote.
* **🔁 Descarga resiliente:** reintentos por capítulo, detección instantánea de capítulos **VIP**, reporte `FALTANTES.txt` y opción de **reanudar** una descarga cortada sin repetir lo ya bajado.
* **🛡️ Respeto al sitio (anti rate-limit):** tope de navegadores y pausa aleatoria por sitio, y parada automática si el sitio bloquea la IP por exceso de peticiones.
* **🌐 Nuevo sitio soportado:** *Novela en Español* (`novelaenespanol.com`), cuyo índice se obtiene del endpoint AJAX del tema.
* **🔒 Seguridad:** validación anti-SSRF de las URLs que llegan a la API (solo sitios soportados, sin IPs internas).
* **🐳 Listo para desplegar:** `Dockerfile` multi-stage + `docker-compose` y perfil `prod`.
* **🧱 Arquitectura reorganizada por funcionalidad** (`cli` / `scraping` / `epub` / `web` / `common`) con un motor de descarga único compartido por la consola y la web.

## ⚡ Métricas de Rendimiento
El sistema ha sido optimizado para velocidad y estabilidad en entornos de alta demanda:
* **Velocidad de Procesamiento:** Promedio de **~2.2 segundos por capítulo** (ciclo completo: descarga + limpieza + guardado).
* **Capacidad de Carga:** Probado exitosamente con novelas de **+1500 capítulos** en una sola sesión sin desbordamiento de memoria.
* **Tasa de Éxito:** **99.8%** en detección de contenido gracias a selectores dinámicos y heurística de texto.

---

## 🛠️ Arquitectura y Módulos

### 1. API REST y Panel de Control Web
* **Panel Reactivo:** Interfaz moderna (HTML/CSS/JS) con modo oscuro y gestión de DOM para inyección de parámetros.
* **Trabajos Asíncronos:** la API (`EpubController`) asigna un UUID a cada tarea; `JobService` guarda su estado (`PENDIENTE` / `EN_PROGRESO` / `LISTO` / `FALLIDO`) con expiración por TTL, y el frontend consulta el progreso sin bloquear el servidor.
* **Extracción desde la web:** descarga multihilo con el mismo resultado que la consola; el `.zip` final lleva el nombre de la novela.
* **Compresión Cliente-Servidor:** uso de `JSZip` en el navegador para comprimir carpetas antes de enviarlas al backend, optimizando el ancho de banda.
* **Limpieza automática:** `CleanupTask` borra los temporales vencidos y purga los trabajos expirados.

### 2. El Extractor Híbrido (Scraper)
Arquitectura **Selenium + Jsoup**: un navegador real (Chrome headless) renderiza las páginas, incluidas las que dependen de JavaScript/AJAX, y Jsoup parsea y limpia el HTML resultante.
* **Estrategia por sitio (Strategy Pattern):** se detecta el dominio y se carga su `SitioWebConfig` (selectores y, si hace falta, una estrategia propia de índice; p. ej. el *turbo* asíncrono de SkyNovels o el endpoint AJAX de Novela en Español).
* **Motor único de descarga paralela:** `MotorDescargaParalela` reparte los capítulos entre varios navegadores, reintenta fallos transitorios, recicla el navegador para liberar RAM y clasifica cada capítulo en éxito / VIP / fallido. CLI y web lo usan como adaptadores.
* **Tope global de navegadores:** un semáforo (`app.scraper.max-navegadores`) limita cuántos Chrome viven a la vez en toda la app para proteger la RAM.
* **Anti rate-limit por sitio:** cada sitio puede declarar un máximo de navegadores, una pausa aleatoria entre capítulos y cómo reconocer su página de bloqueo; si aparece, la descarga se detiene en lugar de insistir.
* **Regex Universal:** extracción de índices capaz de identificar numeración en formatos no estándar (ej: *"RW 391"*, *"Vol.2 Cap.10"*, *"Episodio Final"*), incluidas partes decimales (15.1, 15.2).

### 3. Editor XHTML / EPUB (Post-Procesamiento)
Un motor de limpieza y normalización que transforma el "HTML sucio" de la web en código semántico de calidad editorial.
* **Estandarización Heurística:** analiza etiquetas (`h1-h6`, `p`, `strong`) para encontrar títulos ocultos o mal formateados y los promueve a `<h1>` semánticos, tanto en carpetas de `.xhtml` como dentro de un EPUB.
* **Sanitización XML:** repara errores comunes de HTML5 laxo (etiquetas `<br>`, `<img>` sin cerrar) y escapa los títulos para generar XHTML válido.
* **Creación de EPUB:** EPUB 2 (con `epub4j`), re-empaquetado de carpeta, o **EPUB 3 nativo** (`EmpaquetadorEpub3`).
* **Metadatos:** portada, sinopsis y géneros editables directamente dentro del EPUB.

### 4. Utilidades Avanzadas de EPUB (Splitter & Packer)
Módulo de ingeniería inversa para la gestión de archivos EPUB masivos.
* **División Inteligente (Smart Splitter):** divide novelas de +2000 capítulos en volúmenes lógicos sin romper la estructura interna (CSS/Imágenes).
    * **Mapeo Semántico:** lee los nombres de archivo físicos (`Capitulo0050.xhtml`) y entiende que corresponde al "Capítulo 50" de la historia, permitiendo cortes precisos ignorando prólogos.
* **Re-empaquetado OCF (Open Container Format):** compresión ZIP que respeta el estándar de EPUB:
    * Inyección del archivo `mimetype` **sin compresión** (`STORED`) como primera entrada.
    * Cálculo manual de CRC32 para integridad de datos.
* **Conversión EPUB 2 → 3 nativa:** genera un archivo `*_v3.epub` nuevo (no toca el original), individual o para toda una carpeta.

---

## 🌐 Sitios Soportados

| Sitio | Dominio |
|---|---|
| SkyNovels | `skynovels.net` |
| Novelas Ligera | `novelasligera…` |
| NOVA | `novelasligeras.net` |
| TuNovelaLigera | `tunovelaligera…` |
| Novela en Español | `novelaenespanol.com` |
| Blogspot (Chtholly Nota Seniorious) | `*.blogspot.com` |

> Si pegas la URL de un sitio que no está en la lista, la herramienta lo avisa al instante sin abrir el navegador.
>
> **Novela en Español** limita las peticiones por minuto (Wordfence): la app usa como máximo 3 navegadores con pausas cortas para ese sitio. Si aun así la IP queda bloqueada temporalmente, la descarga se detiene; espera unos minutos y vuelve a lanzarla para reanudar.

---

## 📂 Estructura del Proyecto

El código Java está organizado **por funcionalidad** (*package-by-feature*):

```text
mi-extractor/src/main/
├── java/com/extractor/mi_extractor/
│   ├── MiExtractorApplication.java   # Arranque de Spring Boot (modo web)
│   ├── cli/                          # Menú de consola
│   │   ├── App.java                  #   Punto de entrada de la CLI
│   │   ├── descarga/                 #   Suite 1: Extractor de capítulos
│   │   ├── editor/                   #   Suite 2: Editor XHTML / creación de EPUB
│   │   └── utilidades/               #   Suite 3: Dividir, descomprimir, convertir...
│   ├── scraping/                     # Extracción web
│   │   ├── service/                  #   Scraper, MotorDescargaParalela, GestorSitios...
│   │   ├── model/                    #   SitioWebConfig y una clase por sitio
│   │   └── util/                     #   ConfigScraper (tope global de navegadores)
│   ├── epub/                         # Generación y procesado de EPUB
│   │   ├── service/                  #   EmpaquetadorEpub3, Split/Zip/Calibre processors...
│   │   └── util/
│   ├── web/                          # API REST y servicios web
│   │   ├── controller/               #   EpubController, UrlSafetyValidator (anti-SSRF)
│   │   ├── service/                  #   EpubService, JobService, CleanupTask...
│   │   └── model/                    #   Job, EstadoJob
│   └── common/                       # Utilidades compartidas (StorageService, reportes...)
└── resources/
    ├── static/                       # Frontend (index.html, js, css)
    ├── application.properties        # Configuración local
    └── application-prod.properties   # Perfil de producción (Docker)
```

---

## 🚀 Instalación y Uso

### Requisitos Previos
* **Java JDK 21** o superior.
* **Google Chrome / Chromium** (versión reciente). El `chromedriver` se toma de `mi-extractor/drivers/` y, si no está, Selenium lo descarga y cachea automáticamente.
* **Maven** (incluido mediante el wrapper `mvnw`).

> Todos los comandos se ejecutan dentro de la carpeta `mi-extractor/`. En Windows usa `mvnw.cmd` en lugar de `./mvnw`.

### Modo Web (Recomendado)
```bash
cd mi-extractor
./mvnw spring-boot:run
```
Abre tu navegador en: **http://localhost:8080**

### Modo Consola (CLI)
Ideal para descargas largas o servidores sin interfaz gráfica.
```bash
cd mi-extractor
./mvnw clean package -DskipTests
java -cp target/mi-extractor-0.0.1-SNAPSHOT.jar \
     -Dloader.main=com.extractor.mi_extractor.cli.App \
     org.springframework.boot.loader.launch.PropertiesLauncher
```
> Ojo: `java -jar target/mi-extractor-0.0.1-SNAPSHOT.jar` arranca el **modo web**, no la consola. También puedes ejecutar la clase `cli.App` directamente desde tu IDE.

La CLI ofrece tres suites: **1. Extractor de Capítulos**, **2. Editor de Capítulos** y **3. Utilidades de EPUB**. Qué hacer con cada una está en la [Guía de Uso](#-guía-de-uso-qué-hago-en-cada-caso).

### Docker
```bash
cd mi-extractor
docker compose up --build
```
Levanta la app en **http://localhost:8080** con el perfil `prod` (rutas en `/data`, Chrome incluido en la imagen y el driver cacheado en un volumen).

### Tests
```bash
cd mi-extractor
./mvnw test
```

---

## 📖 Guía de Uso: ¿qué hago en cada caso?

### El flujo típico: de la web a un EPUB
1. **Descargar** los capítulos con el *Extractor* (suite 1). Se guardan en `<carpeta>/<nombre de la novela>/OEBPS/Text/Capitulo0001.xhtml`, `Capitulo0002.xhtml`… y la estructura base del EPUB (`mimetype`, `META-INF`, estilos y fuentes) se crea sola.
2. **Pulir y empaquetar** con el *Editor* (suite 2), indicando como carpeta de trabajo la de la novela (la que contiene `OEBPS/`).
3. **Ajustar el libro** con las *Utilidades* (suite 3) si hace falta: dividirlo, convertirlo, etc.

### 📥 Descargar

**Quiero una novela entera**
* **CLI:** suite `1` → carpeta donde guardar → nombre de la novela → opción `1` → URL de la **portada** (la página con el índice, no un capítulo) → cuántos navegadores (Enter = 5) → revisa el resumen y el tiempo estimado → `Y`.
* **Web:** pestaña *Extractor Web* → URL de la portada → tipo `1` → hilos → *Comenzar Extracción*. Al terminar se descarga un `.zip` con el nombre de la novela.

**Solo algunos capítulos**
| Caso | Opción | Qué pide |
|---|---|---|
| Del capítulo X al Y | `4` Rango | Desde / Hasta |
| Unos concretos | `5` Lista | Números separados por comas (`10, 2.1, 2.2`) |
| Uno solo | `3` Buscar por número | Número de capítulo (admite decimales) |
| Hasta el capítulo N | `4` Rango 1–N | En la web también sirve la opción `2` |

**El sitio no tiene índice o quiero seguir el botón "Siguiente"**
Opción `6` (secuencial): URL del **primer capítulo** a bajar → cuántos (`0` = hasta que no haya botón "Siguiente") → número con el que guardar el primero.

**Se cortó la descarga, se apagó el PC o el sitio me bloqueó**
Vuelve a lanzar **la misma descarga** con la misma carpeta y el mismo nombre. Como ya hay capítulos guardados, te preguntará: `R` (o Enter) para **reanudar** bajando solo lo que falta, o `T` para volver a bajarlo todo. Si el aviso fue de **bloqueo del sitio**, espera unos minutos (en *Novela en Español*, ~30 min) antes de reintentar.

**Al final hay capítulos VIP o fallidos**
El resumen los separa y los anota en `FALTANTES.txt`, dentro de la carpeta de la novela (la URL de cada fallo queda en `logs/mi-extractor.log`).
* **VIP:** están bloqueados en el sitio; hay que desbloquearlos en tu cuenta.
* **Fallidos:** relanza la descarga y elige reanudar (solo baja los que faltan), o usa la opción `5` con esos números.

**¿Cuántos navegadores pongo?**
Más navegadores = más rápido, pero más RAM y más peticiones al sitio. **3–5** es un buen punto de partida. Algunos sitios tienen su propio límite y la app lo aplica sola (te avisa si recorta).

### 📘 Crear el EPUB (Editor)
Suite `2` → carpeta de la novela. Orden recomendado:
1. `1` **Estandarizar títulos** y `8` **Reparar errores comunes**.
2. `4` **Validar títulos** (formato "Capítulo # Nombre", o que `<h1>` y `<title>` coincidan) y `9` **Analizar faltantes**: detecta huecos en la numeración y te da la lista lista para pegar en la opción `5` del Extractor.
3. `7` **Crear EPUB** y elige:
   * `3` **EPUB 3 nativo** (recomendado): pide título, autor, sinopsis, géneros, sitio de extracción, tu nombre como creador, la portada (que debe estar en `OEBPS/Images`) y el nombre del archivo de salida.
   * `1` EPUB 2: para lectores antiguos.
   * `2` Re-empaquetar: si ya tienes la carpeta armada a mano (por ejemplo, editada con Sigil).

**Cambiar la portada o la sinopsis de un EPUB ya hecho**
Suite `2` → carpeta donde está el `.epub` → `14` portada / `15` sinopsis y géneros → **`19` GUARDAR CAMBIOS**. Si sales sin la opción 19, los cambios se pierden.

### 🧰 Ajustar el libro (Utilidades)
Suite `3` → carpeta donde está el `.epub` (te lista los archivos para que copies el nombre exacto).

| Quiero… | Opción |
|---|---|
| Partir un EPUB muy largo | `1` Dividir: por rango, en N partes iguales, en trozos de N capítulos o personalizado (decides dónde termina cada libro) |
| Convertir un EPUB 2 a EPUB 3 | `5` (uno) o `6` (todos los de la carpeta). Genera `*_v3.epub` y no toca el original |
| Ver o editar las "tripas" de un EPUB | `2` Descomprimir → editar → `3` Re-empaquetar |
| Añadir páginas de "Inicio de Libro" | `4` |

Las mismas utilidades están en la pestaña *Utilidades EPUB* de la web. La pestaña *Editor XHTML* de la web todavía no está disponible (*Próximamente*); para editar usa la CLI.

### 🩺 Problemas comunes
| Síntoma | Qué hacer |
|---|---|
| "Sitio no soportado" | Revisa la URL. Si es un sitio nuevo, mira [Añadir nuevos sitios](#-configuración-para-desarrolladores-añadir-nuevos-sitios). |
| "No se encontraron capítulos" | Usa la URL de la **portada** de la novela, no la de un capítulo. Si sigue pasando, el sitio pudo cambiar su diseño. |
| Aviso de **bloqueo** del sitio | Espera unos minutos, usa menos navegadores y relanza para reanudar. |
| Chrome no arranca / error del driver | Actualiza Chrome. El `chromedriver` de `mi-extractor/drivers/` debe coincidir con tu versión de Chrome; si lo borras, Selenium descarga el correcto. |
| El PC va lento o se queda sin RAM | Baja los navegadores en paralelo o el tope `app.scraper.max-navegadores`. |
| Editor: "No se encontraron archivos .xhtml ni .epub" | La carpeta de trabajo es la de la **novela** (la que contiene `OEBPS/`), no `OEBPS/Text`. |

---

## ⚙️ Configuración

Parámetros principales en `application.properties`:

| Propiedad | Descripción | Valor local |
|---|---|---|
| `app.scraper.max-navegadores` | Máximo de Chrome abiertos a la vez en toda la app | `10` |
| `app.storage.tmp-dir` | Carpeta de temporales de la web | `${java.io.tmpdir}/mi-extractor` |
| `app.storage.tmp-ttl-hours` | Horas antes de borrar temporales viejos | `24` |
| `app.storage.output-dir` | Carpeta de salida de la web | `${user.home}/Downloads` |
| `app.calibre.path` | Ruta a `ebook-convert` de Calibre (opcional) | ruta de Windows |

El perfil `prod` (`application-prod.properties`) mueve las rutas a `/data`, baja el tope a 4 navegadores y desactiva Calibre por defecto.

---

## 🔧 Configuración para Desarrolladores (Añadir nuevos sitios)
El sistema es modular. Para soportar una nueva web:

1. Crea una clase en `scraping/model/` que extienda de `SitioWebConfig`.
2. Define los selectores CSS para: título, contenido, contenedor del índice, elementos de la lista y enlaces de capítulo, y el dominio en `esSoportado(url)`.
3. Si el índice no está en el HTML (SPA, AJAX...), sobrescribe `extraerIndiceHtml(...)` con una estrategia propia.
4. Opcional, si el sitio limita peticiones: sobrescribe `getMaxNavegadoresRecomendado()`, `getPausaMinMs()` / `getPausaMaxMs()` y `esPaginaDeBloqueo(html)`.
5. Registra la nueva clase en `GestorSitios.java`.

---

## 📄 Licencia
Este proyecto se distribuye bajo la licencia MIT. Eres libre de usarlo, modificarlo y distribuirlo, siempre que se mantenga la atribución al autor original.
