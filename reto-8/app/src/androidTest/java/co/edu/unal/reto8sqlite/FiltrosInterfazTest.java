package co.edu.unal.reto8sqlite;

import android.content.Context;
import android.widget.ListView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static androidx.test.espresso.matcher.RootMatchers.isDialog;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class FiltrosInterfazTest {
    private static final String PREFIJO = "Filtro827 ";
    private static final String ALFA = PREFIJO + "Soft Alfa";
    private static final String BETA = PREFIJO + "SOFT Beta";
    private static final String GAMMA = PREFIJO + "Gamma";

    @After
    public void limpiar() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (EmpresasDbHelper db = new EmpresasDbHelper(context)) {
            for (Empresa empresa : db.listarFiltradas(PREFIJO, null)) db.eliminar(empresa.getId());
        }
    }

    private void crear(String nombre, int... checks) {
        onView(withId(R.id.agregar)).perform(click());
        onView(withId(R.id.nombre)).perform(replaceText(nombre), closeSoftKeyboard());
        onView(withId(R.id.url)).perform(replaceText("https://empresa.example"), closeSoftKeyboard());
        for (int id : checks) onView(withId(id)).perform(scrollTo(), click());
        onView(withId(R.id.guardar)).perform(scrollTo(), click());
    }

    private void buscar(String nombre) {
        onView(withId(R.id.buscar_nombre)).perform(replaceText(nombre), closeSoftKeyboard());
    }

    private void clasificacion(String etiqueta) {
        onView(withId(R.id.filtro_clasificacion)).perform(click());
        onData(is(etiqueta)).perform(click());
    }

    private void resultados(String... nombres) {
        onView(withId(R.id.empresas)).check((view, error) -> {
            if (error != null) throw error;
            ListView lista = (ListView) view;
            assertEquals(nombres.length, lista.getAdapter().getCount());
            for (int i = 0; i < nombres.length; i++) {
                assertEquals(nombres[i], ((Empresa) lista.getAdapter().getItem(i)).getNombre());
            }
        });
    }

    @Test
    public void filtrosCombinadosSeConservanDuranteCrudYRecreacion() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            crear(ALFA, R.id.consultoria, R.id.fabrica);
            crear(BETA, R.id.desarrollo);
            buscar(PREFIJO + "soft");
            clasificacion("Fábrica de software");
            resultados(ALFA);
            crear(GAMMA, R.id.fabrica);
            resultados(ALFA);
            onView(withId(R.id.buscar_nombre)).check(matches(withText(PREFIJO + "soft")));
            onView(withId(R.id.filtro_clasificacion))
                    .check(matches(withSpinnerText("Fábrica de software")));
            clasificacion("Todas");
            buscar(PREFIJO);
            resultados(GAMMA, ALFA, BETA);
            clasificacion("Consultoría");
            resultados(ALFA);
            clasificacion("Desarrollo a la medida");
            resultados(BETA);
            clasificacion("Fábrica de software");
            resultados(GAMMA, ALFA);
            buscar(PREFIJO + "soft");
            resultados(ALFA);
            scenario.recreate();
            resultados(ALFA);
            onView(withId(R.id.buscar_nombre)).check(matches(withText(PREFIJO + "soft")));
            onView(withId(R.id.filtro_clasificacion))
                    .check(matches(withSpinnerText("Fábrica de software")));

            onData(anything()).inAdapterView(withId(R.id.empresas)).atPosition(0)
                    .onChildView(withId(R.id.editar)).perform(click());
            // El nuevo nombre deja de coincidir: al regresar debe desaparecer del resultado.
            onView(withId(R.id.nombre)).perform(replaceText(PREFIJO + "Alfa editada"), closeSoftKeyboard());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            resultados();
            onView(withId(R.id.estado)).check(matches(withText(R.string.sin_resultados)));
            onView(withId(R.id.buscar_nombre)).check(matches(withText(PREFIJO + "soft")));
            buscar(PREFIJO);
            resultados(PREFIJO + "Alfa editada", GAMMA);
            onData(anything()).inAdapterView(withId(R.id.empresas)).atPosition(0)
                    .onChildView(withId(R.id.eliminar)).perform(click());
            onView(withText(R.string.cancelar)).inRoot(isDialog()).perform(click());
            resultados(PREFIJO + "Alfa editada", GAMMA);
            onData(anything()).inAdapterView(withId(R.id.empresas)).atPosition(0)
                    .onChildView(withId(R.id.eliminar)).perform(click());
            onView(withText(R.string.eliminar)).inRoot(isDialog()).perform(click());
            resultados(GAMMA);
            onView(withId(R.id.filtro_clasificacion))
                    .check(matches(withSpinnerText("Fábrica de software")));
            clasificacion("Todas");
            resultados(GAMMA, BETA);
            buscar(PREFIJO + "inexistente");
            resultados();
            onView(withId(R.id.estado)).check(matches(withText(R.string.sin_resultados)));
            onView(withId(R.id.limpiar_filtros)).perform(click());
            onView(withId(R.id.buscar_nombre)).check(matches(withText("")));
            onView(withId(R.id.filtro_clasificacion)).check(matches(withSpinnerText("Todas")));
            Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
            try (EmpresasDbHelper db = new EmpresasDbHelper(context)) {
                int total = db.listar().size();
                onView(withId(R.id.empresas)).check((view, error) -> {
                    if (error != null) throw error;
                    assertEquals(total, ((ListView) view).getAdapter().getCount());
                });
            }
        }
    }
}
