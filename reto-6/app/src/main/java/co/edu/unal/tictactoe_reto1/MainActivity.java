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

public class MainActivity extends AppCompatActivity {

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

        // Obtener referencias del XML
        mBoardView = findViewById(R.id.board);
        mInfoTextView = findViewById(R.id.information);
        mScoresTextView = findViewById(R.id.scores);

        // Las puntuaciones persistentes se recuperan independientemente del Bundle.
        mScorePreferences = getSharedPreferences(SCORE_PREFERENCES, MODE_PRIVATE);
        mHumanWins = mScorePreferences.getInt(SCORE_HUMAN_WINS, 0);
        mComputerWins = mScorePreferences.getInt(SCORE_COMPUTER_WINS, 0);
        mTies = mScorePreferences.getInt(SCORE_TIES, 0);
        updateScores();

        Button newGameButton = findViewById(R.id.new_game);

        // Crear el juego
        mGame = new TicTacToeGame();

        mHumanMoveSound = MediaPlayer.create(
                this,
                R.raw.human_move
        );

        mComputerMoveSound = MediaPlayer.create(
                this,
                R.raw.computer_move
        );

        // Conectar BoardView con TicTacToeGame
        mBoardView.setGame(mGame);

        mBoardView.setOnTouchListener(new View.OnTouchListener() {

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {

                if (event.getAction() == android.view.MotionEvent.ACTION_UP) {

                    v.performClick();

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
        newGameButton.setOnClickListener(
                v -> startNewGame()
        );

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
        saveScores();
        super.onStop();
    }


    // =========================
    // MENÚ
    // =========================

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {

        MenuInflater inflater = getMenuInflater();

        inflater.inflate(R.menu.options_menu, menu);

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
