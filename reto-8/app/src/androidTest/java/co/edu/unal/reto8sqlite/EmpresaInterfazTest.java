package co.edu.unal.reto8sqlite;

import android.content.Context;
import android.content.Intent;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.onData;
import static androidx.test.espresso.action.ViewActions.*;
import static androidx.test.espresso.assertion.ViewAssertions.*;
import static androidx.test.espresso.matcher.ViewMatchers.*;
import static org.hamcrest.Matchers.*;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class EmpresaInterfazTest {
    private static final String NOMBRE = "Empresa prueba interfaz 82746";
    private static final String EDITADA = "Empresa editada interfaz 82746";

    @After
    public void limpiarSoloEmpresasDePrueba() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (EmpresasDbHelper db = new EmpresasDbHelper(context)) {
            for (Empresa empresa : db.listar()) {
                if (empresa.getNombre().equals(NOMBRE) || empresa.getNombre().equals(EDITADA)) {
                    db.eliminar(empresa.getId());
                }
            }
        }
    }

    @Test
    public void crearReabrirEditarCancelarYConfirmarEliminacion() {
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withId(R.id.agregar)).perform(click());
            onView(withId(R.id.nombre)).perform(replaceText(NOMBRE), closeSoftKeyboard());
            onView(withId(R.id.url)).perform(replaceText("https://empresa.example"), closeSoftKeyboard());
            onView(withId(R.id.telefono)).perform(replaceText("+57 3001234567"), closeSoftKeyboard());
            onView(withId(R.id.email)).perform(replaceText("contacto@empresa.example"), closeSoftKeyboard());
            onView(withId(R.id.productos)).perform(replaceText("Aplicaciones y consultoría"), closeSoftKeyboard());
            onView(withId(R.id.consultoria)).perform(scrollTo(), click());
            onView(withId(R.id.fabrica)).perform(scrollTo(), click());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withText(NOMBRE)).check(matches(isDisplayed()));
        }
        // Se destruye y vuelve a abrir la actividad; el listado se reconstruye desde SQLite.
        try (ActivityScenario<MainActivity> scenario = ActivityScenario.launch(MainActivity.class)) {
            onView(withText(NOMBRE)).check(matches(isDisplayed()));
            onData(anything()).inAdapterView(withId(R.id.empresas))
                    .atPosition(posicion(NOMBRE)).onChildView(withId(R.id.editar)).perform(click());
            onView(withId(R.id.nombre)).check(matches(withText(NOMBRE)));
            onView(withId(R.id.url)).check(matches(withText("https://empresa.example")));
            onView(withId(R.id.email)).check(matches(withText("contacto@empresa.example")));
            onView(withId(R.id.nombre)).perform(replaceText(EDITADA), closeSoftKeyboard());
            onView(withId(R.id.consultoria)).perform(scrollTo()).check(matches(isChecked()));
            onView(withId(R.id.fabrica)).perform(scrollTo()).check(matches(isChecked()));
            onView(withId(R.id.desarrollo)).perform(scrollTo(), click());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withText(EDITADA)).check(matches(isDisplayed()));
            pulsarEliminar();
            onView(withText(R.string.confirmar_eliminacion)).check(matches(isDisplayed()));
            scenario.recreate();
            onView(withText(R.string.confirmar_eliminacion)).check(matches(isDisplayed()));
            onView(withText(R.string.cancelar)).perform(click());
            onView(withText(EDITADA)).check(matches(isDisplayed()));
            assertTrue(posicion(EDITADA) >= 0);
            pulsarEliminar();
            onView(withText(R.string.eliminar)).inRoot(
                    androidx.test.espresso.matcher.RootMatchers.isDialog()).perform(click());
            onView(withText(EDITADA)).check(doesNotExist());
            assertEquals(-1, posicion(EDITADA));
        }
    }

    private void pulsarEliminar() {
        onData(anything()).inAdapterView(withId(R.id.empresas))
                .atPosition(posicion(EDITADA)).onChildView(withId(R.id.eliminar)).perform(click());
    }

    private int posicion(String nombre) {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        try (EmpresasDbHelper db = new EmpresasDbHelper(context)) {
            java.util.List<Empresa> empresas = db.listar();
            for (int i = 0; i < empresas.size(); i++) {
                if (empresas.get(i).getNombre().equals(nombre)) return i;
            }
            return -1;
        }
    }

    @Test
    public void validarYConservarBorradorAlRecrearFormulario() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent intent = new Intent(context, EmpresaFormActivity.class);
        try (ActivityScenario<EmpresaFormActivity> scenario = ActivityScenario.launch(intent)) {
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withId(R.id.nombre)).check(matches(hasErrorText(
                    context.getString(R.string.nombre_obligatorio))));
            onView(withId(R.id.nombre)).perform(scrollTo(), replaceText(NOMBRE), closeSoftKeyboard());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withId(R.id.error_formulario)).check(matches(withText(R.string.clasificacion_obligatoria)));
            scenario.recreate();
            onView(withId(R.id.error_formulario))
                    .check(matches(withText(R.string.clasificacion_obligatoria)));
            assertEquals(-1, posicion(NOMBRE));
            onView(withId(R.id.url)).perform(scrollTo(), replaceText("url incorrecta"), closeSoftKeyboard());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withId(R.id.url)).check(matches(hasErrorText(context.getString(R.string.url_invalida))));
            onView(withId(R.id.url)).perform(scrollTo(), replaceText("https://borrador.example"), closeSoftKeyboard());
            onView(withId(R.id.email)).perform(scrollTo(), replaceText("correo incorrecto"), closeSoftKeyboard());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withId(R.id.email)).check(matches(hasErrorText(context.getString(R.string.email_invalido))));
            onView(withId(R.id.email)).perform(scrollTo(), replaceText(""), closeSoftKeyboard());
            onView(withId(R.id.telefono)).perform(scrollTo(), replaceText("12"), closeSoftKeyboard());
            onView(withId(R.id.guardar)).perform(scrollTo(), click());
            onView(withId(R.id.telefono)).check(matches(hasErrorText(context.getString(R.string.telefono_invalido))));
            onView(withId(R.id.telefono)).perform(scrollTo(), replaceText(""), closeSoftKeyboard());
            onView(withId(R.id.consultoria)).perform(scrollTo(), click());
            scenario.recreate();
            onView(withId(R.id.nombre)).check(matches(withText(NOMBRE)));
            onView(withId(R.id.url)).check(matches(withText("https://borrador.example")));
            onView(withId(R.id.consultoria)).perform(scrollTo()).check(matches(isChecked()));
            assertEquals(-1, posicion(NOMBRE));
            onView(withId(R.id.cancelar)).perform(scrollTo(), click());
        }
        assertEquals(-1, posicion(NOMBRE));
    }

    @Test
    public void guardarRepetidoNoDuplicaYCancelarEdicionNoModifica() {
        Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        Intent nueva = new Intent(context, EmpresaFormActivity.class);
        try (ActivityScenario<EmpresaFormActivity> scenario = ActivityScenario.launch(nueva)) {
            onView(withId(R.id.nombre)).perform(replaceText(NOMBRE), closeSoftKeyboard());
            onView(withId(R.id.consultoria)).perform(scrollTo(), click());
            // Dos llamadas en el mismo turno de UI ejercitan incluso el guardado antes de finish().
            scenario.onActivity(activity -> {
                activity.findViewById(R.id.guardar).performClick();
                activity.findViewById(R.id.guardar).performClick();
            });
        }
        long id;
        try (EmpresasDbHelper db = new EmpresasDbHelper(context)) {
            List<Empresa> creadas = db.listarFiltradas(NOMBRE, null);
            assertEquals(1, creadas.size());
            id = creadas.get(0).getId();
        }
        Intent editar = new Intent(context, EmpresaFormActivity.class)
                .putExtra(EmpresaFormActivity.EXTRA_ID, id);
        try (ActivityScenario<EmpresaFormActivity> scenario = ActivityScenario.launch(editar)) {
            onView(withId(R.id.nombre)).check(matches(withText(NOMBRE)));
            onView(withId(R.id.nombre)).perform(replaceText(EDITADA), closeSoftKeyboard());
            scenario.recreate();
            onView(withId(R.id.nombre)).check(matches(withText(EDITADA)));
            onView(withId(R.id.cancelar)).perform(scrollTo(), click());
        }
        try (EmpresasDbHelper db = new EmpresasDbHelper(context)) {
            Empresa original = db.obtenerPorId(id);
            assertNotNull(original);
            assertEquals(id, original.getId());
            assertEquals(NOMBRE, original.getNombre());
            assertTrue(db.listarFiltradas(EDITADA, null).isEmpty());
        }
    }
}
