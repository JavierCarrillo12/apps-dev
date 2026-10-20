package co.edu.unal.reto8sqlite;

import android.content.Context;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.EnumSet;
import java.util.List;

import static org.junit.Assert.*;

/** Prueba SQLite real en una base aislada, sin tocar empresas.db. */
@RunWith(AndroidJUnit4.class)
public class EmpresasDbHelperTest {
    private static final String TEST_DB = "empresas_pruebas.db";
    private Context context;
    private EmpresasDbHelper db;

    @Before
    public void preparar() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB);
        db = new EmpresasDbHelper(context, TEST_DB);
    }

    @After
    public void limpiar() {
        db.close();
        context.deleteDatabase(TEST_DB);
    }

    private Empresa ejemplo(String nombre) {
        return new Empresa(nombre, "https://empresa.example", "+57 3001234567",
                "contacto@empresa.example", "Aplicaciones móviles",
                EnumSet.of(Empresa.Clasificacion.CONSULTORIA,
                        Empresa.Clasificacion.FABRICA_DE_SOFTWARE));
    }

    @Test
    public void crearConsultarYListarConTodosLosCampos() {
        assertTrue(db.listar().isEmpty());
        Empresa original = ejemplo("Zeta's Software");
        long id = db.insertar(original);
        assertTrue(id > 0);
        Empresa guardada = db.obtenerPorId(id);
        assertNotNull(guardada);
        assertEquals(id, guardada.getId());
        assertEquals(original.getNombre(), guardada.getNombre());
        assertEquals(original.getUrl(), guardada.getUrl());
        assertEquals(original.getTelefono(), guardada.getTelefono());
        assertEquals(original.getEmail(), guardada.getEmail());
        assertEquals(original.getProductosServicios(), guardada.getProductosServicios());
        assertEquals(original.getClasificaciones(), guardada.getClasificaciones());
        db.insertar(ejemplo("Alfa"));
        List<Empresa> lista = db.listar();
        assertEquals(2, lista.size());
        assertEquals("Alfa", lista.get(0).getNombre());
        assertEquals(id, lista.get(1).getId());
    }

    @Test
    public void actualizarEliminarYNoReutilizarId() {
        long id = db.insertar(ejemplo("Inicial"));
        long otroId = db.insertar(ejemplo("Otra"));
        Empresa editada = new Empresa(id, "Actualizada", "https://nueva.example", "1234567",
                "nuevo@empresa.example", "Consultoría tecnológica",
                EnumSet.of(Empresa.Clasificacion.DESARROLLO_A_LA_MEDIDA));
        assertTrue(db.actualizar(editada));
        Empresa guardada = db.obtenerPorId(id);
        assertNotNull(guardada);
        assertEquals(editada.getNombre(), guardada.getNombre());
        assertEquals(editada.getUrl(), guardada.getUrl());
        assertEquals(editada.getTelefono(), guardada.getTelefono());
        assertEquals(editada.getEmail(), guardada.getEmail());
        assertEquals(editada.getProductosServicios(), guardada.getProductosServicios());
        assertEquals(editada.getClasificaciones(), guardada.getClasificaciones());
        assertTrue(db.eliminar(id));
        assertNull(db.obtenerPorId(id));
        assertFalse(db.eliminar(id));
        assertFalse(db.actualizar(editada));
        assertEquals("Otra", db.obtenerPorId(otroId).getNombre());
        assertTrue(db.eliminar(otroId));
        assertTrue(db.insertar(ejemplo("Nueva")) > otroId);
    }

    @Test
    public void conservaDatosAlCerrarYReabrirLaBase() {
        long id = db.insertar(ejemplo("Persistente"));
        db.close();
        db = new EmpresasDbHelper(context, TEST_DB);
        Empresa guardada = db.obtenerPorId(id);
        assertNotNull(guardada);
        assertEquals("Persistente", guardada.getNombre());
        assertEquals(ejemplo("Persistente").getClasificaciones(), guardada.getClasificaciones());
    }
}
