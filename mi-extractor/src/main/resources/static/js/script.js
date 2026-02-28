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
    } catch(e) {}
    themeToggle.textContent = theme === 'dark' ? '☀️' : '🌙';
}

// --- 2. LÓGICA VISUAL Y DE MENÚS ---
function actualizarFormulario() {
    const accion = document.getElementById('accionSelector').value;
    const display = document.getElementById('fileNameDisplay');
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
window.onload = function() {
    agregarFilaPagina();
    actualizarFormulario();
};

// --- 3. LÓGICA DE ENVÍO AL SERVIDOR ---
async function comenzarProceso() {
    const fileInput = document.getElementById('fileInput');
    const accion = document.getElementById('accionSelector').value;

    if (fileInput.files.length === 0) {
        alert("⚠️ Selecciona un archivo o carpeta primero.");
        return;
    }
    const formData = new FormData();
    formData.append("accion", accion);
    if (accion === 'REEMPAQUETAR') {
        for (let file of fileInput.files) {
            formData.append("files", file); 
        }
    } else {
        formData.append("file", fileInput.files[0]);
    }if (accion === 'DIVIDIR') {
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

    // Resetear barra visualmente antes de empezar
    const pBar = document.getElementById('pBar');
    const pContainer = document.getElementById('pContainer');
    const statusTxt = document.getElementById('statusTxt');
    
    pContainer.style.display = 'block';
    pBar.style.width = '5%';
    pBar.textContent = '';
    pBar.className = 'progress-bar'; 
    statusTxt.innerHTML = '⏳ <span style="color: var(--text-muted)">Subiendo archivo al servidor...</span>';

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
        pBar.style.width = '100%';
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