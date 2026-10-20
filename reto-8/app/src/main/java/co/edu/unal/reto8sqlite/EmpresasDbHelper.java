package co.edu.unal.reto8sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

public class EmpresasDbHelper extends SQLiteOpenHelper {
    private static final String DATABASE_NAME = "empresas.db";
    private static final int DATABASE_VERSION = 1;
    private static final String TABLA = "empresas";
    private static final String[] COLUMNAS = {
            "id", "nombre", "url", "telefono", "email", "productos_servicios", "clasificacion"
    };

    public EmpresasDbHelper(Context context) {
        this(context, DATABASE_NAME);
    }

    // Permite usar una base independiente en las pruebas instrumentadas.
    EmpresasDbHelper(Context context, String databaseName) {
        super(context.getApplicationContext(), databaseName, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE empresas ("
                + "id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "nombre TEXT NOT NULL CHECK(length(trim(nombre)) > 0), "
                + "url TEXT NOT NULL DEFAULT '', "
                + "telefono TEXT NOT NULL DEFAULT '', "
                + "email TEXT NOT NULL DEFAULT '', "
                + "productos_servicios TEXT NOT NULL DEFAULT '', "
                + "clasificacion TEXT NOT NULL CHECK(length(clasificacion) > 0))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Al aumentar la versión se debe agregar la migración correspondiente.
        // Nunca eliminar la tabla: contiene los datos del usuario.
        throw new IllegalStateException("Migración no definida: " + oldVersion + " -> " + newVersion);
    }

    /** Devuelve el ID generado. Si falla la escritura, propaga la excepción SQLite. */
    public long insertar(Empresa empresa) {
        return getWritableDatabase().insertOrThrow(TABLA, null, valores(empresa));
    }

    /** Devuelve null si no existe una empresa con ese ID. */
    public Empresa obtenerPorId(long id) {
        try (Cursor cursor = getReadableDatabase().query(TABLA, COLUMNAS,
                "id = ?", new String[]{String.valueOf(id)}, null, null, null)) {
            return cursor.moveToFirst() ? leerEmpresa(cursor) : null;
        }
    }

    public List<Empresa> listar() {
        return listarFiltradas("", null);
    }

    /** null en clasificación representa Todas. El nombre se busca como texto literal. */
    public List<Empresa> listarFiltradas(String nombre, Empresa.Clasificacion clasificacion) {
        List<String> condiciones = new ArrayList<>();
        List<String> argumentos = new ArrayList<>();
        if (nombre != null && !nombre.trim().isEmpty()) {
            // instr evita que % y _ escritos por el usuario actúen como comodines.
            condiciones.add("instr(lower(nombre), lower(?)) > 0");
            argumentos.add(nombre.trim());
        }
        if (clasificacion != null) {
            // Las comas delimitan valores completos, incluso al inicio y al final.
            condiciones.add("instr(',' || clasificacion || ',', ',' || ? || ',') > 0");
            argumentos.add(clasificacion.name());
        }
        List<Empresa> empresas = new ArrayList<>();
        try (Cursor cursor = getReadableDatabase().query(TABLA, COLUMNAS,
                condiciones.isEmpty() ? null : TextUtils.join(" AND ", condiciones),
                argumentos.toArray(new String[0]), null, null, "nombre COLLATE NOCASE ASC, id ASC")) {
            while (cursor.moveToNext()) {
                empresas.add(leerEmpresa(cursor));
            }
        }
        return empresas;
    }

    /** Consulta mínima para distinguir una base vacía de filtros sin coincidencias. */
    public boolean hayEmpresas() {
        try (Cursor cursor = getReadableDatabase().query(TABLA, new String[]{"id"},
                null, null, null, null, null, "1")) {
            return cursor.moveToFirst();
        }
    }

    /** Devuelve true si se actualizó una fila; false si el ID no existe. */
    public boolean actualizar(Empresa empresa) {
        return getWritableDatabase().update(TABLA, valores(empresa),
                "id = ?", new String[]{String.valueOf(empresa.getId())}) == 1;
    }

    /** La interfaz deberá solicitar confirmación antes de llamar este método. */
    public boolean eliminar(long id) {
        return getWritableDatabase().delete(TABLA,
                "id = ?", new String[]{String.valueOf(id)}) == 1;
    }

    private ContentValues valores(Empresa empresa) {
        ContentValues values = new ContentValues();
        values.put("nombre", empresa.getNombre());
        values.put("url", empresa.getUrl());
        values.put("telefono", empresa.getTelefono());
        values.put("email", empresa.getEmail());
        values.put("productos_servicios", empresa.getProductosServicios());
        // Nombres estables de enum, separados por coma y en orden de declaración.
        // Las etiquetas visibles pueden cambiar sin alterar los datos guardados.
        List<String> clasificaciones = new ArrayList<>();
        for (Empresa.Clasificacion clasificacion : empresa.getClasificaciones()) {
            clasificaciones.add(clasificacion.name());
        }
        values.put("clasificacion", TextUtils.join(",", clasificaciones));
        return values;
    }

    private Empresa leerEmpresa(Cursor cursor) {
        Set<Empresa.Clasificacion> clasificaciones = EnumSet.noneOf(Empresa.Clasificacion.class);
        for (String valor : cursor.getString(cursor.getColumnIndexOrThrow("clasificacion")).split(",")) {
            clasificaciones.add(Empresa.Clasificacion.valueOf(valor));
        }
        return new Empresa(
                cursor.getLong(cursor.getColumnIndexOrThrow("id")),
                cursor.getString(cursor.getColumnIndexOrThrow("nombre")),
                cursor.getString(cursor.getColumnIndexOrThrow("url")),
                cursor.getString(cursor.getColumnIndexOrThrow("telefono")),
                cursor.getString(cursor.getColumnIndexOrThrow("email")),
                cursor.getString(cursor.getColumnIndexOrThrow("productos_servicios")),
                clasificaciones);
    }
}
