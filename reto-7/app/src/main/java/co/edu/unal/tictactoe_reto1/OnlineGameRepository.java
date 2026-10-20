package co.edu.unal.tictactoe_reto1;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.TaskCompletionSource;
import com.google.android.gms.tasks.Tasks;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.MutableData;
import com.google.firebase.database.Query;
import com.google.firebase.database.ServerValue;
import com.google.firebase.database.Transaction;
import com.google.firebase.database.ValueEventListener;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

/** Acceso a games. No inicia autenticación, no controla UI y no ejecuta jugadas. */
public final class OnlineGameRepository {
    public interface Listener<T> {
        void onData(T value);
        void onError(Exception error);
    }

    /** La pantalla deberá cancelar la suscripción al dejar de observar. */
    public interface Subscription {
        void remove();
    }

    private final FirebaseAuth auth;
    private final DatabaseReference gamesRef;

    /** Usar la URL exacta mostrada en la consola de Realtime Database. */
    public OnlineGameRepository(String databaseUrl) {
        this(FirebaseAuth.getInstance(), FirebaseDatabase.getInstance(databaseUrl));
    }

    /** Permite suministrar una instancia configurada para Emulator Suite. */
    public OnlineGameRepository(FirebaseAuth auth, FirebaseDatabase database) {
        if (auth == null || database == null) {
            throw new IllegalArgumentException("FirebaseAuth y FirebaseDatabase son obligatorios.");
        }
        this.auth = auth;
        gamesRef = database.getReference("games");
    }

    /** El Task entrega el ID solo cuando Firebase confirma la escritura. */
    public Task<String> createGame() {
        try {
            String uid = requireUid();
            DatabaseReference gameRef = gamesRef.push();
            String gameId = gameRef.getKey();
            if (gameId == null) {
                return Tasks.forException(new IllegalStateException("No se pudo generar gameId."));
            }
            return gameRef.setValue(initialGameData(uid)).continueWith(task -> {
                if (!task.isSuccessful()) {
                    Exception error = task.getException();
                    throw error != null ? error : new IllegalStateException("No se pudo crear la partida.");
                }
                return gameId;
            });
        } catch (RuntimeException error) {
            return Tasks.forException(error);
        }
    }

    /** null significa que la partida no existe o fue eliminada. */
    public Subscription observeGame(String gameId, Listener<OnlineGame> listener) {
        final DatabaseReference ref;
        try {
            requireUid();
            requireGameId(gameId);
            ref = gamesRef.child(gameId);
        } catch (RuntimeException error) {
            listener.onError(error);
            return () -> { };
        }
        ValueEventListener firebaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                final OnlineGame game;
                try {
                    game = readGame(snapshot);
                } catch (RuntimeException error) {
                    listener.onError(error);
                    return;
                }
                listener.onData(game);
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.toException());
            }
        };
        ref.addValueEventListener(firebaseListener);
        return () -> ref.removeEventListener(firebaseListener);
    }

    /** Clave = gameId; valor = partida. Incluir .indexOn: ["status"] en reglas futuras. */
    public Subscription observeWaitingGames(Listener<Map<String, OnlineGame>> listener) {
        try {
            requireUid();
        } catch (RuntimeException error) {
            listener.onError(error);
            return () -> { };
        }
        Query query = gamesRef.orderByChild("status").equalTo(OnlineGame.WAITING);
        ValueEventListener firebaseListener = new ValueEventListener() {
            @Override
            public void onDataChange(@NonNull DataSnapshot snapshot) {
                Map<String, OnlineGame> games = new LinkedHashMap<>();
                try {
                    for (DataSnapshot child : snapshot.getChildren()) {
                        OnlineGame game = readGame(child);
                        if (game != null && child.getKey() != null) {
                            games.put(child.getKey(), game);
                        }
                    }
                } catch (RuntimeException error) {
                    listener.onError(error);
                    return;
                }
                listener.onData(Collections.unmodifiableMap(games));
            }

            @Override
            public void onCancelled(@NonNull DatabaseError error) {
                listener.onError(error.toException());
            }
        };
        query.addValueEventListener(firebaseListener);
        return () -> query.removeEventListener(firebaseListener);
    }

    /** Reserva O de forma atómica; no modifica tablero, resultado ni contador. */
    public Task<Void> joinGame(String gameId) {
        final String uid;
        final DatabaseReference ref;
        try {
            uid = requireUid();
            requireGameId(gameId);
            ref = gamesRef.child(gameId);
        } catch (RuntimeException error) {
            return Tasks.forException(error);
        }
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        // Comprobación previa de existencia; no garantiza la caché inicial de la transacción.
        // La reserva y TODAS sus condiciones siguen evaluándose dentro de la transacción.
        ref.get().addOnCompleteListener(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                completion.setException(error != null ? error
                        : new IllegalStateException("No se pudo leer la partida."));
                return;
            }
            if (!task.getResult().exists()) {
                completion.setException(new IllegalStateException("La partida no existe."));
                return;
            }
            runJoinTransaction(ref, uid, completion);
        });
        return completion.getTask();
    }

    private void runJoinTransaction(DatabaseReference ref, String uid,
                                    TaskCompletionSource<Void> completion) {
        ref.runTransaction(new Transaction.Handler() {
            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                try {
                    // El primer estado local puede estar vacío aunque la partida exista.
                    // Continuar sin modificarlo permite que el SDK contraste el estado remoto.
                    if (data.getValue() == null) {
                        return Transaction.success(data);
                    }
                    OnlineGame game = data.getValue(OnlineGame.class);
                    if (!canJoin(game, uid)) {
                        return Transaction.abort();
                    }
                    OnlineBoardConverter.toLocalBoard(game.getBoard());
                    data.child("playerO").setValue(uid);
                    data.child("status").setValue(OnlineGame.PLAYING);
                    data.child("turn").setValue(OnlineGame.X);
                    return Transaction.success(data);
                } catch (RuntimeException error) {
                    // Datos incompatibles: no escribir ni producir efectos durante reintentos.
                    return Transaction.abort();
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (error != null) {
                    completion.setException(error.toException());
                } else if (!committed) {
                    completion.setException(new IllegalStateException(
                            "No se pudo unir: partida inexistente, no disponible, propia o con datos inválidos."));
                } else {
                    try {
                        OnlineGame joinedGame = snapshot == null ? null : readGame(snapshot);
                        if (joinedGame == null
                                || !uid.equals(joinedGame.getPlayerO())
                                || !OnlineGame.PLAYING.equals(joinedGame.getStatus())
                                || !OnlineGame.X.equals(joinedGame.getTurn())
                                || !OnlineGame.NONE.equals(joinedGame.getResult())
                                || joinedGame.getMoveNumber() != 0) {
                            throw new IllegalStateException(
                                    "La transacción fue confirmada, pero el estado final no corresponde a la unión solicitada.");
                        }
                    } catch (RuntimeException exception) {
                        completion.setException(exception);
                        return;
                    }
                    completion.setResult(null);
                }
            }
        }, false);
    }

    private String requireUid() {
        FirebaseUser user = auth.getCurrentUser();
        if (user == null || user.getUid().isEmpty()) {
            throw new IllegalStateException("Se requiere autenticación antes de usar el repositorio.");
        }
        return user.getUid();
    }

    /** Una sola transacción; no usa el tablero de la interfaz como estado definitivo. */
    public Task<Void> playMove(String gameId, int cellIndex, long expectedMoveNumber) {
        final String uid;
        final DatabaseReference ref;
        try {
            uid = requireUid();
            requireGameId(gameId);
            if (cellIndex < 0 || cellIndex >= TicTacToeGame.BOARD_SIZE
                    || expectedMoveNumber < 0 || expectedMoveNumber >= TicTacToeGame.BOARD_SIZE) {
                throw new IllegalArgumentException("Índice o número de movimiento inválido.");
            }
            ref = gamesRef.child(gameId);
        } catch (RuntimeException error) {
            return Tasks.forException(error);
        }
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        ref.runTransaction(new Transaction.Handler() {
            private RuntimeException rejection;

            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                rejection = null;
                try {
                    // Igual que en joinGame: no confundir caché inicial vacía con inexistencia.
                    if (data.getValue() == null) {
                        return Transaction.success(data);
                    }
                    OnlineGame next = prepareMove(data.getValue(OnlineGame.class), uid,
                            cellIndex, expectedMoveNumber);
                    data.child("board").child(String.valueOf(cellIndex))
                            .setValue(next.getBoard().get(cellIndex));
                    data.child("turn").setValue(next.getTurn());
                    data.child("moveNumber").setValue(next.getMoveNumber());
                    data.child("status").setValue(next.getStatus());
                    data.child("result").setValue(next.getResult());
                    return Transaction.success(data);
                } catch (RuntimeException error) {
                    rejection = error;
                    return Transaction.abort();
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (error != null) {
                    completion.setException(error.toException());
                } else if (!committed) {
                    completion.setException(rejection != null ? rejection
                            : new IllegalStateException("No se pudo confirmar la jugada."));
                } else {
                    try {
                        OnlineGame game = snapshot == null ? null : readGame(snapshot);
                        String symbol = game == null ? null
                                : uid.equals(game.getPlayerX()) ? OnlineGame.X
                                : uid.equals(game.getPlayerO()) ? OnlineGame.O : null;
                        String result = game == null ? null : boardResult(game.getBoard());
                        boolean finished = result != null && !OnlineGame.NONE.equals(result);
                        if (symbol == null
                                || !(finished ? OnlineGame.FINISHED : OnlineGame.PLAYING).equals(game.getStatus())
                                || !result.equals(game.getResult())
                                || (finished && !OnlineGame.DRAW.equals(result) && !symbol.equals(result))
                                || game.getMoveNumber() != expectedMoveNumber + 1
                                || !symbol.equals(game.getBoard().get(cellIndex))
                                || !(finished ? symbol : opposite(symbol)).equals(game.getTurn())) {
                            throw new IllegalStateException("El estado confirmado no corresponde a la jugada.");
                        }
                        completion.setResult(null);
                    } catch (RuntimeException failure) {
                        completion.setException(failure);
                    }
                }
            }
        }, false);
        return completion.getTask();
    }

    /** Reinicia la misma partida terminada; Firebase decide atómicamente quién la reinicia. */
    public Task<Void> restartGame(String gameId) {
        final String uid;
        final DatabaseReference ref;
        try {
            uid = requireUid();
            requireGameId(gameId);
            ref = gamesRef.child(gameId);
        } catch (RuntimeException error) {
            return Tasks.forException(error);
        }
        TaskCompletionSource<Void> completion = new TaskCompletionSource<>();
        ref.runTransaction(new Transaction.Handler() {
            private RuntimeException rejection;
            private OnlineGame proposed;

            @NonNull
            @Override
            public Transaction.Result doTransaction(@NonNull MutableData data) {
                rejection = null;
                proposed = null;
                try {
                    // Conservar la continuación validada para el primer estado local vacío.
                    if (data.getValue() == null) return Transaction.success(data);
                    proposed = prepareRestart(data.getValue(OnlineGame.class), uid);
                    data.child("board").setValue(proposed.getBoard());
                    data.child("turn").setValue(proposed.getTurn());
                    data.child("status").setValue(proposed.getStatus());
                    data.child("result").setValue(proposed.getResult());
                    data.child("moveNumber").setValue(proposed.getMoveNumber());
                    return Transaction.success(data);
                } catch (RuntimeException error) {
                    rejection = error;
                    return Transaction.abort();
                }
            }

            @Override
            public void onComplete(@Nullable DatabaseError error, boolean committed,
                                   @Nullable DataSnapshot snapshot) {
                if (error != null) {
                    completion.setException(error.toException());
                } else if (!committed) {
                    completion.setException(rejection != null ? rejection
                            : new IllegalStateException("No se pudo confirmar el reinicio."));
                } else {
                    try {
                        OnlineGame game = snapshot == null ? null : readGame(snapshot);
                        if (game == null || proposed == null
                                || !proposed.getPlayerX().equals(game.getPlayerX())
                                || !proposed.getPlayerO().equals(game.getPlayerO())
                                || proposed.getCreatedAt() != game.getCreatedAt()
                                || !proposed.getBoard().equals(game.getBoard())
                                || !OnlineGame.X.equals(game.getTurn())
                                || !OnlineGame.PLAYING.equals(game.getStatus())
                                || !OnlineGame.NONE.equals(game.getResult())
                                || game.getMoveNumber() != 0) {
                            throw new IllegalStateException("El estado confirmado no corresponde al reinicio.");
                        }
                        completion.setResult(null);
                    } catch (RuntimeException failure) {
                        completion.setException(failure);
                    }
                }
            }
        }, false);
        return completion.getTask();
    }

    /** Lógica pura: conserva identidad y fecha, sin modificar el objeto recibido. */
    static OnlineGame prepareRestart(OnlineGame game, String uid) {
        if (game == null) throw new IllegalArgumentException("La partida no contiene datos.");
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("Se requiere un jugador autenticado.");
        }
        if (!OnlineGame.FINISHED.equals(game.getStatus())) {
            throw new IllegalStateException("La partida ya fue reiniciada o no está terminada.");
        }
        if (!OnlineGame.X.equals(game.getResult()) && !OnlineGame.O.equals(game.getResult())
                && !OnlineGame.DRAW.equals(game.getResult())) {
            throw new IllegalStateException("La partida no tiene un resultado válido.");
        }
        if (game.getPlayerX() == null || game.getPlayerX().trim().isEmpty()
                || game.getPlayerO() == null || game.getPlayerO().trim().isEmpty()
                || game.getPlayerX().equals(game.getPlayerO())) {
            throw new IllegalStateException("Se necesitan dos jugadores diferentes.");
        }
        if (!uid.equals(game.getPlayerX()) && !uid.equals(game.getPlayerO())) {
            throw new IllegalStateException("No perteneces a esta partida.");
        }
        OnlineBoardConverter.toLocalBoard(game.getBoard());
        OnlineGame next = new OnlineGame();
        next.setPlayerX(game.getPlayerX());
        next.setPlayerO(game.getPlayerO());
        next.setCreatedAt(game.getCreatedAt());
        next.setStatus(OnlineGame.PLAYING);
        next.setTurn(OnlineGame.X);
        next.setResult(OnlineGame.NONE);
        next.setMoveNumber(0);
        return next;
    }

    /** Lógica pura comprobable sin Firebase. Devuelve una copia; no modifica game. */
    static OnlineGame prepareMove(OnlineGame game, String uid, int cellIndex, long expectedMoveNumber) {
        if (game == null) throw new IllegalArgumentException("La partida no contiene datos.");
        if (uid == null || uid.trim().isEmpty()) {
            throw new IllegalArgumentException("Se requiere un jugador autenticado.");
        }
        if (!OnlineGame.PLAYING.equals(game.getStatus()) || !OnlineGame.NONE.equals(game.getResult())) {
            throw new IllegalStateException("La partida no está disponible para jugar.");
        }
        if (game.getPlayerX() == null || game.getPlayerX().trim().isEmpty()
                || game.getPlayerO() == null || game.getPlayerO().trim().isEmpty()
                || game.getPlayerX().equals(game.getPlayerO())) {
            throw new IllegalStateException("Se necesitan dos jugadores diferentes.");
        }
        String symbol = uid.equals(game.getPlayerX()) ? OnlineGame.X
                : uid.equals(game.getPlayerO()) ? OnlineGame.O : null;
        if (symbol == null) throw new IllegalStateException("No perteneces a esta partida.");
        if (!symbol.equals(game.getTurn())) throw new IllegalStateException("No es tu turno.");
        if (cellIndex < 0 || cellIndex >= TicTacToeGame.BOARD_SIZE) {
            throw new IllegalArgumentException("Casilla inválida.");
        }
        if (expectedMoveNumber < 0 || expectedMoveNumber >= TicTacToeGame.BOARD_SIZE
                || game.getMoveNumber() != expectedMoveNumber) {
            throw new IllegalStateException("La partida cambió. Espera el estado actualizado.");
        }
        java.util.List<String> board = game.getBoard();
        if (!OnlineGame.NONE.equals(boardResult(board))) {
            throw new IllegalStateException("El tablero ya tiene un resultado.");
        }
        if (!OnlineGame.EMPTY.equals(board.get(cellIndex))) {
            throw new IllegalStateException("La casilla ya está ocupada.");
        }
        board.set(cellIndex, symbol);
        OnlineGame next = new OnlineGame();
        next.setPlayerX(game.getPlayerX());
        next.setPlayerO(game.getPlayerO());
        next.setBoard(board);
        String result = boardResult(board);
        boolean finished = !OnlineGame.NONE.equals(result);
        next.setTurn(finished ? game.getTurn() : opposite(symbol));
        next.setStatus(finished ? OnlineGame.FINISHED : OnlineGame.PLAYING);
        next.setResult(result);
        next.setMoveNumber(game.getMoveNumber() + 1);
        next.setCreatedAt(game.getCreatedAt());
        return next;
    }

    private static String boardResult(java.util.List<String> board) {
        TicTacToeGame evaluation = new TicTacToeGame();
        evaluation.setBoardState(OnlineBoardConverter.toLocalBoard(board));
        switch (evaluation.checkForWinner()) {
            case 1: return OnlineGame.DRAW;
            case 2: return OnlineGame.X;
            case 3: return OnlineGame.O;
            default: return OnlineGame.NONE;
        }
    }

    private static String opposite(String symbol) {
        return OnlineGame.X.equals(symbol) ? OnlineGame.O : OnlineGame.X;
    }

    static void requireGameId(String gameId) {
        if (gameId == null || gameId.trim().isEmpty()) {
            throw new IllegalArgumentException("gameId es obligatorio.");
        }
        for (int i = 0; i < gameId.length(); i++) {
            char c = gameId.charAt(i);
            if (c <= 31 || c == 127 || ".#$[]/".indexOf(c) >= 0) {
                throw new IllegalArgumentException("gameId debe ser una sola clave válida.");
            }
        }
    }

    static boolean canJoin(OnlineGame game, String uid) {
        return game != null && uid != null && !uid.trim().isEmpty()
                && OnlineGame.WAITING.equals(game.getStatus())
                && (game.getPlayerO() == null || game.getPlayerO().isEmpty())
                && game.getPlayerX() != null && !game.getPlayerX().trim().isEmpty()
                && !uid.equals(game.getPlayerX());
    }

    // Mapa de escritura: permite usar el timestamp del servidor sin cambiar el long del modelo.
    static Map<String, Object> initialGameData(String uid) {
        OnlineGame game = new OnlineGame(uid, 0L);
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("playerX", game.getPlayerX());
        values.put("playerO", null); // Firebase omite campos nulos; al leer vuelven como null.
        values.put("board", game.getBoard());
        values.put("turn", game.getTurn());
        values.put("status", game.getStatus());
        values.put("result", game.getResult());
        values.put("moveNumber", game.getMoveNumber());
        values.put("createdAt", ServerValue.TIMESTAMP);
        return values;
    }

    private static OnlineGame readGame(DataSnapshot snapshot) {
        if (!snapshot.exists()) {
            return null;
        }
        OnlineGame game = snapshot.getValue(OnlineGame.class);
        if (game == null) {
            throw new IllegalArgumentException("La partida no contiene un objeto válido.");
        }
        OnlineBoardConverter.toLocalBoard(game.getBoard());
        return game;
    }
}
