package co.edu.unal.tictactoe_reto1;

import org.junit.Test;
import java.util.Arrays;
import java.util.Collections;
import static org.junit.Assert.*;

/** Lógica pura del reinicio; no requiere una conexión a Firebase. */
public class OnlineRestartTest {
    private OnlineGame finished(String result) {
        OnlineGame game = new OnlineGame("creator", 123L);
        game.setPlayerO("guest");
        game.setBoard(Arrays.asList("X", "X", "X", "O", "O", "_", "_", "_", "_"));
        game.setStatus(OnlineGame.FINISHED);
        game.setResult(result);
        game.setTurn(OnlineGame.O);
        game.setMoveNumber(5);
        return game;
    }

    @Test
    public void eitherPlayerCanResetEveryFinishedResultAndPreserveMetadata() {
        for (String result : new String[]{OnlineGame.X, OnlineGame.O, OnlineGame.DRAW}) {
            for (String uid : new String[]{"creator", "guest"}) {
                OnlineGame original = finished(result);
                OnlineGame next = OnlineGameRepository.prepareRestart(original, uid);
                assertEquals("creator", next.getPlayerX());
                assertEquals("guest", next.getPlayerO());
                assertEquals(123L, next.getCreatedAt());
                assertEquals(Collections.nCopies(9, OnlineGame.EMPTY), next.getBoard());
                assertEquals(OnlineGame.X, next.getTurn());
                assertEquals(OnlineGame.PLAYING, next.getStatus());
                assertEquals(OnlineGame.NONE, next.getResult());
                assertEquals(0, next.getMoveNumber());
                assertEquals(OnlineGame.FINISHED, original.getStatus());
                assertEquals(result, original.getResult());
                assertEquals(5, original.getMoveNumber());
                assertEquals(OnlineGame.O, original.getTurn());
                assertEquals(OnlineGame.X, original.getBoard().get(0));
            }
        }
    }

    @Test
    public void rejectsPlayingGame() {
        OnlineGame game = finished(OnlineGame.X);
        game.setStatus(OnlineGame.PLAYING);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(game, "creator"));
    }

    @Test
    public void rejectsWaitingGame() {
        OnlineGame game = finished(OnlineGame.X);
        game.setStatus(OnlineGame.WAITING);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(game, "creator"));
    }

    @Test
    public void rejectsForeignPlayer() {
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(finished(OnlineGame.X), "stranger"));
    }

    @Test
    public void rejectsMissingGameOrAuthentication() {
        assertThrows(IllegalArgumentException.class,
                () -> OnlineGameRepository.prepareRestart(null, "creator"));
        for (String uid : new String[]{null, "", " "}) {
            assertThrows(IllegalArgumentException.class,
                    () -> OnlineGameRepository.prepareRestart(finished(OnlineGame.X), uid));
        }
    }

    @Test
    public void rejectsInvalidResultOrPlayers() {
        OnlineGame game = finished(OnlineGame.NONE);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(game, "creator"));
        game.setResult(OnlineGame.X);
        game.setPlayerO(null);
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(game, "creator"));
        game.setPlayerO("creator");
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(game, "creator"));
    }

    @Test
    public void rejectsSecondRequestAgainstAlreadyRestartedStateAndAllowsNextMove() {
        OnlineGame next = OnlineGameRepository.prepareRestart(finished(OnlineGame.DRAW), "guest");
        assertThrows(IllegalStateException.class,
                () -> OnlineGameRepository.prepareRestart(next, "creator"));
        OnlineGame moved = OnlineGameRepository.prepareMove(next, "creator", 4, 0);
        assertEquals(OnlineGame.X, moved.getBoard().get(4));
        assertEquals(OnlineGame.O, moved.getTurn());
        assertEquals(1, moved.getMoveNumber());
    }
}
