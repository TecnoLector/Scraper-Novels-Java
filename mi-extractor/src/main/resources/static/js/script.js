// src/main/resources/static/js/script.js

// --- 1. LÓGICA DEL MODO OSCURO (Segura contra bloqueos) ---
const themeToggle = document.getElementById('themeToggle');
const root = document.documentElement;

try {
    const savedTheme = localStorage.getItem('theme') || 'light';
    setTheme(savedTheme);
} catch (e) {
    setTheme('light');
}

themeToggle.addEventListener('click', () => {
    const currentTheme = root.getAttribute('data-theme');
    const newTheme = currentTheme === 'light' ? 'dark' : 'light';
    setTheme(newTheme);
});

function setTheme(theme) {
    root.setAttribute('data-theme', theme);
    try {
        localStorage.setItem('theme', theme);
    } catch (e) { }
    themeToggle.textContent = theme === 'dark' ? '☀️' : '🌙';
}

// --- 2. LÓGICA VISUAL Y DE MENÚS ---
function actualizarFormulario() {
    const accion = document.getElementById('accionSelector').value;
    const display = document.getElementById('fileNameDisplay');
    const fileInput = document.getElementById('fileInput');

    document.getElementById('cajaDividir').style.display = (accion === 'DIVIDIR') ? 'block' : 'none';
    document.getElementById('cajaInicioLibro').style.display = (accion === 'INICIO_LIBRO') ? 'block' : 'none';
    if (accion === 'REEMPAQUETAR') {
        fileInput.setAttribute('webkitdirectory', '');
        fileInput.setAttribute('directory', '');
        fileInput.removeAttribute('accept'); // Quitamos la restricción de .epub
        display.textContent = "📂 Haz clic para seleccionar CARPETA...";
    } else {
        fileInput.removeAttribute('webkitdirectory');
        fileInput.removeAttribute('directory');
        fileInput.setAttribute('accept', '.epub,.zip');
        display.textContent = "📄 Haz clic para seleccionar ARCHIVO...";
    }
}

function actualizarNombreArchivo() {
    const input = document.getElementById('fileInput');
    const display = document.getElementById('fileNameDisplay');
    if (input.files.length > 0) {
        display.textContent = "📄 " + input.files[0].name;
        display.style.color = "var(--primary-color)";
        display.style.fontWeight = "bold";
    } else {
        display.textContent = "📂 Haz clic para seleccionar archivo...";
        display.style.color = "var(--text-muted)";
        display.style.fontWeight = "normal";
    }
}

function agregarFilaPagina() {
    const div = document.createElement('div');
    div.className = 'fila-dinamica';
    div.innerHTML = `
        <input type="text" class="form-control input-nombre-pag" placeholder="Nombre del Libro (Ej: Vol. 2)">
        <input type="number" class="form-control input-cap-pag" placeholder="Después del Cap #">
        <button type="button" class="btn-remove" onclick="eliminarFila(this)" title="Eliminar fila">✕</button>
    `;
    document.getElementById('contenedorPaginas').appendChild(div);
}
function eliminarFila(boton) {
    boton.parentElement.remove();
}
// Inicializamos la vista al cargar la página
window.onload = function () {
    agregarFilaPagina();
    actualizarFormulario();
};

// --- 3. LÓGICA DE ENVÍO AL SERVIDOR ---

async function comenzarProceso() {
    const fileInput = document.getElementById('fileInput');
    const accion = document.getElementById('accionSelector').value;
    const statusTxt = document.getElementById('statusTxt');
    const pBar = document.getElementById('pBar');
    const pContainer = document.getElementById('pContainer');

    if (fileInput.files.length === 0) {
        alert("⚠️ Selecciona un archivo o carpeta primero.");
        return;
    }

    pContainer.style.display = 'block';
    pBar.style.width = '0%';
    pBar.className = 'progress-bar';

    const formData = new FormData();
    formData.append("accion", accion);

    if (accion === 'REEMPAQUETAR') {
        if (fileInput.files.length > 1) {
            statusTxt.innerHTML = '📦 <span style="color: var(--primary-color)">Comprimiendo carpeta en el navegador...</span>';

            try {
                const zip = new JSZip();
                for (let file of fileInput.files) {
                    zip.file(file.webkitRelativePath || file.name, file);
                }

                const content = await zip.generateAsync({ type: "blob" });

                formData.append("file", content, "carpeta_comprimida.zip");

            } catch (err) {
                statusTxt.innerHTML = '❌ Error al comprimir: ' + err.message;
                return;
            }
        } else {
            formData.append("file", fileInput.files[0]);
        }
    } else {
        formData.append("file", fileInput.files[0]);
    }

    if (accion === 'DIVIDIR') {
        formData.append("tipoDivision", document.getElementById('tipoDivision').value);
        formData.append("parametro", document.getElementById('inputParametro').value);
        formData.append("sitio", document.getElementById('inputSitioDiv').value);
        formData.append("creador", document.getElementById('inputCreadorDiv').value);
    }
    if (accion === 'INICIO_LIBRO') {
        formData.append("sitio", document.getElementById('inputSitioInicio').value);
        formData.append("creador", document.getElementById('inputCreadorInicio').value);
        const nombres = document.querySelectorAll('.input-nombre-pag');
        const capitulos = document.querySelectorAll('.input-cap-pag');
        for (let i = 0; i < nombres.length; i++) {
            if (nombres[i].value && capitulos[i].value) {
                formData.append("nombresPaginas", nombres[i].value);
                formData.append("capitulosAnteriores", capitulos[i].value);
            }
        }
    }


    // --- ENVÍO AL SERVIDOR ---
    statusTxt.innerHTML = '⏳ <span style="color: var(--text-muted)">Subiendo archivo al servidor...</span>';
    pBar.style.width = '10%';
    pBar.textContent = 'Enviando...';

    try {
        const response = await fetch('/api/epub/upload', {
            method: 'POST',
            body: formData
        });

        if (response.ok) {
            const id = await response.text();
            consultarServidor(id);
        } else {
            statusTxt.innerHTML = '<span style="color: var(--error-color)">❌ Error al subir el archivo.</span>';
            pBar.classList.add('error');
            pBar.style.width = '100%';
        }
    } catch (error) {
        statusTxt.innerHTML = '<span style="color: var(--error-color)">❌ Error de conexión con el servidor.</span>';
        pBar.classList.add('error');
    }
}

function consultarServidor(id) {
    const statusTxt = document.getElementById('statusTxt');
    const pBar = document.getElementById('pBar');
    let progress = 5;

    const interval = setInterval(async () => {
        try {
            const res = await fetch('/api/epub/status/' + id);
            const msg = await res.text();
            const msgTrimmed = msg.trim(); // Limpiamos espacios en blanco

            // 1. Intentamos detectar si es el JSON de MULTIHILO
            if (msgTrimmed.startsWith("{")) {
                try {
                    const data = JSON.parse(msgTrimmed);
                    if (data.tipo === "MULTIHILO") {
                        // Actualizamos la barra global
                        pBar.style.width = data.globalPct + '%';
                        pBar.textContent = data.globalPct + '%';
                        statusTxt.innerHTML = `⚙️ <span style="color:var(--primary-color)">Extrayendo con ${data.hilos.length} hilos...</span>`;

                        // Dibujamos las mini-barras en el contenedor
                        const container = document.getElementById('hilosContainer');
                        container.style.display = 'block';
                        container.innerHTML = ''; // Limpiamos para redibujar

                        data.hilos.forEach(hilo => {
                            container.innerHTML += `
                    <div style="margin-bottom: 10px;">
                        <div style="display:flex; justify-content: space-between; font-size: 0.8rem; color: var(--text-muted); margin-bottom: 2px;">
                            <strong>Hilo ${hilo.id}</strong> <span>${hilo.msg}</span>
                        </div>
                        <div class="progress-container" style="display:block; height:8px; background: var(--border-color);">
                            <div class="progress-bar" style="width: ${hilo.pct}%; background-color: var(--secondary-color); transition: width 0.3s;"></div>
                        </div>
                    </div>
                `;
                        });

                        return; // ¡IMPORTANTE! Cortamos aquí para que NO se ejecute la línea de abajo
                    }
                } catch (e) {
                    console.error("Error parseando JSON de hilos:", e);
                }
            }
            const mensajeLimpio = msg.trim().toUpperCase();

            if (mensajeLimpio.includes("LISTO")) {
                clearInterval(interval);
                pBar.style.width = '100%';
                pBar.classList.add('success');
                pBar.textContent = '¡Completado!';
                statusTxt.innerHTML = "✅ <strong>¡Proceso terminado!</strong> <br> <a href='/api/epub/download/" + id + "' class='download-link'>Descargar archivo final</a>";
                window.location.href = '/api/epub/download/' + id;
            }
            else if (msg.startsWith("ERROR")) {
                clearInterval(interval);
                pBar.style.width = '100%';
                pBar.classList.add('error');
                pBar.textContent = 'Error';
                statusTxt.innerHTML = '<span style="color: var(--error-color)">❌ ' + msg + '</span>';
            }
            else {
                statusTxt.innerHTML = "⚙️ " + msg;
                const match = msg.match(/(\d+)%/);
                if (match) {
                    progress = parseInt(match[1]);
                } else if (progress < 90) {
                    progress += 2;
                }
                pBar.style.width = progress + '%';
                pBar.textContent = progress > 10 ? progress + '%' : '';
            }
        } catch (e) {
            console.error("Error consultando estado", e);
        }
    }, 800);
}

// --- 4. LÓGICA DE PESTAÑAS (TABS) ---
function cambiarModulo(idModulo, botonPresionado) {
    const modulos = document.querySelectorAll('.modulo-seccion');
    modulos.forEach(modulo => modulo.style.display = 'none');

    const botones = document.querySelectorAll('.tab-btn');
    botones.forEach(btn => btn.classList.remove('active'));

    document.getElementById(idModulo).style.display = 'block';
    botonPresionado.classList.add('active');

    document.getElementById('pContainer').style.display = 'none';
    document.getElementById('statusTxt').innerHTML = '';
}

// --- 5. LÓGICA DE EXTRACCIÓN WEB ---
function actualizarInterfazExtraccion() {
    const opcion = document.getElementById('tipoExtraccion').value;
    const labelUrl = document.getElementById('labelUrl');

    // Control de visibilidad de campos
    document.getElementById('campoRango').style.display = (opcion === "4") ? "flex" : "none";
    document.getElementById('campoLista').style.display = (opcion === "5") ? "block" : "none";
    document.getElementById('campoLimite').style.display = (opcion === "2" || opcion === "6") ? "block" : "none";

    // Ajuste de etiquetas
    if (opcion === "6") {
        labelUrl.innerText = "URL del PRIMER capítulo:";
        document.getElementById('labelLimite').innerText = "Límite de capítulos (0 = todo):";
    } else {
        labelUrl.innerText = "URL de la Portada:";
        document.getElementById('labelLimite').innerText = "Descargar hasta el capítulo #:";
    }
}

async function iniciarExtraccion() {
    const urlInput = document.getElementById('inputUrlScrap').value.trim();
    if (!urlInput) { alert("Ingresa una URL válida"); return; }

    const statusTxt = document.getElementById('statusTxt');
    const pBar = document.getElementById('pBar');
    const pContainer = document.getElementById('pContainer');

    pContainer.style.display = 'block';
    pBar.className = 'progress-bar';
    statusTxt.innerHTML = '⏳ Conectando con Selenium...';

    const formData = new FormData();
    // Capturamos TODO el formulario
    formData.append("opcion", document.getElementById('tipoExtraccion').value);
    formData.append("url", urlInput);
    formData.append("hilos", document.getElementById('inputHilos').value || 3);
    formData.append("limite", document.getElementById('inputLimiteCap').value || 0);
    formData.append("inicio", document.getElementById('inputInicio').value || 0);
    formData.append("fin", document.getElementById('inputFin').value || 0);
    formData.append("lista", document.getElementById('inputLista').value || "");

    try {
        const response = await fetch('/api/epub/scrape', { method: 'POST', body: formData });
        if (response.ok) {
            const id = await response.text();
            consultarServidor(id);
        }
    } catch (error) { statusTxt.innerHTML = '❌ Error de conexión'; }
}