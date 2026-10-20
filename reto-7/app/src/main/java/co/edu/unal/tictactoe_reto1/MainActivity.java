package co.edu.unal.tictactoe_reto1;

import androidx.appcompat.app.AppCompatActivity;

import android.os.Bundle;
import android.content.SharedPreferences;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.content.DialogInterface;
import android.app.AlertDialog;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;
import android.media.MediaPlayer;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    public static final String EXTRA_GAME_ID = "gameId";
    private boolean mOnline;
    private String mGameId;
    private String mUid;
    private String mOnlineSymbol;
    private OnlineGame mOnlineGame;
    private OnlineGameRepository mRepository;
    private OnlineGameRepository.Subscription mGameSubscription;
    private Task<Void> mPendingMove;
    private Task<Void> mPendingRestart;
    private Button mRestartButton;
    private boolean restartPending;
    private boolean movePending;
    private boolean mActive;
    private int mObserverVersion;

    private static final String STATE_BOARD = "board_state";
    private static final String STATE_GAME_OVER = "game_over";
    private static final String STATE_INFORMATION = "information_text";
    private static final String SCORE_PREFERENCES = "scores";
    private static final String SCORE_HUMAN_WINS = "human_wins";
    private static final String SCORE_COMPUTER_WINS = "computer_wins";
    private static final String SCORE_TIES = "ties";

    private SharedPreferences mScorePreferences;
    private TextView mScoresTextView;
    private int mHumanWins;
    private int mComputerWins;
    private int mTies;

    // Tablero gráfico
    private BoardView mBoardView;

    // Texto inferior
    private TextView mInfoTextView;

    // Lógica del juego
    private TicTacToeGame mGame;

    // Indica si la partida terminó
    private boolean mGameOver;

    private MediaPlayer mHumanMoveSound;
    private MediaPlayer mComputerMoveSound;


    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_main);
        mOnline = getIntent().hasExtra(EXTRA_GAME_ID);
        mGameId = getIntent().getStringExtra(EXTRA_GAME_ID);
        if (mOnline && getLastCustomNonConfigurationInstance() instanceof PendingOnlineRequests) {
            PendingOnlineRequests pending = (PendingOnlineRequests) getLastCustomNonConfigurationInstance();
            mPendingMove = pending.move;
            mPendingRestart = pending.restart;
            movePending = mPendingMove != null;
            restartPending = mPendingRestart != null;
        }

        // Obtener referencias del XML
        mBoardView = findViewById(R.id.board);
        mInfoTextView = findViewById(R.id.information);
        mScoresTextView = findViewById(R.id.scores);
        mRestartButton = findViewById(R.id.restart_online);

        // Las puntuaciones persistentes se recuperan independientemente del Bundle.
        mScorePreferences = getSharedPreferences(SCORE_PREFERENCES, MODE_PRIVATE);
        mHumanWins = mScorePreferences.getInt(SCORE_HUMAN_WINS, 0);
        mComputerWins = mScorePreferences.getInt(SCORE_COMPUTER_WINS, 0);
        mTies = mScorePreferences.getInt(SCORE_TIES, 0);
        updateScores();

        Button newGameButton = findViewById(R.id.new_game);

        // Crear el juego
        mGame = new TicTacToeGame();

        if (!mOnline) {
            mHumanMoveSound = MediaPlayer.create(
                    this,
                    R.raw.human_move
            );

            mComputerMoveSound = MediaPlayer.create(
                    this,
                    R.raw.computer_move
            );
        }

        // Conectar BoardView con TicTacToeGame
        mBoardView.setGame(mGame);

        mBoardView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {

                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {

                    v.performClick();

                    if (mOnline) {
                        handleOnlineTouch(event.getX(), event.getY());
                        return true;
                    }

                    // Si la partida ya terminó, no hacemos nada
                    if (mGameOver) {
                        return true;
                    }

                    // Coordenadas del toque dentro del BoardView
                    float x = event.getX();
                    float y = event.getY();

                    // Tamaño de cada casilla
                    int cellWidth = mBoardView.getBoardCellWidth();
                    int cellHeight = mBoardView.getBoardCellHeight();

                    // Convertir coordenadas a columna y fila
                    int col = (int) x / cellWidth;
                    int row = (int) y / cellHeight;

                    // Convertir fila y columna a posición 0-8
                    int pos = row * 3 + col;

                    // Verificar que la casilla esté vacía
                    if (mGame.getBoardOccupant(pos)
                            == TicTacToeGame.OPEN_SPOT) {

                        // Movimiento del jugador
                        setMove(
                                TicTacToeGame.HUMAN_PLAYER,
                                pos
                        );

                        // Comprobar si ganó el jugador
                        int winner = mGame.checkForWinner();

                        if (winner != 0) {

                            showWinner(winner);

                            return true;
                        }

                        // Turno del computador
                        mInfoTextView.setText("Turno de Android");

                        // Movimiento del computador
                        int computerMove = mGame.getComputerMove();

                        setMove(
                                TicTacToeGame.COMPUTER_PLAYER,
                                computerMove
                        );

                        // Comprobar si ganó el computador o hubo empate
                        winner = mGame.checkForWinner();

                        if (winner != 0) {

                            showWinner(winner);

                        } else {

                            mInfoTextView.setText("Tu turno");
                        }
                    }
                }

                return true;
            }
        });

        // Botón Nueva partida
        if (mOnline) {
            mScoresTextView.setVisibility(View.GONE);
            newGameButton.setText(R.string.online_back);
            newGameButton.setOnClickListener(v -> finish());
            mRestartButton.setOnClickListener(v -> restartOnlineGame());
            mRepository = new OnlineGameRepository(
                    "https://tictactoe-online-unal-15f23-default-rtdb.firebaseio.com");
            if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                mUid = FirebaseAuth.getInstance().getCurrentUser().getUid();
            }
            mInfoTextView.setText(R.string.online_loading);
            return;
        }
        newGameButton.setOnClickListener(v -> startNewGame());

        if (savedInstanceState == null) {
            // Iniciar partida únicamente cuando no hay estado guardado.
            startNewGame();
        } else {
            // Restaurar sin ejecutar movimientos ni procesar el resultado.
            mGame.setBoardState(savedInstanceState.getCharArray(STATE_BOARD));
            mGameOver = savedInstanceState.getBoolean(STATE_GAME_OVER);
            mInfoTextView.setText(savedInstanceState.getCharSequence(STATE_INFORMATION));
            mBoardView.invalidate();
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        mActive = true;
        if (!mOnline) return;
        mOnlineGame = null;
        mOnlineSymbol = null;
        updateOnlineInformation();
        final int version = ++mObserverVersion;
        if (mUid == null) {
            mInfoTextView.setText(R.string.online_not_player);
            return;
        }
        mGameSubscription = mRepository.observeGame(mGameId, new OnlineGameRepository.Listener<OnlineGame>() {
            @Override
            public void onData(OnlineGame game) {
                if (!mActive || version != mObserverVersion) return;
                try {
                    if (game == null) throw new IllegalStateException(getString(R.string.lobby_unavailable));
                    mGame.setBoardState(OnlineBoardConverter.toLocalBoard(game.getBoard()));
                    mOnlineGame = game;
                    mOnlineSymbol = mUid.equals(game.getPlayerX()) ? OnlineGame.X
                            : mUid.equals(game.getPlayerO()) ? OnlineGame.O : null;
                    mBoardView.invalidate();
                    updateOnlineInformation();
                } catch (RuntimeException error) {
                    onError(error);
                }
            }

            @Override
            public void onError(Exception error) {
                if (!mActive || version != mObserverVersion) return;
                mOnlineGame = null;
                mOnlineSymbol = null;
                mRestartButton.setVisibility(View.GONE);
                mInfoTextView.setText(R.string.online_read_error);
                Toast.makeText(MainActivity.this, getString(R.string.online_error,
                        error == null ? getString(R.string.lobby_unknown_error) : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
        if (mPendingMove != null) watchPendingMove();
        if (mPendingRestart != null) watchPendingRestart();
    }

    private void handleOnlineTouch(float x, float y) {
        if (movePending || restartPending || mOnlineGame == null || mOnlineSymbol == null
                || !OnlineGame.PLAYING.equals(mOnlineGame.getStatus())
                || !OnlineGame.NONE.equals(mOnlineGame.getResult())
                || !mOnlineSymbol.equals(mOnlineGame.getTurn())) return;
        int cellWidth = mBoardView.getBoardCellWidth();
        int cellHeight = mBoardView.getBoardCellHeight();
        if (cellWidth <= 0 || cellHeight <= 0 || x < 0 || y < 0
                || x >= cellWidth * 3 || y >= cellHeight * 3) return;
        int cellIndex = ((int) y / cellHeight) * 3 + (int) x / cellWidth;
        if (mGame.getBoardOccupant(cellIndex) != TicTacToeGame.OPEN_SPOT) return;
        movePending = true;
        mPendingMove = mRepository.playMove(mGameId, cellIndex, mOnlineGame.getMoveNumber());
        updateOnlineInformation();
        watchPendingMove();
    }

    private void watchPendingMove() {
        mPendingMove.addOnCompleteListener(this, task -> {
            if (!mActive || task != mPendingMove) return;
            movePending = false;
            mPendingMove = null;
            updateOnlineInformation();
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                Toast.makeText(this, getString(R.string.online_error,
                        error == null ? getString(R.string.lobby_unknown_error) : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void restartOnlineGame() {
        if (!mOnline || movePending || restartPending || mOnlineGame == null
                || mOnlineSymbol == null || !OnlineGame.FINISHED.equals(mOnlineGame.getStatus())) return;
        restartPending = true;
        mPendingRestart = mRepository.restartGame(mGameId);
        updateOnlineInformation();
        watchPendingRestart();
    }

    private void watchPendingRestart() {
        mPendingRestart.addOnCompleteListener(this, task -> {
            if (!mActive || task != mPendingRestart) return;
            restartPending = false;
            mPendingRestart = null;
            updateOnlineInformation();
            if (!task.isSuccessful()) {
                Exception error = task.getException();
                Toast.makeText(this, getString(R.string.online_error,
                        error == null ? getString(R.string.lobby_unknown_error) : error.getMessage()),
                        Toast.LENGTH_LONG).show();
            }
        });
    }

    private void updateOnlineInformation() {
        boolean canRestart = mOnlineGame != null && mOnlineSymbol != null
                && OnlineGame.FINISHED.equals(mOnlineGame.getStatus());
        mRestartButton.setVisibility(canRestart ? View.VISIBLE : View.GONE);
        mRestartButton.setEnabled(!movePending && !restartPending);
        if (mOnlineGame == null) {
            mInfoTextView.setText(R.string.online_loading);
        } else if (mOnlineSymbol == null) {
            mInfoTextView.setText(R.string.online_not_player);
        } else if (restartPending && !OnlineGame.FINISHED.equals(mOnlineGame.getStatus())) {
            mInfoTextView.setText(R.string.online_restarting);
        } else if (OnlineGame.FINISHED.equals(mOnlineGame.getStatus())) {
            if (OnlineGame.DRAW.equals(mOnlineGame.getResult())) {
                mInfoTextView.setText(R.string.online_draw);
            } else if (OnlineGame.X.equals(mOnlineGame.getResult())
                    || OnlineGame.O.equals(mOnlineGame.getResult())) {
                mInfoTextView.setText(getString(R.string.online_winner, mOnlineGame.getResult()));
            } else {
                mInfoTextView.setText(R.string.online_unavailable);
            }
        } else if (!OnlineGame.PLAYING.equals(mOnlineGame.getStatus())
                || !OnlineGame.NONE.equals(mOnlineGame.getResult())) {
            mInfoTextView.setText(R.string.online_unavailable);
        } else if (movePending) {
            mInfoTextView.setText(R.string.online_sending);
        } else if (mOnlineGame.getMoveNumber() >= TicTacToeGame.BOARD_SIZE) {
            mInfoTextView.setText(R.string.online_full);
        } else {
            mInfoTextView.setText(getString(R.string.online_turn, mOnlineSymbol,
                    getString(mOnlineSymbol.equals(mOnlineGame.getTurn())
                            ? R.string.online_your_turn : R.string.online_other_turn)));
        }
    }

    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        return new PendingOnlineRequests(mPendingMove, mPendingRestart);
    }

    private static final class PendingOnlineRequests {
        final Task<Void> move;
        final Task<Void> restart;

        PendingOnlineRequests(Task<Void> move, Task<Void> restart) {
            this.move = move;
            this.restart = restart;
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        outState.putCharArray(STATE_BOARD, mGame.getBoardState());
        outState.putBoolean(STATE_GAME_OVER, mGameOver);
        outState.putCharSequence(STATE_INFORMATION, mInfoTextView.getText());
        super.onSaveInstanceState(outState);
    }


    private void startNewGame() {

        // Reiniciar el tablero lógico
        mGame.clearBoard();

        // La partida vuelve a estar activa
        mGameOver = false;

        // Actualizar texto
        mInfoTextView.setText("Tu turno");

        // Pedir a BoardView que se vuelva a dibujar
        mBoardView.invalidate();
    }


    private void setMove(char player, int location) {

        mGame.setMove(player, location);

        mBoardView.invalidate();

        if (player == TicTacToeGame.HUMAN_PLAYER) {

            mHumanMoveSound.start();

        } else if (player == TicTacToeGame.COMPUTER_PLAYER) {

            mComputerMoveSound.start();
        }
    }


    private void showWinner(int winner) {

        // Cada partida finalizada se contabiliza una sola vez.
        if (mGameOver || winner < 1 || winner > 3) {
            return;
        }

        mGameOver = true;

        if (winner == 1) {

            mTies++;
            mInfoTextView.setText("Empate!");

        } else if (winner == 2) {

            mHumanWins++;
            mInfoTextView.setText("Ganaste!");

        } else if (winner == 3) {

            mComputerWins++;
            mInfoTextView.setText("Android ganó!");
        }
        updateScores();
        saveScores();
    }

    private void updateScores() {
        mScoresTextView.setText(getString(
                R.string.scores_summary, mHumanWins, mComputerWins, mTies));
    }

    private void saveScores() {
        mScorePreferences.edit()
                .putInt(SCORE_HUMAN_WINS, mHumanWins)
                .putInt(SCORE_COMPUTER_WINS, mComputerWins)
                .putInt(SCORE_TIES, mTies)
                .apply();
    }

    @Override
    protected void onStop() {
        mActive = false;
        mObserverVersion++;
        if (mGameSubscription != null) {
            mGameSubscription.remove();
            mGameSubscription = null;
        }
        if (!mOnline) saveScores();
        super.onStop();
    }


    // =========================
    // MENÚ
    // =========================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.options_menu, menu);
        if (mOnline) {
            menu.findItem(R.id.ai_difficulty).setVisible(false);
            menu.findItem(R.id.reset_scores).setVisible(false);
        }

        return true;
    }


    @Override
    public boolean onOptionsItemSelected(MenuItem item) {

        int id = item.getItemId();

        if (id == R.id.ai_difficulty) {

            showDifficultyDialog();

            return true;

        } else if (id == R.id.reset_scores) {

            resetScores();

            return true;
        } else if (id == R.id.quit) {
            showQuitDialog();
            return true;
        }

        return false;
    }


    // =========================
    // DIFICULTAD
    // =========================

    private void showDifficultyDialog() {

        final String[] levels = {
                "Easy",
                "Harder",
                "Expert"
        };

        int selected = 2;

        TicTacToeGame.DifficultyLevel currentLevel =
                mGame.getDifficultyLevel();

        if (currentLevel ==
                TicTacToeGame.DifficultyLevel.Easy) {

            selected = 0;

        } else if (currentLevel ==
                TicTacToeGame.DifficultyLevel.Harder) {

            selected = 1;
        }


        AlertDialog.Builder builder =
                new AlertDialog.Builder(this);

        builder.setTitle("Choose difficulty");

        builder.setSingleChoiceItems(
                levels,
                selected,
                new DialogInterface.OnClickListener() {

                    @Override
                    public void onClick(
                            DialogInterface dialog,
                            int which) {

                        if (which == 0) {

                            mGame.setDifficultyLevel(
                                    TicTacToeGame.DifficultyLevel.Easy
                            );

                        } else if (which == 1) {

                            mGame.setDifficultyLevel(
                                    TicTacToeGame.DifficultyLevel.Harder
                            );

                        } else {

                            mGame.setDifficultyLevel(
                                    TicTacToeGame.DifficultyLevel.Expert
                            );
                        }


                        dialog.dismiss();


                        Toast.makeText(
                                MainActivity.this,
                                "Difficulty: " + levels[which],
                                Toast.LENGTH_SHORT
                        ).show();
                    }
                });


        builder.show();
    }


    // Reiniciar únicamente las puntuaciones, conservando la partida actual.
    private void resetScores() {
        mHumanWins = 0;
        mComputerWins = 0;
        mTies = 0;
        updateScores();
        saveScores();
    }

    private void showQuitDialog() {
        new AlertDialog.Builder(this)
                .setTitle("Salir")
                .setMessage("¿Seguro que quieres salir?")
                .setPositiveButton("Sí", (dialog, which) -> finish())
                .setNegativeButton("No", null)
                .show();
    }

    @Override
    protected void onDestroy() {

        super.onDestroy();

        if (mHumanMoveSound != null) {
            mHumanMoveSound.release();
            mHumanMoveSound = null;
        }

        if (mComputerMoveSound != null) {
            mComputerMoveSound.release();
            mComputerMoveSound = null;
        }
    }

}
