package co.edu.unal.tictactoe_reto1;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.auth.FirebaseAuth;

import java.util.Map;

/** Lobby mínimo. Las operaciones de partidas pasan exclusivamente por el repositorio. */
public class LobbyActivity extends AppCompatActivity {
    private static final String TAG = "LobbyActivity";
    private static final String STATE_GAME_ID = "lobby_game_id";

    // Solo memoria durante recreaciones: evita repetir una creación/unión al rotar.
    private static final class LobbyState {
        String gameId;
        Task<AuthResult> authentication;
        Task<String> selection;
        int operationMessage;
        boolean gameOpened;
    }

    private LobbyState state;
    private FirebaseAuth auth;
    private OnlineGameRepository repository;
    private OnlineGameRepository.Subscription waitingSubscription;
    private OnlineGameRepository.Subscription gameSubscription;
    private boolean active;
    private int observerVersion;
    private TextView statusText;
    private TextView errorText;
    private TextView emptyText;
    private View progress;
    private View availableSection;
    private LinearLayout gamesContainer;
    private Button createButton;
    private Button retryButton;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_lobby);
        Object retained = getLastCustomNonConfigurationInstance();
        state = retained instanceof LobbyState ? (LobbyState) retained : new LobbyState();
        if (retained == null && savedInstanceState != null) {
            state.gameId = savedInstanceState.getString(STATE_GAME_ID);
            state.gameOpened = savedInstanceState.getBoolean("lobby_game_opened");
        }
        auth = FirebaseAuth.getInstance();
        repository = new OnlineGameRepository(
                "https://tictactoe-online-unal-15f23-default-rtdb.firebaseio.com");
        statusText = findViewById(R.id.lobby_status);
        errorText = findViewById(R.id.lobby_error);
        emptyText = findViewById(R.id.lobby_empty);
        progress = findViewById(R.id.lobby_progress);
        availableSection = findViewById(R.id.lobby_available_section);
        gamesContainer = findViewById(R.id.lobby_games);
        createButton = findViewById(R.id.lobby_create);
        retryButton = findViewById(R.id.lobby_retry);
        createButton.setOnClickListener(v -> {
            if (state.gameId != null && state.gameOpened) {
                openGame(state.gameId);
            } else {
                createGame();
            }
        });
        retryButton.setOnClickListener(v -> connect());
        findViewById(R.id.lobby_local).setOnClickListener(v ->
                startActivity(new Intent(this, MainActivity.class)));
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.lobby_root), (view, insets) -> {
            Insets bars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            view.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });
    }

    @Override
    protected void onStart() {
        super.onStart();
        active = true;
        connect();
    }

    private void connect() {
        removeObservers();
        clearError();
        setBusy(true, R.string.lobby_connecting);
        if (auth.getCurrentUser() != null) {
            onAuthenticated();
            return;
        }
        // Autenticación trasladada desde MainActivity; nunca se repite por operación.
        if (state.authentication == null || state.authentication.isComplete()) {
            state.authentication = auth.signInAnonymously();
        }
        state.authentication.addOnCompleteListener(this, task -> {
            if (!active) return;
            if (task.isSuccessful()) {
                onAuthenticated();
            } else {
                showError(task.getException());
            }
        });
    }

    private void onAuthenticated() {
        if (state.selection != null) {
            watchSelection();
        } else {
            observeCurrent();
        }
    }

    private void createGame() {
        if (!canSelect()) return;
        removeObservers();
        state.operationMessage = R.string.lobby_creating;
        state.selection = repository.createGame();
        watchSelection();
    }

    private void joinGame(String gameId) {
        if (!canSelect()) return;
        removeObservers();
        state.operationMessage = R.string.lobby_joining;
        String fallbackError = getString(R.string.lobby_unknown_error);
        state.selection = repository.joinGame(gameId).continueWith(task -> {
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                throw error != null ? error : new IllegalStateException(fallbackError);
            }
            return gameId;
        });
        watchSelection();
    }

    private boolean canSelect() {
        return active && auth.getCurrentUser() != null && state.gameId == null && state.selection == null;
    }

    private void watchSelection() {
        clearError();
        setBusy(true, state.operationMessage);
        state.selection.addOnCompleteListener(this, task -> {
            if (!active || state.selection != task) return;
            state.selection = null;
            if (task.isSuccessful()) {
                state.gameId = task.getResult();
                state.gameOpened = false;
                observeCurrent();
            } else {
                observeCurrent();
                showError(task.getException());
            }
        });
    }

    private void observeCurrent() {
        removeObservers();
        clearError();
        final int version = observerVersion;
        boolean hasGame = state.gameId != null;
        availableSection.setVisibility(hasGame ? View.GONE : View.VISIBLE);
        if (hasGame) {
            setBusy(true, R.string.lobby_observing);
            gameSubscription = repository.observeGame(state.gameId, new OnlineGameRepository.Listener<OnlineGame>() {
                @Override
                public void onData(OnlineGame game) {
                    if (!active || version != observerVersion) return;
                    progress.setVisibility(View.GONE);
                    if (game == null) {
                        state.gameId = null;
                        observeCurrent();
                        statusText.setText(R.string.lobby_unavailable);
                    } else if (OnlineGame.WAITING.equals(game.getStatus())) {
                        statusText.setText(getString(R.string.lobby_waiting, state.gameId));
                    } else if (OnlineGame.PLAYING.equals(game.getStatus())) {
                        onGameReady(state.gameId);
                    } else {
                        statusText.setText(R.string.lobby_unavailable);
                    }
                }

                @Override
                public void onError(Exception error) {
                    if (active && version == observerVersion) showError(error);
                }
            });
        } else {
            createButton.setText(R.string.lobby_create);
            gamesContainer.removeAllViews();
            emptyText.setVisibility(View.GONE);
            setBusy(true, R.string.lobby_loading);
            waitingSubscription = repository.observeWaitingGames(new OnlineGameRepository.Listener<Map<String, OnlineGame>>() {
                @Override
                public void onData(Map<String, OnlineGame> games) {
                    if (!active || version != observerVersion) return;
                    setBusy(false, R.string.lobby_choose);
                    renderGames(games);
                }

                @Override
                public void onError(Exception error) {
                    if (active && version == observerVersion) showError(error);
                }
            });
        }
    }

    private void renderGames(Map<String, OnlineGame> games) {
        gamesContainer.removeAllViews();
        String uid = auth.getCurrentUser().getUid();
        for (Map.Entry<String, OnlineGame> entry : games.entrySet()) {
            OnlineGame game = entry.getValue();
            if (uid.equals(game.getPlayerX()) || !OnlineGame.WAITING.equals(game.getStatus())
                    || (game.getPlayerO() != null && !game.getPlayerO().isEmpty())) continue;
            String gameId = entry.getKey();
            LinearLayout row = new LinearLayout(this);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = new TextView(this);
            label.setText(getString(R.string.lobby_room, gameId));
            row.addView(label, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1));
            Button joinButton = new Button(this);
            joinButton.setText(R.string.lobby_join);
            joinButton.setOnClickListener(v -> joinGame(gameId));
            row.addView(joinButton);
            gamesContainer.addView(row);
        }
        emptyText.setVisibility(gamesContainer.getChildCount() == 0 ? View.VISIBLE : View.GONE);
    }

    private void onGameReady(String gameId) {
        statusText.setText(getString(R.string.lobby_ready, gameId));
        removeObservers();
        createButton.setText(R.string.lobby_continue);
        createButton.setEnabled(true);
        if (!state.gameOpened) openGame(gameId);
    }

    private void openGame(String gameId) {
        state.gameOpened = true;
        startActivity(new Intent(this, MainActivity.class)
                .putExtra(MainActivity.EXTRA_GAME_ID, gameId));
    }

    private void setBusy(boolean busy, int message) {
        progress.setVisibility(busy ? View.VISIBLE : View.GONE);
        statusText.setText(message);
        createButton.setEnabled(!busy && canSelect());
        retryButton.setEnabled(!busy);
        for (int i = 0; i < gamesContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) gamesContainer.getChildAt(i);
            row.getChildAt(1).setEnabled(!busy && canSelect());
        }
    }

    private void clearError() {
        errorText.setVisibility(View.GONE);
        retryButton.setVisibility(View.GONE);
    }

    private void showError(Exception error) {
        Log.e(TAG, "Lobby operation failed", error);
        progress.setVisibility(View.GONE);
        statusText.setText(R.string.lobby_choose);
        String message = error == null ? getString(R.string.lobby_unknown_error) : error.getLocalizedMessage();
        errorText.setText(getString(R.string.lobby_error, message));
        errorText.setVisibility(View.VISIBLE);
        retryButton.setVisibility(View.VISIBLE);
        retryButton.setEnabled(state.selection == null);
        createButton.setEnabled(canSelect());
    }

    private void removeObservers() {
        observerVersion++;
        if (waitingSubscription != null) {
            waitingSubscription.remove();
            waitingSubscription = null;
        }
        if (gameSubscription != null) {
            gameSubscription.remove();
            gameSubscription = null;
        }
    }

    @Override
    protected void onStop() {
        active = false;
        removeObservers();
        super.onStop();
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return state;
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putString(STATE_GAME_ID, state.gameId);
        outState.putBoolean("lobby_game_opened", state.gameOpened);
        super.onSaveInstanceState(outState);
    }
}
