package co.edu.unal.tictactoe_reto1;

import org.junit.Test;
import java.util.Arrays;
import static org.junit.Assert.*;

/** Solo movimientos puros; no simula confirmaciones de Firebase. */
public class OnlineMoveTest {
    private OnlineGame playing() {
        OnlineGame game = new OnlineGame("creator", 123L);
        game.setPlayerO("guest");
        game.setStatus(OnlineGame.PLAYING);
        return game;
    }

    @Test
    public void xMoveChangesOnlyOneCellTurnAndCounter() {
        OnlineGame original = playing();
        OnlineGame next = OnlineGameRepository.prepareMove(original, "creator", 0, 0);
        assertEquals(Arrays.asList("X", "_", "_", "_", "_", "_", "_", "_", "_"), next.getBoard());
        assertEquals(OnlineGame.O, next.getTurn());
        assertEquals(1, next.getMoveNumber());
        assertEquals(original.getPlayerX(), next.getPlayerX());
        assertEquals(original.getPlayerO(), next.getPlayerO());
        assertEquals(original.getCreatedAt(), next.getCreatedAt());
        assertEquals(original.getStatus(), next.getStatus());
        assertEquals(original.getResult(), next.getResult());
        assertEquals(OnlineGame.EMPTY, original.getBoard().get(0));
        assertEquals(OnlineGame.X, original.getTurn());
        assertEquals(0, original.getMoveNumber());
    }

    @Test
    public void oMovePreservesXAndChangesTurnAndCounter() {
        OnlineGame first = OnlineGameRepository.prepareMove(playing(), "creator", 0, 0);
        OnlineGame next = OnlineGameRepository.prepareMove(first, "guest", 4, 1);
        assertEquals(Arrays.asList("X", "_", "_", "_", "O", "_", "_", "_", "_"), next.getBoard());
        assertEquals(OnlineGame.X, next.getTurn());
        assertEquals(2, next.getMoveNumber());
        assertEquals(OnlineGame.EMPTY, first.getBoard().get(4));
    }

    @Test
    public void rejectsOutOfTurnAndRepeatedX() {
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(playing(), "guest", 0, 0));
        OnlineGame afterX = OnlineGameRepository.prepareMove(playing(), "creator", 0, 0);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(afterX, "creator", 1, 1));
    }

    @Test
    public void rejectsOccupiedCell() {
        OnlineGame afterX = OnlineGameRepository.prepareMove(playing(), "creator", 0, 0);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(afterX, "guest", 0, 1));
    }

    @Test
    public void rejectsForeignPlayerAndWaitingGame() {
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(playing(), "stranger", 0, 0));
        OnlineGame waiting = playing();
        waiting.setStatus(OnlineGame.WAITING);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(waiting, "creator", 0, 0));
    }

    @Test
    public void rejectsInvalidIndex() {
        for (int index : new int[]{-1, 9}) {
            assertThrows(IllegalArgumentException.class,
                    () -> OnlineGameRepository.prepareMove(playing(), "creator", index, 0));
        }
    }

    @Test
    public void rejectsStaleAndOutOfRangeMoveNumber() {
        for (long number : new long[]{-1, 1, 9}) {
            assertThrows(IllegalStateException.class,
                    () -> OnlineGameRepository.prepareMove(playing(), "creator", 0, number));
        }
    }

    @Test
    public void delayedRequestCannotBecomeALaterTurn() {
        OnlineGame first = OnlineGameRepository.prepareMove(playing(), "creator", 0, 0);
        OnlineGame second = OnlineGameRepository.prepareMove(first, "guest", 4, 1);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(second, "creator", 1, 0));
    }

    @Test
    public void rejectsMissingPlayersAndInvalidBoards() {
        OnlineGame game = playing();
        game.setPlayerO(null);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(game, "creator", 0, 0));
        game.setPlayerO("guest");
        game.setBoard(Arrays.asList("_", "_"));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineGameRepository.prepareMove(game, "creator", 0, 0));
        game.setBoard(Arrays.asList("?", "_", "_", "_", "_", "_", "_", "_", "_"));
        assertThrows(IllegalArgumentException.class,
                () -> OnlineGameRepository.prepareMove(game, "creator", 0, 0));
    }

    @Test
    public void refusesUnavailableGameAndFinishedBoard() {
        OnlineGame game = playing();
        game.setResult(OnlineGame.X);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(game, "creator", 0, 0));
        game.setResult(OnlineGame.NONE);
        game.setMoveNumber(9);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(game, "creator", 0, 9));
    }

    @Test
    public void allNineMovesFinishAsDrawWithoutSwitchingLastTurn() {
        OnlineGame game = playing();
        int[] moves = {0, 1, 2, 4, 3, 5, 7, 6, 8};
        for (int i = 0; i < 9; i++) {
            game = OnlineGameRepository.prepareMove(game, i % 2 == 0 ? "creator" : "guest", moves[i], i);
            if (i < 8) {
                assertEquals(OnlineGame.PLAYING, game.getStatus());
                assertEquals(OnlineGame.NONE, game.getResult());
            }
        }
        assertEquals(9, game.getMoveNumber());
        assertEquals(OnlineGame.X, game.getTurn());
        assertEquals(OnlineGame.FINISHED, game.getStatus());
        assertEquals(OnlineGame.DRAW, game.getResult());
    }

    private OnlineGame sequence(int... moves) {
        OnlineGame game = playing();
        for (int i = 0; i < moves.length; i++) {
            game = OnlineGameRepository.prepareMove(game, i % 2 == 0 ? "creator" : "guest", moves[i], i);
        }
        return game;
    }

    @Test
    public void xWinsAtomicallyAndRetainsTurn() {
        OnlineGame before = sequence(0, 3, 1, 4);
        OnlineGame finished = OnlineGameRepository.prepareMove(before, "creator", 2, 4);
        assertEquals(OnlineGame.FINISHED, finished.getStatus());
        assertEquals(OnlineGame.X, finished.getResult());
        assertEquals(OnlineGame.X, finished.getTurn());
        assertEquals(5, finished.getMoveNumber());
        assertEquals(Arrays.asList("X", "X", "X", "O", "O", "_", "_", "_", "_"), finished.getBoard());
        assertEquals(OnlineGame.PLAYING, before.getStatus());
        assertEquals(OnlineGame.NONE, before.getResult());
        assertEquals(OnlineGame.EMPTY, before.getBoard().get(2));
        assertEquals(before.getPlayerX(), finished.getPlayerX());
        assertEquals(before.getPlayerO(), finished.getPlayerO());
        assertEquals(before.getCreatedAt(), finished.getCreatedAt());
    }

    @Test
    public void oWinsAndRetainsTurn() {
        OnlineGame finished = sequence(0, 3, 1, 4, 8, 5);
        assertEquals(OnlineGame.FINISHED, finished.getStatus());
        assertEquals(OnlineGame.O, finished.getResult());
        assertEquals(OnlineGame.O, finished.getTurn());
        assertEquals(6, finished.getMoveNumber());
    }

    @Test
    public void noPlayerCanMoveAfterFinished() {
        OnlineGame finished = sequence(0, 3, 1, 4, 2);
        for (String uid : Arrays.asList("creator", "guest")) {
            assertThrows(IllegalStateException.class,
                    () -> OnlineGameRepository.prepareMove(finished, uid, 6, 5));
        }
    }

    @Test
    public void winOnNinthMoveHasPriorityOverDraw() {
        OnlineGame game = playing();
        game.setBoard(Arrays.asList("X", "O", "X", "O", "X", "O", "O", "X", "_"));
        game.setMoveNumber(8);
        OnlineGame finished = OnlineGameRepository.prepareMove(game, "creator", 8, 8);
        assertEquals(OnlineGame.FINISHED, finished.getStatus());
        assertEquals(OnlineGame.X, finished.getResult());
        assertEquals(OnlineGame.X, finished.getTurn());
        assertEquals(9, finished.getMoveNumber());
    }

    @Test
    public void everyWinningLineIsRecognized() {
        int[][] lines = {{0,1,2}, {3,4,5}, {6,7,8}, {0,3,6}, {1,4,7}, {2,5,8}, {0,4,8}, {2,4,6}};
        for (int[] line : lines) {
            OnlineGame game = playing();
            java.util.List<String> board = game.getBoard();
            board.set(line[0], OnlineGame.X);
            board.set(line[1], OnlineGame.X);
            int remainingO = 2;
            for (int i = 0; i < 9 && remainingO > 0; i++) {
                if (i != line[2] && OnlineGame.EMPTY.equals(board.get(i))) {
                    board.set(i, OnlineGame.O);
                    remainingO--;
                }
            }
            game.setBoard(board);
            game.setMoveNumber(4);
            OnlineGame finished = OnlineGameRepository.prepareMove(game, "creator", line[2], 4);
            assertEquals(OnlineGame.X, finished.getResult());
            assertEquals(OnlineGame.FINISHED, finished.getStatus());
        }
    }

    @Test
    public void alreadyWonBoardCannotReceiveAnotherMoveEvenWithStaleStatus() {
        OnlineGame stale = sequence(0, 3, 1, 4, 2);
        stale.setStatus(OnlineGame.PLAYING);
        stale.setResult(OnlineGame.NONE);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareMove(stale, "creator", 6, 5));
    }
}
