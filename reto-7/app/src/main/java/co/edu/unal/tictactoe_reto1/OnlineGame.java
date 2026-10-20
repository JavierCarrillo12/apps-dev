package co.edu.unal.tictactoe_reto1;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Datos de games/{gameId}. El identificador pertenece a la clave, no al contenido. */
public class OnlineGame {
    public static final String EMPTY = "_";
    public static final String X = "X";
    public static final String O = "O";
    public static final String WAITING = "WAITING";
    public static final String PLAYING = "PLAYING";
    public static final String FINISHED = "FINISHED";
    public static final String CANCELLED = "CANCELLED";
    public static final String NONE = "NONE";
    public static final String DRAW = "DRAW";

    private String playerX;
    private String playerO;
    private List<String> board = new ArrayList<>(Collections.nCopies(9, EMPTY));
    private String turn = X;
    private String status = WAITING;
    private String result = NONE;
    private long moveNumber;
    private long createdAt;

    // Constructor público sin argumentos para la futura deserialización.
    public OnlineGame() { }

    // createdAt deberá recibirse del servidor al integrar la persistencia.
    public OnlineGame(String playerX, long createdAt) {
        if (playerX == null || playerX.trim().isEmpty()) {
            throw new IllegalArgumentException("El creador debe tener un UID.");
        }
        this.playerX = playerX;
        this.createdAt = createdAt;
    }

    public String getPlayerX() { return playerX; }
    public void setPlayerX(String playerX) { this.playerX = playerX; }
    public String getPlayerO() { return playerO; }
    public void setPlayerO(String playerO) { this.playerO = playerO; }
    public List<String> getBoard() { return board == null ? null : new ArrayList<>(board); }
    public void setBoard(List<String> board) {
        this.board = board == null ? null : new ArrayList<>(board);
    }
    public String getTurn() { return turn; }
    public void setTurn(String turn) { this.turn = turn; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getResult() { return result; }
    public void setResult(String result) { this.result = result; }
    public long getMoveNumber() { return moveNumber; }
    public void setMoveNumber(long moveNumber) { this.moveNumber = moveNumber; }
    public long getCreatedAt() { return createdAt; }
    public void setCreatedAt(long createdAt) { this.createdAt = createdAt; }
}
