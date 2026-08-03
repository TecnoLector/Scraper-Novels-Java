# 📚 Universal Novel Scraper & EPUB Suite

> **Suite de ingeniería editorial automatizada: Extracción, Limpieza, Maquetación y Gestión de Novelas Ligeras.**

Este proyecto es una solución integral escrita en **Java 21** y **Spring Boot** para automatizar el flujo de trabajo de archivado digital. Transforma novelas web dispersas en miles de páginas HTML en libros electrónicos **EPUB 3.0** profesionales, limpios y validados, listos para dispositivos como Kindle, Kobo o Apple Books.

![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Spring Boot](https://img.shields.io/badge/Spring_Boot-3.0-6DB33F?style=for-the-badge&logo=spring&logoColor=white)
![Selenium](https://img.shields.io/badge/Selenium-4.0-43B02A?style=for-the-badge&logo=selenium&logoColor=white)
![Jsoup](https://img.shields.io/badge/Jsoup-Parser-blue?style=for-the-badge)
![EPUB](https://img.shields.io/badge/EPUB-3.0-standard?style=for-the-badge)

## ✨ Novedades: Interfaz Web Integrada (Spring Boot)
El proyecto ha evolucionado de una herramienta de consola (CLI) a una aplicación web Full-Stack. Ahora cuenta con un panel de control moderno (con Modo Oscuro) que permite gestionar la división de libros, inyección de páginas y reempaquetado con una barra de progreso en tiempo real mediante peticiones asíncronas.

## ⚡ Métricas de Rendimiento
El sistema ha sido optimizado para velocidad y estabilidad en entornos de alta demanda:
* **Velocidad de Procesamiento:** Promedio de **~2.2 segundos por capítulo** (ciclo completo: descarga + limpieza + guardado).
* **Capacidad de Carga:** Probado exitosamente con novelas de **+1500 capítulos** en una sola sesión sin desbordamiento de memoria.
* **Tasa de Éxito:** **99.8%** en detección de contenido gracias a selectores dinámicos y heurística de texto.

---

## 🛠️ Arquitectura y Módulos

### 1. API REST y Panel de Control (Novedad)
* **Panel Reactivo:** Interfaz moderna (HTML/CSS/JS) con cambio de tema dinámico y gestión de DOM para inyección de parámetros.
* **Procesamiento Asíncrono:** La API (`EpubController`) asigna UUIDs únicos a cada tarea, permitiendo al frontend consultar el progreso (Long Polling) sin bloquear el servidor.
* **Compresión Cliente-Servidor:** Uso inteligente de `JSZip` en el navegador para comprimir carpetas localmente antes de enviarlas al backend, optimizando drásticamente el ancho de banda.

### 2. El Extractor Híbrido (Scraper)
Utiliza una arquitectura inteligente **Selenium + Jsoup** para combinar la capacidad de renderizado de un navegador real con la velocidad de un parser ligero.
* **Patrón Estrategia (Strategy Pattern):** El sistema detecta automáticamente el dominio web (ej. *Novelas Ligera*, *SkyNovels*) y carga la clase de configuración (`SitioWebConfig`) adecuada en tiempo de ejecución.
* **Bypass de Renderizado:** Utiliza Selenium WebDriver solo para resolver índices complejos (AJAX/JS), cambiando inmediatamente a peticiones HTTP ligeras para el contenido textual.
* **Regex Universal:** Algoritmo de extracción de índices capaz de identificar numeración en formatos no estándar (ej: *"RW 391"*, *"Vol.2 Cap.10"*, *"Episodio Final"*).

### 3. Editor XHTML / EPUB (Post-Procesamiento)
Un motor de limpieza y normalización que transforma el "HTML sucio" de la web en código semántico de calidad editorial.
* **Estandarización Heurística:** Analiza etiquetas (`h1-h6`, `p`, `strong`) usando Regex para encontrar títulos ocultos o mal formateados y los promueve automáticamente a etiquetas `<h1>` semánticas.
* **Sanitización XML:** Repara errores comunes de HTML5 laxo (etiquetas `<br>`, `<img>` sin cerrar) convirtiéndolos a **XHTML 1.1 estricto**, requisito obligatorio para la validación EPUB.
* **Generación de Metadatos:** Gestión automatizada de autor, sinopsis, géneros y portada mediante integración con `EpubLib`.

### 4. Utilidades Avanzadas de EPUB (Splitter & Packer)
Módulo de ingeniería inversa para la gestión de archivos EPUB masivos.
* **División Inteligente (Smart Splitter):** Algoritmo capaz de dividir novelas de +2000 capítulos en volúmenes lógicos sin romper la estructura interna (CSS/Imágenes).
    * **Mapeo Semántico:** Lee los nombres de archivo físicos (`Capitulo0050.xhtml`) y entiende que corresponde al "Capítulo 50" de la historia, permitiendo cortes precisos ignorando prólogos.
* **Re-empaquetado OCF (Open Container Format):** Implementación manual de compresión ZIP que respeta rigurosamente el estándar ISO/IEC de EPUB:
    * Inyección del archivo `mimetype` **sin compresión** (`STORED`) en el primer byte del archivo.
    * Cálculo manual de CRC32 para integridad de datos.

---

## 📂 Estructura del Proyecto

```text
src/main/
├── java/com/extractor/mi_extractor/
│   ├── controller/          # API REST (EpubController)
│   ├── service/             # Lógica Asíncrona (EpubService, StorageService)
│   ├── processor/           # Lógica de división, empaquetado e inyección
│   ├── configs/             # Estrategias de Scraping por sitio
│   ├── Scraper.java         # Motor de navegación (Selenium)
│   ├── EditorXHTML.java     # Motor de limpieza y estandarización
│   └── App.java             # Menú CLI Legacy
└── resources/
    ├── static/              # Frontend (index.html, js/script.js, css/style.css)
    └── application.properties # Configuración de Spring Boot y Rutas de Drivers
```

## Instalación y Uso
Requisitos Previos

Java JDK 21 o superior.

Google Chrome/Chromium (versión reciente).

Maven (incluido en el Wrapper del proyecto).

**Modo Web (Recomendado)**
Abre tu terminal en la raíz del proyecto.

Inicia el servidor de Spring Boot ejecutando:

Bash
./mvnw spring-boot:run
Abre tu navegador y dirígete a: http://localhost:8080

**Modo Consola (CLI Heredado)**
Si prefieres usar la herramienta de extracción desde la terminal (ideal para servidores sin interfaz gráfica):

Compila el proyecto: ./mvnw clean package

Ejecuta el archivo generado:

Bash
java -jar target/mi-extractor-0.0.1-SNAPSHOT.jar

## 🔧 Configuración para Desarrolladores (Añadir nuevos sitios)
El sistema es modular. Para soportar una nueva web en el Scraper:

Crea una clase en configs/ que extienda de SitioWebConfig.

Define los selectores CSS/XPath para: Título, Contenido, Lista de Capítulos y Enlaces.

Registra la nueva clase en GestorSitios.java.

## 📄 Licencia
Este proyecto se distribuye bajo la licencia MIT. Eres libre de usarlo, modificarlo y distribuirlo, siempre que se mantenga la atribución al autor original.