package co.edu.unal.tictactoe_reto1;

import java.util.Random;

public class TicTacToeGame {

    // Jugadores
    public static final char HUMAN_PLAYER = 'X';
    public static final char COMPUTER_PLAYER = 'O';

    // Casilla vacía
    public static final char OPEN_SPOT = ' ';

    // Tamaño del tablero
    public static final int BOARD_SIZE = 9;

    // Niveles de dificultad
    public enum DifficultyLevel {
        Easy,
        Harder,
        Expert
    }

    // Dificultad actual
    private DifficultyLevel mDifficultyLevel = DifficultyLevel.Expert;

    // Tablero
    private char[] board;

    // Generador aleatorio
    private Random random;


    // Constructor
    public TicTacToeGame() {

        board = new char[BOARD_SIZE];

        random = new Random();

        clearBoard();
    }


    // Obtener dificultad actual
    public DifficultyLevel getDifficultyLevel() {

        return mDifficultyLevel;
    }


    // Cambiar dificultad
    public void setDifficultyLevel(DifficultyLevel difficultyLevel) {

        mDifficultyLevel = difficultyLevel;
    }


    // Limpiar tablero
    public void clearBoard() {

        for (int i = 0; i < BOARD_SIZE; i++) {

            board[i] = OPEN_SPOT;
        }
    }


    // Colocar una ficha
    public void setMove(char player, int location) {

        if (board[location] == OPEN_SPOT) {

            board[location] = player;
        }
    }


    // Obtener movimiento del computador según la dificultad
    public int getComputerMove() {

        int move = -1;

        if (mDifficultyLevel == DifficultyLevel.Easy) {

            move = getRandomMove();

        } else if (mDifficultyLevel == DifficultyLevel.Harder) {

            move = getWinningMove();

            if (move == -1) {

                move = getRandomMove();
            }

        } else if (mDifficultyLevel == DifficultyLevel.Expert) {

            // Primero intenta ganar
            move = getWinningMove();

            // Si no puede ganar, intenta bloquear
            if (move == -1) {

                move = getBlockingMove();
            }

            // Si tampoco puede bloquear, juega aleatoriamente
            if (move == -1) {

                move = getRandomMove();
            }
        }

        return move;
    }


    // Movimiento aleatorio
    private int getRandomMove() {

        int move;

        do {

            move = random.nextInt(BOARD_SIZE);

        } while (board[move] != OPEN_SPOT);

        return move;
    }


    // Buscar movimiento ganador
    private int getWinningMove() {

        for (int i = 0; i < BOARD_SIZE; i++) {

            if (board[i] == OPEN_SPOT) {

                board[i] = COMPUTER_PLAYER;

                if (checkForWinner() == 3) {

                    board[i] = OPEN_SPOT;

                    return i;
                }

                board[i] = OPEN_SPOT;
            }
        }

        return -1;
    }


    // Buscar movimiento para bloquear al jugador
    private int getBlockingMove() {

        for (int i = 0; i < BOARD_SIZE; i++) {

            if (board[i] == OPEN_SPOT) {

                board[i] = HUMAN_PLAYER;

                if (checkForWinner() == 2) {

                    board[i] = OPEN_SPOT;

                    return i;
                }

                board[i] = OPEN_SPOT;
            }
        }

        return -1;
    }


    // Comprobar ganador
    public int checkForWinner() {

        int[][] win = {

                {0, 1, 2},
                {3, 4, 5},
                {6, 7, 8},

                {0, 3, 6},
                {1, 4, 7},
                {2, 5, 8},

                {0, 4, 8},
                {2, 4, 6}
        };


        for (int i = 0; i < win.length; i++) {

            int a = win[i][0];
            int b = win[i][1];
            int c = win[i][2];


            if (board[a] != OPEN_SPOT &&
                    board[a] == board[b] &&
                    board[a] == board[c]) {

                if (board[a] == HUMAN_PLAYER) {

                    return 2;

                } else {

                    return 3;
                }
            }
        }


        // Comprobar si todavía hay casillas libres
        for (int i = 0; i < BOARD_SIZE; i++) {

            if (board[i] == OPEN_SPOT) {

                return 0;
            }
        }


        // Empate
        return 1;
    }
}