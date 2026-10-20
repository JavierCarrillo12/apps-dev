package co.edu.unal.reto8sqlite;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/** Datos de una empresa. El ID 0 representa una empresa todavía sin guardar. */
public final class Empresa {
    public enum Clasificacion {
        CONSULTORIA("Consultoría"),
        DESARROLLO_A_LA_MEDIDA("Desarrollo a la medida"),
        FABRICA_DE_SOFTWARE("Fábrica de software");

        private final String etiqueta;

        Clasificacion(String etiqueta) {
            this.etiqueta = etiqueta;
        }

        public String getEtiqueta() {
            return etiqueta;
        }
    }

    private final long id;
    private final String nombre;
    private final String url;
    private final String telefono;
    private final String email;
    private final String productosServicios;
    private final Set<Clasificacion> clasificaciones;

    public Empresa(String nombre, String url, String telefono, String email,
                   String productosServicios, Set<Clasificacion> clasificaciones) {
        this(0, nombre, url, telefono, email, productosServicios, clasificaciones);
    }

    public Empresa(long id, String nombre, String url, String telefono, String email,
                   String productosServicios, Set<Clasificacion> clasificaciones) {
        if (nombre == null || nombre.trim().isEmpty()) {
            throw new IllegalArgumentException("El nombre es obligatorio");
        }
        if (clasificaciones == null || clasificaciones.isEmpty()) {
            throw new IllegalArgumentException("Seleccione al menos una clasificación");
        }
        this.id = id;
        this.nombre = nombre.trim();
        this.url = texto(url);
        this.telefono = texto(telefono);
        this.email = texto(email);
        this.productosServicios = texto(productosServicios);
        this.clasificaciones = Collections.unmodifiableSet(EnumSet.copyOf(clasificaciones));
    }

    private static String texto(String valor) {
        return valor == null ? "" : valor.trim();
    }

    public long getId() { return id; }
    public String getNombre() { return nombre; }
    public String getUrl() { return url; }
    public String getTelefono() { return telefono; }
    public String getEmail() { return email; }
    public String getProductosServicios() { return productosServicios; }
    public Set<Clasificacion> getClasificaciones() { return clasificaciones; }
}
