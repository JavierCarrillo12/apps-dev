package co.edu.unal.tictactoe_reto1;

import java.util.ArrayList;
import java.util.List;

/** Conversión de datos únicamente: no ejecuta movimientos ni accede a la red. */
public final class OnlineBoardConverter {
    private OnlineBoardConverter() { }

    public static char[] toLocalBoard(List<String> cells) {
        if (cells == null || cells.size() != TicTacToeGame.BOARD_SIZE) {
            throw new IllegalArgumentException("El tablero debe tener nueve casillas.");
        }
        char[] board = new char[TicTacToeGame.BOARD_SIZE];
        for (int i = 0; i < board.length; i++) {
            String cell = cells.get(i);
            if (OnlineGame.EMPTY.equals(cell)) {
                board[i] = TicTacToeGame.OPEN_SPOT;
            } else if (OnlineGame.X.equals(cell)) {
                board[i] = TicTacToeGame.HUMAN_PLAYER;
            } else if (OnlineGame.O.equals(cell)) {
                board[i] = TicTacToeGame.COMPUTER_PLAYER;
            } else {
                throw new IllegalArgumentException("Símbolo inválido en la casilla " + i);
            }
        }
        return board;
    }

    public static List<String> fromLocalBoard(char[] board) {
        if (board == null || board.length != TicTacToeGame.BOARD_SIZE) {
            throw new IllegalArgumentException("El tablero debe tener nueve casillas.");
        }
        List<String> cells = new ArrayList<>(board.length);
        for (char cell : board) {
            if (cell == TicTacToeGame.OPEN_SPOT) {
                cells.add(OnlineGame.EMPTY);
            } else if (cell == TicTacToeGame.HUMAN_PLAYER) {
                cells.add(OnlineGame.X);
            } else if (cell == TicTacToeGame.COMPUTER_PLAYER) {
                cells.add(OnlineGame.O);
            } else {
                throw new IllegalArgumentException("Símbolo inválido en el tablero local.");
            }
        }
        return cells;
    }
}
