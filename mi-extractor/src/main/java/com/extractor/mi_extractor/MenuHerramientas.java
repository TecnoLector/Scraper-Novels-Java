package com.extractor.mi_extractor;

import java.util.Scanner;

public class MenuHerramientas {
    public static void iniciar(Scanner scanner) {
        HerramientasEpub herramientas = new HerramientasEpub(scanner);
        herramientas.mostrarMenu();
    }
}
