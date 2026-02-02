package com.extractor.mi_extractor;
public abstract class SitioWebConfig {

    public abstract String getNombreSitio();
    public abstract boolean esSoportado(String url);
    public abstract String getSelectorContenido(); 
    public abstract String getSelectorTitulo();    
    public abstract String getSelectorContenedorIndice();
    public abstract String getSelectorElementoLista(); 
    public abstract String getSelectorEnlaceCapitulo();
    public abstract String getSelectorNumeroCapitulo();
}