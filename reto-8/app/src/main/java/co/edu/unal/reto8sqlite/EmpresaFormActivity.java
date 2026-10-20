package co.edu.unal.reto8sqlite;

import android.database.sqlite.SQLiteException;
import android.graphics.Rect;
import android.os.Bundle;
import android.util.Patterns;
import android.view.View;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import java.util.EnumSet;

public class EmpresaFormActivity extends AppCompatActivity {
    public static final String EXTRA_ID = "empresa_id";
    private EmpresasDbHelper db;
    private long empresaId;
    private EditText nombre, url, telefono, email, productos;
    private CheckBox consultoria, desarrollo, fabrica;
    private TextView error;
    private boolean cargaCorrecta = true;
    private boolean guardando;
    private int mensajeError;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_empresa_form);
        MainActivity.ajustarInsets(this);
        db = new EmpresasDbHelper(this);
        empresaId = getIntent().getLongExtra(EXTRA_ID, 0);
        nombre = findViewById(R.id.nombre);
        url = findViewById(R.id.url);
        telefono = findViewById(R.id.telefono);
        email = findViewById(R.id.email);
        productos = findViewById(R.id.productos);
        consultoria = findViewById(R.id.consultoria);
        desarrollo = findViewById(R.id.desarrollo);
        fabrica = findViewById(R.id.fabrica);
        error = findViewById(R.id.error_formulario);
        ((TextView) findViewById(R.id.titulo_formulario)).setText(
                empresaId == 0 ? R.string.agregar_empresa : R.string.editar_empresa);
        findViewById(R.id.cancelar).setOnClickListener(v -> finish());
        findViewById(R.id.guardar).setOnClickListener(v -> guardar());
        if (savedInstanceState == null && empresaId != 0) {
            cargarEmpresa();
        } else if (savedInstanceState != null) {
            // Android restaura textos, checks y scroll por sus IDs; no sobrescribirlos.
            cargaCorrecta = savedInstanceState.getBoolean("carga_correcta", true);
            findViewById(R.id.guardar).setEnabled(cargaCorrecta);
            int mensaje = savedInstanceState.getInt("mensaje_error", 0);
            if (!cargaCorrecta) mensaje = R.string.error_cargar;
            if (mensaje != 0) mostrarError(mensaje);
        }
    }

    private void cargarEmpresa() {
        try {
            Empresa empresa = db.obtenerPorId(empresaId);
            if (empresa == null) {
                Toast.makeText(this, R.string.empresa_no_existe, Toast.LENGTH_LONG).show();
                finish();
                return;
            }
            nombre.setText(empresa.getNombre());
            url.setText(empresa.getUrl());
            telefono.setText(empresa.getTelefono());
            email.setText(empresa.getEmail());
            productos.setText(empresa.getProductosServicios());
            consultoria.setChecked(empresa.getClasificaciones().contains(Empresa.Clasificacion.CONSULTORIA));
            desarrollo.setChecked(empresa.getClasificaciones().contains(Empresa.Clasificacion.DESARROLLO_A_LA_MEDIDA));
            fabrica.setChecked(empresa.getClasificaciones().contains(Empresa.Clasificacion.FABRICA_DE_SOFTWARE));
        } catch (SQLiteException e) {
            cargaCorrecta = false;
            findViewById(R.id.guardar).setEnabled(false);
            mostrarError(R.string.error_cargar);
        }
    }

    private String texto(EditText campo) {
        return campo.getText().toString().trim();
    }

    private boolean validar() {
        error.setVisibility(View.GONE);
        mensajeError = 0;
        nombre.setError(null);
        url.setError(null);
        telefono.setError(null);
        email.setError(null);
        if (texto(nombre).isEmpty()) return errorCampo(nombre, R.string.nombre_obligatorio);
        if (!texto(url).isEmpty() && !Patterns.WEB_URL.matcher(texto(url)).matches())
            return errorCampo(url, R.string.url_invalida);
        if (!texto(email).isEmpty() && !Patterns.EMAIL_ADDRESS.matcher(texto(email)).matches())
            return errorCampo(email, R.string.email_invalido);
        String tel = texto(telefono);
        if (!tel.isEmpty() && (!tel.matches("[+0-9() .-]+")
                || tel.replaceAll("[^0-9]", "").length() < 7
                || tel.replaceAll("[^0-9]", "").length() > 15))
            return errorCampo(telefono, R.string.telefono_invalido);
        if (!consultoria.isChecked() && !desarrollo.isChecked() && !fabrica.isChecked()) {
            mostrarError(R.string.clasificacion_obligatoria);
            return false;
        }
        return true;
    }

    private boolean errorCampo(EditText campo, int mensaje) {
        campo.setError(getString(mensaje));
        campo.requestFocus();
        return false;
    }

    private void mostrarError(int mensaje) {
        mensajeError = mensaje;
        error.setText(mensaje);
        error.setVisibility(View.VISIBLE);
        // Esperar el layout para desplazar el mensaje visible dentro del ScrollView.
        error.post(() -> error.requestRectangleOnScreen(
                new Rect(0, 0, error.getWidth(), error.getHeight()), false));
    }

    private void guardar() {
        if (guardando || isFinishing() || !cargaCorrecta || !validar()) return;
        guardando = true;
        findViewById(R.id.guardar).setEnabled(false);
        EnumSet<Empresa.Clasificacion> seleccion = EnumSet.noneOf(Empresa.Clasificacion.class);
        if (consultoria.isChecked()) seleccion.add(Empresa.Clasificacion.CONSULTORIA);
        if (desarrollo.isChecked()) seleccion.add(Empresa.Clasificacion.DESARROLLO_A_LA_MEDIDA);
        if (fabrica.isChecked()) seleccion.add(Empresa.Clasificacion.FABRICA_DE_SOFTWARE);
        Empresa empresa = new Empresa(empresaId, texto(nombre), texto(url), texto(telefono),
                texto(email), texto(productos), seleccion);
        try {
            if (empresaId == 0) {
                db.insertar(empresa);
            } else if (!db.actualizar(empresa)) {
                mostrarError(R.string.empresa_no_existe);
                return;
            }
            Toast.makeText(this, R.string.empresa_guardada, Toast.LENGTH_SHORT).show();
            finish();
        } catch (SQLiteException e) {
            mostrarError(R.string.error_guardar);
        } finally {
            // Si la escritura falla, permitir corregir o reintentar sin perder el borrador.
            if (!isFinishing()) {
                guardando = false;
                findViewById(R.id.guardar).setEnabled(true);
            }
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putBoolean("carga_correcta", cargaCorrecta);
        outState.putInt("mensaje_error", mensajeError);
        super.onSaveInstanceState(outState);
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }
}
