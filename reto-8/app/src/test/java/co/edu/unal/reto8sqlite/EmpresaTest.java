package co.edu.unal.reto8sqlite;

import org.junit.Test;

import java.util.EnumSet;

import static org.junit.Assert.*;

public class EmpresaTest {
    @Test(expected = IllegalArgumentException.class)
    public void rechazaNombreVacio() {
        new Empresa("  ", null, null, null, null,
                EnumSet.of(Empresa.Clasificacion.CONSULTORIA));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rechazaClasificacionesVacias() {
        new Empresa("Empresa", null, null, null, null,
                EnumSet.noneOf(Empresa.Clasificacion.class));
    }

    @Test
    public void normalizaCamposYCopiaClasificaciones() {
        EnumSet<Empresa.Clasificacion> seleccion = EnumSet.of(Empresa.Clasificacion.CONSULTORIA);
        Empresa empresa = new Empresa("  Empresa  ", null, " 1234567 ", null, null, seleccion);
        seleccion.clear();
        assertEquals(0, empresa.getId());
        assertEquals("Empresa", empresa.getNombre());
        assertEquals("", empresa.getUrl());
        assertEquals("1234567", empresa.getTelefono());
        assertEquals("", empresa.getEmail());
        assertEquals("", empresa.getProductosServicios());
        assertEquals(EnumSet.of(Empresa.Clasificacion.CONSULTORIA), empresa.getClasificaciones());
    }
}
