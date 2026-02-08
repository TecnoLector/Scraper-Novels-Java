# 📚 Universal Novel Scraper & EPUB Suite

> **Suite de ingeniería editorial automatizada: Extracción, Limpieza, Maquetación y Gestión de Novelas Ligeras.**

Este proyecto es una solución integral escrita en **Java** para automatizar el flujo de trabajo de archivado digital. Transforma novelas web dispersas en miles de páginas HTML en libros electrónicos **EPUB 3.0** profesionales, limpios y validados, listos para dispositivos como Kindle, Kobo o Apple Books.

![Java](https://img.shields.io/badge/Java-21%2B-ED8B00?style=for-the-badge&logo=openjdk&logoColor=white)
![Selenium](https://img.shields.io/badge/Selenium-4.0-43B02A?style=for-the-badge&logo=selenium&logoColor=white)
![Jsoup](https://img.shields.io/badge/Jsoup-Parser-blue?style=for-the-badge)
![EPUB](https://img.shields.io/badge/EPUB-3.0-standard?style=for-the-badge)

## ⚡ Métricas de Rendimiento
El sistema ha sido optimizado para velocidad y estabilidad en entornos de alta demanda:
* **Velocidad de Procesamiento:** Promedio de **~2.2 segundos por capítulo** (ciclo completo: descarga + limpieza + guardado).
* **Capacidad de Carga:** Probado exitosamente con novelas de **+1500 capítulos** en una sola sesión sin desbordamiento de memoria.
* **Tasa de Éxito:** **99.8%** en detección de contenido gracias a selectores dinámicos y heurística de texto.

---

## 🛠️ Arquitectura y Módulos

### 1. El Extractor Híbrido (Scraper)
Utiliza una arquitectura inteligente **Selenium + Jsoup** para combinar la capacidad de renderizado de un navegador real con la velocidad de un parser ligero.

* **Patrón Estrategia (Strategy Pattern):** El sistema detecta automáticamente el dominio web (ej. *Novelas Ligera*, *SkyNovels*) y carga la clase de configuración (`SitioWebConfig`) adecuada en tiempo de ejecución.
* **Bypass de Renderizado:** Utiliza Selenium WebDriver solo para resolver índices complejos (AJAX/JS), cambiando inmediatamente a peticiones HTTP ligeras para el contenido textual.
* **Regex Universal:** Algoritmo de extracción de índices capaz de identificar numeración en formatos no estándar (ej: *"RW 391"*, *"Vol.2 Cap.10"*, *"Episodio Final"*).

### 2. Editor XHTML / EPUB (Post-Procesamiento)
Un motor de limpieza y normalización que transforma el "HTML sucio" de la web en código semántico de calidad editorial.

* **Estandarización Heurística:** Analiza etiquetas (`h1-h6`, `p`, `strong`) usando Regex para encontrar títulos ocultos o mal formateados y los promueve automáticamente a etiquetas `<h1>` semánticas.
* **Sanitización XML:** Repara errores comunes de HTML5 laxo (etiquetas `<br>`, `<img>` sin cerrar) convirtiéndolos a **XHTML 1.1 estricto**, requisito obligatorio para la validación EPUB.
* **Generación de Metadatos:** Gestión automatizada de autor, sinopsis, géneros y portada mediante integración con `EpubLib`.

### 3. Utilidades Avanzadas de EPUB (Splitter & Packer)
Módulo de ingeniería inversa para la gestión de archivos EPUB masivos.

* **División Inteligente (Smart Splitter):** Algoritmo capaz de dividir novelas de +2000 capítulos en volúmenes lógicos sin romper la estructura interna (CSS/Imágenes).
    * **Mapeo Semántico:** Lee los nombres de archivo físicos (`Capitulo0050.xhtml`) y entiende que corresponde al "Capítulo 50" de la historia, permitiendo cortes precisos ignorando prólogos.
* **Re-empaquetado OCF (Open Container Format):** Implementación manual de compresión ZIP que respeta rigurosamente el estándar ISO/IEC de EPUB:
    * Inyección del archivo `mimetype` **sin compresión** (`STORED`) en el primer byte del archivo.
    * Cálculo manual de CRC32 para integridad de datos.

---

## 📂 Estructura del Proyecto

````text
src/main/java/com/extractor
├── App.java                 # CLI y Menú Principal
├── GestorSitios.java        # Factory para selección de estrategias
├── Scraper.java             # Motor de navegación (Selenium)
├── IndiceExtractor.java     # Lógica Regex para parsing de listas
├── ExtractorContenido.java  # Limpieza Jsoup y guardado XHTML
├── EditorXHTML.java         # Motor de limpieza y estandarización
├── UtilidadesEPUB.java      # Motor de división y empaquetado OCF
└── configs/                 # Estrategias por sitio
    ├── SitioNovelasLigera.java
    └── SitioDefault.java
````

##**🚀 Instalación y Uso**
**Requisitos Previos**
-Java JDK 21 o superior.
-Google Chrome (versión reciente).
-Conexión a internet estable.

Ejecución Rápida
Ve a la sección de Releases y descarga el último archivo .jar.

Abre tu terminal en la carpeta de descarga.

Ejecuta:

````Bash
   java -jar ScraperNovelas.jar
````
Guía de Uso del Menú
Opción 1 (Descarga Masiva): Pega la URL de la portada de la novela. El sistema detectará la web y descargará todos los capítulos en carpetas organizadas.

Opción 5 (Editor XHTML/EPUB): Accede a las herramientas de post-producción para limpiar los archivos descargados, generar el índice toc.xhtml y compilar el EPUB final.

Opción 6 (Utilidades EPUB): Usa esta opción si tienes un EPUB gigante y quieres dividirlo en "Libro 1", "Libro 2", etc.

##**🔧 Configuración para Desarrolladores (Añadir nuevos sitios)**
El sistema es modular. Para soportar una nueva web:

Crea una clase en configs/ que extienda de SitioWebConfig.

Define los selectores CSS/XPath para: Título, Contenido, Lista de Capítulos y Enlaces.

Registra la nueva clase en GestorSitios.java.

##**📄 Licencia**
Este proyecto se distribuye bajo la licencia MIT. Eres libre de usarlo, modificarlo y distribuirlo, siempre que se mantenga la atribución al autor original.

Desarrollado con ❤️ y mucho Café.

