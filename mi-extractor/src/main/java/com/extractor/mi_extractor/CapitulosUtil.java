package com.extractor.mi_extractor;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utilidades puras sobre el mapa de capítulos (número -> URL): filtrado por opción,
 * extracción del número desde el título y formateo decimal-safe. Extraído de
 * {@link MenuExtractor} sin cambios de comportamiento; lo usan CLI y web.
 */
class CapitulosUtil {

    private CapitulosUtil() {
    }

    /** Une los números de capítulo exactos (decimales incluidos) separados por coma. */
    static String formatearListaCaps(java.util.Collection<Double> caps) {
        StringBuilder sb = new StringBuilder();
        for (Double cap : caps) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(formatearNumeroCap(cap));
        }
        return sb.toString();
    }

    /** Muestra 15 (entero) o 15.1 (decimal); NUNCA inventa un entero a partir de un decimal. */
    static String formatearNumeroCap(double cap) {
        if (cap == Math.floor(cap) && !Double.isInfinite(cap)) {
            return String.valueOf((long) cap);
        }
        return String.valueOf(cap);
    }

    static Map<Double, String> filtrarCapitulos(Map<Double, String> todosLosCapitulos, int opcion,
            int limite, int inicio, int fin, String listaStr) {
        Map<Double, String> filtrados = new HashMap<>();
        if (todosLosCapitulos == null) {
            return filtrados;
        }
        switch (opcion) {
            case 1:
            case 2:
                int max = (opcion == 2 && limite > 0) ? limite : Integer.MAX_VALUE;
                for (Double k : todosLosCapitulos.keySet())
                    if (k <= max)
                        filtrados.put(k, todosLosCapitulos.get(k));
                break;
            case 4:
                for (Double k : todosLosCapitulos.keySet())
                    if (k >= inicio && k <= fin)
                        filtrados.put(k, todosLosCapitulos.get(k));
                break;
            case 5:
                String[] nums = (listaStr != null) ? listaStr.split(",") : new String[0];
                for (String n : nums) {
                    try {
                        double nb = Double.parseDouble(n.trim());
                        if (todosLosCapitulos.containsKey(nb))
                            filtrados.put(nb, todosLosCapitulos.get(nb));
                    } catch (Exception e) {
                    }
                }
                break;
            case 3:
                double dLimite = (double) limite;
                if (todosLosCapitulos.containsKey(dLimite))
                    filtrados.put(dLimite, todosLosCapitulos.get(dLimite));
                break;
        }
        return filtrados;
    }

    static double extraerNumeroDeTitulo(String titulo) {
        if (titulo == null)
            return 0.0;
        Pattern pattern = Pattern.compile("\\d+(\\.\\d+)?");
        Matcher matcher = pattern.matcher(titulo);
        if (matcher.find()) {
            try {
                return Double.parseDouble(matcher.group());
            } catch (NumberFormatException e) {
                return 0.0;
            }
        }
        return 0.0;
    }
}
