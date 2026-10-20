package co.edu.unal.reto8sqlite;

import android.app.Dialog;
import android.content.Intent;
import android.database.sqlite.SQLiteException;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.BaseAdapter;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;

import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {
    private static final String CONFIRMAR_ELIMINACION = "confirmar_eliminacion";
    private EmpresasDbHelper db;
    private ListView lista;
    private TextView estado;
    private View reintentar;
    private EditText buscarNombre;
    private Spinner filtroClasificacion;
    private boolean abriendoFormulario;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        ajustarInsets(this);
        db = new EmpresasDbHelper(this);
        lista = findViewById(R.id.empresas);
        estado = findViewById(R.id.estado);
        reintentar = findViewById(R.id.reintentar);
        buscarNombre = findViewById(R.id.buscar_nombre);
        filtroClasificacion = findViewById(R.id.filtro_clasificacion);
        List<String> opciones = new ArrayList<>();
        opciones.add(getString(R.string.todas));
        for (Empresa.Clasificacion c : Empresa.Clasificacion.values()) opciones.add(c.getEtiqueta());
        ArrayAdapter<String> opcionesAdapter = new ArrayAdapter<>(this,
                android.R.layout.simple_spinner_item, opciones);
        opcionesAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filtroClasificacion.setAdapter(opcionesAdapter);
        if (savedInstanceState != null) {
            buscarNombre.setText(savedInstanceState.getString("filtro_nombre", ""));
            filtroClasificacion.setSelection(savedInstanceState.getInt("filtro_clasificacion", 0));
        }
        buscarNombre.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) { }
            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
                cargarEmpresas();
            }
            @Override public void afterTextChanged(Editable s) { }
        });
        filtroClasificacion.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                cargarEmpresas();
            }
            @Override public void onNothingSelected(AdapterView<?> parent) { }
        });
        findViewById(R.id.limpiar_filtros).setOnClickListener(v -> {
            buscarNombre.setText("");
            filtroClasificacion.setSelection(0);
            cargarEmpresas();
        });
        findViewById(R.id.agregar).setOnClickListener(v -> abrirFormulario(0));
        reintentar.setOnClickListener(v -> cargarEmpresas());
    }

    static void ajustarInsets(AppCompatActivity activity) {
        EdgeToEdge.enable(activity);
        ViewCompat.setOnApplyWindowInsetsListener(activity.findViewById(R.id.main), (v, insets) -> {
            Insets barras = insets.getInsets(WindowInsetsCompat.Type.systemBars()
                    | WindowInsetsCompat.Type.ime());
            v.setPadding(barras.left, barras.top, barras.right, barras.bottom);
            return insets;
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        abriendoFormulario = false;
        cargarEmpresas();
    }

    private void cargarEmpresas() {
        try {
            int seleccion = filtroClasificacion.getSelectedItemPosition();
            Empresa.Clasificacion clasificacion = seleccion > 0
                    ? Empresa.Clasificacion.values()[seleccion - 1] : null;
            List<Empresa> empresas = db.listarFiltradas(buscarNombre.getText().toString(), clasificacion);
            lista.setAdapter(new EmpresasAdapter(empresas));
            lista.setVisibility(empresas.isEmpty() ? View.GONE : View.VISIBLE);
            estado.setText(empresas.isEmpty() && db.hayEmpresas()
                    ? R.string.sin_resultados : R.string.lista_vacia);
            estado.setVisibility(empresas.isEmpty() ? View.VISIBLE : View.GONE);
            reintentar.setVisibility(View.GONE);
        } catch (SQLiteException e) {
            lista.setVisibility(View.GONE);
            estado.setText(R.string.error_listado);
            estado.setVisibility(View.VISIBLE);
            reintentar.setVisibility(View.VISIBLE);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString("filtro_nombre", buscarNombre.getText().toString());
        outState.putInt("filtro_clasificacion", filtroClasificacion.getSelectedItemPosition());
        super.onSaveInstanceState(outState);
    }

    private void abrirFormulario(long id) {
        if (abriendoFormulario) return;
        abriendoFormulario = true;
        startActivity(new Intent(this, EmpresaFormActivity.class)
                .putExtra(EmpresaFormActivity.EXTRA_ID, id));
    }

    private void eliminarConfirmada(long id) {
        try {
            boolean eliminada = db.eliminar(id);
            Toast.makeText(this, eliminada ? R.string.empresa_eliminada : R.string.empresa_no_existe,
                    Toast.LENGTH_SHORT).show();
            cargarEmpresas();
        } catch (SQLiteException e) {
            Toast.makeText(this, R.string.error_eliminar, Toast.LENGTH_LONG).show();
        }
    }

    @Override
    protected void onDestroy() {
        db.close();
        super.onDestroy();
    }

    private class EmpresasAdapter extends BaseAdapter {
        // Representa la consulta actual. SQLite sigue siendo la fuente de verdad.
        private final List<Empresa> empresas;

        EmpresasAdapter(List<Empresa> empresas) { this.empresas = empresas; }
        @Override public int getCount() { return empresas.size(); }
        @Override public Empresa getItem(int position) { return empresas.get(position); }
        @Override public long getItemId(int position) { return getItem(position).getId(); }
        @Override public boolean hasStableIds() { return true; }

        @Override
        public View getView(int position, View view, ViewGroup parent) {
            if (view == null) view = getLayoutInflater().inflate(R.layout.item_empresa, parent, false);
            Empresa empresa = getItem(position);
            ((TextView) view.findViewById(R.id.nombre_empresa)).setText(empresa.getNombre());
            List<String> etiquetas = new ArrayList<>();
            for (Empresa.Clasificacion c : empresa.getClasificaciones()) etiquetas.add(c.getEtiqueta());
            ((TextView) view.findViewById(R.id.clasificaciones_empresa)).setText(TextUtils.join(" · ", etiquetas));
            TextView url = view.findViewById(R.id.url_empresa);
            url.setText(empresa.getUrl());
            url.setVisibility(empresa.getUrl().isEmpty() ? View.GONE : View.VISIBLE);
            view.findViewById(R.id.editar).setOnClickListener(v -> abrirFormulario(empresa.getId()));
            view.findViewById(R.id.editar).setContentDescription(
                    getString(R.string.editar_empresa_nombre, empresa.getNombre()));
            view.findViewById(R.id.eliminar).setContentDescription(
                    getString(R.string.eliminar_empresa, empresa.getNombre()));
            view.findViewById(R.id.eliminar).setOnClickListener(v -> {
                if (getSupportFragmentManager().findFragmentByTag(CONFIRMAR_ELIMINACION) != null) return;
                ConfirmarEliminacion dialog = new ConfirmarEliminacion();
                Bundle args = new Bundle();
                args.putLong("id", empresa.getId());
                args.putString("nombre", empresa.getNombre());
                dialog.setArguments(args);
                // Registro inmediato: un segundo toque encuentra el diálogo existente.
                dialog.showNow(getSupportFragmentManager(), CONFIRMAR_ELIMINACION);
            });
            return view;
        }
    }

    /** Conserva la confirmación y el ID al rotar. */
    public static class ConfirmarEliminacion extends DialogFragment {
        private boolean eliminando;

        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            Bundle args = requireArguments();
            return new AlertDialog.Builder(requireContext())
                    .setTitle(getString(R.string.eliminar_empresa, args.getString("nombre")))
                    .setMessage(R.string.confirmar_eliminacion)
                    .setNegativeButton(R.string.cancelar, null)
                    .setPositiveButton(R.string.eliminar, (dialog, which) -> {
                        if (eliminando) return;
                        eliminando = true;
                        ((MainActivity) requireActivity()).eliminarConfirmada(args.getLong("id"));
                    })
                    .create();
        }
    }
}
