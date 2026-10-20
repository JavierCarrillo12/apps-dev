package co.edu.unal.tictactoe_reto1;

import org.junit.Test;
import java.util.List;
import static org.junit.Assert.*;

public class OnlineGameTest {
    @Test
    public void newGameWaitsForSecondPlayerWithEmptyBoard() {
        OnlineGame game = new OnlineGame("creator-uid", 123L);
        assertEquals("creator-uid", game.getPlayerX());
        assertNull(game.getPlayerO());
        assertEquals(OnlineGame.WAITING, game.getStatus());
        assertEquals(OnlineGame.X, game.getTurn());
        assertEquals(OnlineGame.NONE, game.getResult());
        assertEquals(0, game.getMoveNumber());
        assertEquals(123L, game.getCreatedAt());
        assertArrayEquals(new TicTacToeGame().getBoardState(),
                OnlineBoardConverter.toLocalBoard(game.getBoard()));
    }

    @Test
    public void conversionPreservesBoardAndExistingWinnerDetection() {
        char[][] boards = {
                {'X','X','X','O','O',' ',' ',' ',' '},
                {'X','X',' ','O','O','O','X',' ',' '},
                {'X','O','X','X','O','O','O','X','X'}
        };
        int[] results = {2, 3, 1};
        for (int i = 0; i < boards.length; i++) {
            char[] restored = OnlineBoardConverter.toLocalBoard(
                    OnlineBoardConverter.fromLocalBoard(boards[i]));
            assertArrayEquals(boards[i], restored);
            TicTacToeGame local = new TicTacToeGame();
            local.setBoardState(restored);
            assertEquals(results[i], local.checkForWinner());
        }
    }

    @Test
    public void boardCopiesDoNotExposeMutableState() {
        OnlineGame game = new OnlineGame();
        List<String> input = game.getBoard();
        game.setBoard(input);
        input.set(0, OnlineGame.X);
        List<String> output = game.getBoard();
        output.set(1, OnlineGame.O);
        assertEquals(OnlineGame.EMPTY, game.getBoard().get(0));
        assertEquals(OnlineGame.EMPTY, game.getBoard().get(1));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsMalformedRemoteBoard() {
        List<String> board = new OnlineGame().getBoard();
        board.set(3, null);
        OnlineBoardConverter.toLocalBoard(board);
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsWrongBoardSize() {
        OnlineBoardConverter.fromLocalBoard(new char[8]);
    }
}
