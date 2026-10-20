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

import static co.edu.unal.reto8sqlite.Empresa.Clasificacion.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EmpresasFiltrosTest {
    private static final String TEST_DB = "empresas_filtros_pruebas.db";
    private Context context;
    private EmpresasDbHelper db;
    private long alfa, beta, gamma;

    @Before
    public void preparar() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        context.deleteDatabase(TEST_DB);
        db = new EmpresasDbHelper(context, TEST_DB);
        alfa = db.insertar(new Empresa("Alfa Soft", "", "", "", "",
                EnumSet.of(CONSULTORIA, FABRICA_DE_SOFTWARE)));
        beta = db.insertar(new Empresa("Beta SOFTWARE", "", "", "", "",
                EnumSet.of(DESARROLLO_A_LA_MEDIDA)));
        gamma = db.insertar(new Empresa("Gamma", "", "", "", "",
                EnumSet.of(FABRICA_DE_SOFTWARE)));
    }

    @After
    public void limpiar() {
        db.close();
        context.deleteDatabase(TEST_DB);
    }

    private void comprobar(List<Empresa> empresas, long... ids) {
        assertEquals(ids.length, empresas.size());
        for (int i = 0; i < ids.length; i++) assertEquals(ids[i], empresas.get(i).getId());
    }

    @Test
    public void sinFiltrosDevuelveTodasOrdenadas() {
        comprobar(db.listarFiltradas(null, null), alfa, beta, gamma);
        comprobar(db.listar(), alfa, beta, gamma);
    }

    @Test
    public void nombreParcialToleraMayusculas() {
        comprobar(db.listarFiltradas("sOfT", null), alfa, beta);
        comprobar(db.listarFiltradas(" GAM ", null), gamma);
    }

    @Test
    public void cadaClasificacionIncluyeSeleccionMultiple() {
        comprobar(db.listarFiltradas(null, CONSULTORIA), alfa);
        comprobar(db.listarFiltradas(null, DESARROLLO_A_LA_MEDIDA), beta);
        comprobar(db.listarFiltradas(null, FABRICA_DE_SOFTWARE), alfa, gamma);
    }

    @Test
    public void combinaNombreYClasificacionConAnd() {
        comprobar(db.listarFiltradas("soft", FABRICA_DE_SOFTWARE), alfa);
        comprobar(db.listarFiltradas("soft", DESARROLLO_A_LA_MEDIDA), beta);
        comprobar(db.listarFiltradas("gamma", CONSULTORIA));
    }

    @Test
    public void todasRepresentadaPorNullSoloAplicaNombre() {
        comprobar(db.listarFiltradas("soft", null), alfa, beta);
    }

    @Test
    public void nombreVacioSoloAplicaClasificacion() {
        comprobar(db.listarFiltradas("", CONSULTORIA), alfa);
        comprobar(db.listarFiltradas("   ", FABRICA_DE_SOFTWARE), alfa, gamma);
        comprobar(db.listarFiltradas("", null), alfa, beta, gamma);
    }

    @Test
    public void sinResultadosSeDistingueDeBaseVacia() {
        comprobar(db.listarFiltradas("inexistente", null));
        assertTrue(db.hayEmpresas());
        db.eliminar(alfa);
        db.eliminar(beta);
        db.eliminar(gamma);
        assertFalse(db.hayEmpresas());
    }

    @Test
    public void caracteresEspecialesSeBuscanLiteralmente() {
        long especial = db.insertar(new Empresa("O'Brien 100%_Soft", "", "", "", "",
                EnumSet.of(CONSULTORIA)));
        comprobar(db.listarFiltradas("%_", null), especial);
        comprobar(db.listarFiltradas("O'Brien", CONSULTORIA), especial);
        comprobar(db.listarFiltradas("' OR 1=1 --", null));
        comprobar(db.listarFiltradas("\\", null));
    }
}
