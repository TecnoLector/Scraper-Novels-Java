package com.extractor.mi_extractor.controller;
import com.extractor.mi_extractor.scraping.service.GestorSitios;


import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * Valida en el borde la URL que el usuario quiere scrapear, antes de encolar el trabajo.
 * Cierra el vector SSRF: solo esquemas http/https, solo sitios soportados (allowlist, sin
 * fallback silencioso) y rechazo de direcciones no públicas (loopback, link-local -> metadata
 * de instancia, privadas, ::1/ULA) como defensa en profundidad.
 */
@Component
public class UrlSafetyValidator {

    public void validar(String url) {
        if (url == null || url.isBlank()) {
            throw rechazo("La URL está vacía.");
        }

        final URI uri;
        try {
            uri = new URI(url.trim());
        } catch (URISyntaxException e) {
            throw rechazo("La URL no es válida.");
        }

        String esquema = uri.getScheme();
        if (esquema == null
                || !(esquema.equalsIgnoreCase("http") || esquema.equalsIgnoreCase("https"))) {
            throw rechazo("Solo se permiten URLs http o https.");
        }

        String host = uri.getHost();
        if (host == null || host.isBlank()) {
            throw rechazo("La URL no tiene un host válido.");
        }

        // Allowlist: debe ser un sitio soportado (sin el fallback a SkyNovels).
        if (!GestorSitios.esUrlSoportada(url)) {
            throw rechazo("El sitio de esa URL no está soportado.");
        }

        // Defensa en profundidad: resolver el host y rechazar destinos internos.
        final InetAddress[] direcciones;
        try {
            direcciones = InetAddress.getAllByName(host);
        } catch (UnknownHostException e) {
            throw rechazo("No se pudo resolver el host de la URL.");
        }
        for (InetAddress dir : direcciones) {
            if (esDireccionNoPublica(dir)) {
                throw rechazo("La URL apunta a una dirección interna no permitida.");
            }
        }
    }

    private boolean esDireccionNoPublica(InetAddress dir) {
        if (dir.isAnyLocalAddress()      // 0.0.0.0 / ::
                || dir.isLoopbackAddress()   // 127.0.0.0/8, ::1
                || dir.isLinkLocalAddress()  // 169.254.0.0/16 (metadata AWS), fe80::/10
                || dir.isSiteLocalAddress()  // 10/8, 172.16/12, 192.168/16
                || dir.isMulticastAddress()) {
            return true;
        }
        // IPv6 Unique Local Address fc00::/7 (no lo cubre isSiteLocalAddress).
        byte[] b = dir.getAddress();
        return b.length == 16 && (b[0] & 0xfe) == 0xfc;
    }

    private UrlNoPermitidaException rechazo(String mensaje) {
        return new UrlNoPermitidaException(mensaje);
    }
}
