package co.edu.unal.tictactoe_reto1;

import com.google.firebase.database.ServerValue;
import org.junit.Test;
import java.util.Arrays;
import java.util.Map;
import static org.junit.Assert.*;

/** Solo lógica pura: estas pruebas no se conectan a Firebase. */
public class OnlineGameRepositoryTest {
    @Test
    public void initialPayloadUsesServerTimestampAndWaitingState() {
        Map<String, Object> data = OnlineGameRepository.initialGameData("creator");
        assertEquals("creator", data.get("playerX"));
        assertNull(data.get("playerO"));
        assertEquals(Arrays.asList("_", "_", "_", "_", "_", "_", "_", "_", "_"), data.get("board"));
        assertEquals(OnlineGame.X, data.get("turn"));
        assertEquals(OnlineGame.WAITING, data.get("status"));
        assertEquals(OnlineGame.NONE, data.get("result"));
        assertEquals(0L, data.get("moveNumber"));
        assertEquals(ServerValue.TIMESTAMP, data.get("createdAt"));
        assertFalse(data.containsKey("gameId"));
    }

    @Test
    public void onlyAnotherPlayerCanJoinAnAvailableGame() {
        OnlineGame game = new OnlineGame("creator", 0);
        assertTrue(OnlineGameRepository.canJoin(game, "guest"));
        assertFalse(OnlineGameRepository.canJoin(game, "creator"));
        assertFalse(OnlineGameRepository.canJoin(game, null));
        assertFalse(OnlineGameRepository.canJoin(game, " "));
        assertFalse(OnlineGameRepository.canJoin(null, "guest"));
        assertFalse(OnlineGameRepository.canJoin(new OnlineGame(), "guest"));
        game.setPlayerO("someone");
        assertFalse(OnlineGameRepository.canJoin(game, "guest"));
        game.setPlayerO(null);
        for (String status : Arrays.asList(OnlineGame.PLAYING, OnlineGame.FINISHED, OnlineGame.CANCELLED)) {
            game.setStatus(status);
            assertFalse(OnlineGameRepository.canJoin(game, "guest"));
        }
    }

    @Test
    public void rejectsInvalidIdsBeforeBuildingDatabasePaths() {
        for (String id : Arrays.asList(null, "", " ", "games/id", "a.b", "a#b", "a$b", "a[b", "a]b", "a\nb", "a\u007fb")) {
            try {
                OnlineGameRepository.requireGameId(id);
                fail("Se aceptó un ID inválido: " + id);
            } catch (IllegalArgumentException expected) {
                // Validación esperada.
            }
        }
        OnlineGameRepository.requireGameId("-Firebase_push-key123");
    }
}
