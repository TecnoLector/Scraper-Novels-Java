# ============================================================================
#  mi-extractor — imagen de produccion (multi-stage)
#  - Stage build: Maven + JDK 21 -> jar ejecutable
#  - Stage runtime: JRE 21 + Google Chrome stable + fuentes, usuario no-root
#  El chromedriver NO se incluye: Selenium Manager lo descarga una vez (segun la
#  version de Chrome instalada) y lo cachea en SE_CACHE_PATH (volumen en compose).
# ============================================================================

# ---------- Stage 1: build ----------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Primero el pom para cachear la descarga de dependencias en una capa aparte.
COPY pom.xml .
RUN mvn -q dependency:go-offline
# Luego el codigo y el empaquetado (sin tests: necesitan Chrome; se corren en CI/local).
COPY src ./src
RUN mvn -q -DskipTests clean package

# ---------- Stage 2: runtime ----------
FROM eclipse-temurin:21-jre AS runtime

# Google Chrome stable + fuentes. 'apt-get install' del .deb resuelve sus dependencias.
RUN apt-get update && apt-get install -y --no-install-recommends \
        wget gnupg ca-certificates fonts-liberation \
    && wget -q -O /tmp/chrome.deb https://dl.google.com/linux/direct/google-chrome-stable_current_amd64.deb \
    && apt-get install -y --no-install-recommends /tmp/chrome.deb \
    && rm -f /tmp/chrome.deb \
    && rm -rf /var/lib/apt/lists/*

# Usuario no-root y carpetas de datos/cache con permisos.
RUN useradd -m -u 1000 app \
    && mkdir -p /data/tmp /data/salidas /home/app/.cache/selenium \
    && chown -R app:app /data /home/app/.cache

WORKDIR /app
COPY --from=build /build/target/*.jar app.jar
RUN chown app:app app.jar

USER app

ENV SPRING_PROFILES_ACTIVE=prod \
    SE_CACHE_PATH=/home/app/.cache/selenium \
    JAVA_TOOL_OPTIONS="-Dfile.encoding=UTF-8"

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
