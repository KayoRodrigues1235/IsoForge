package isoforge;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import isoforge.entity.Building;
import isoforge.entity.BuildingType;
import isoforge.entity.JobBoard;
import isoforge.entity.Stockpile;
import isoforge.entity.Tree;
import isoforge.entity.Unit;
import isoforge.sim.World;
import isoforge.world.GridMap;
import isoforge.world.PathFinder;

import java.util.function.BooleanSupplier;

/**
 * O laço do jogo rodando sem janela nenhuma.
 *
 * <p>Esta classe existe porque a simulação do IsoForge <b>não conhece um
 * pixel</b>: nada em {@link World}, {@code entity} ou {@code world} importa
 * OpenGL, contexto gráfico ou renderizador. Dá para plantar uma árvore, mandar
 * cortá-la e ver a lenha chegar ao depósito num processo sem tela. Se um dia um
 * teste daqui precisar abrir uma janela para passar, a separação vazou — e é
 * justamente isso que se quer descobrir cedo.
 *
 * <p>Ela já foi maior. Na fase 0 esta classe montava o mapa, o quadro, o
 * estoque e reimplementava o passo de simulação do jogo à mão, porque essas
 * coisas moravam dentro de {@code IsoForgeGame} e não havia como alcançá-las de
 * um teste. Depois que a fase 1 extraiu {@link World}, sobrou o que sempre
 * deveria ter sido: um relógio de passo fixo e alguns atalhos de leitura. O
 * encolhimento é o resultado que se queria — se o arreio tivesse continuado
 * grande, a extração não teria valido.
 */
final class ColonySimulation {

    /** Um frame a 60 Hz. Passo fixo: teste que depende de relógio real mente. */
    static final float FRAME = 1f / 60f;

    final World world;

    ColonySimulation(int depotX, int depotY) {
        world = new World(depotX, depotY);
    }

    // Atalhos de leitura, só para os testes não ficarem cheios de world.getX().

    GridMap map() {
        return world.getMap();
    }

    PathFinder finder() {
        return world.getPathFinder();
    }

    Stockpile stockpile() {
        return world.getStockpile();
    }

    JobBoard board() {
        return world.getJobBoard();
    }

    Array<Unit> units() {
        return world.getUnits();
    }

    Array<Building> buildings() {
        return world.getBuildings();
    }

    GridPoint2 depot() {
        return world.getDepot();
    }

    Unit addUnit(String name, int x, int y) {
        return world.addUnit(name, x, y);
    }

    Tree addTree(int x, int y) {
        return world.addTree(x, y);
    }

    Building markBuilding(int x, int y, BuildingType type) {
        return world.placeBuilding(x, y, type);
    }

    /**
     * Roda até a condição valer ou o teto de tempo estourar.
     *
     * <p>O teto não é decoração: sem ele, uma unidade que fique presa numa
     * tarefa transforma um teste que falha num teste que trava, e a diferença
     * entre os dois é uma tarde perdida.
     *
     * @return segundos simulados até a condição valer, ou o teto se não valeu
     */
    float runUntil(float limitSeconds, BooleanSupplier done) {
        float elapsed = 0f;
        while (elapsed < limitSeconds) {
            world.update(FRAME);
            elapsed += FRAME;
            if (done.getAsBoolean()) {
                return elapsed;
            }
        }
        return limitSeconds;
    }

    /** Roda um tempo fixo, sem condição de parada. */
    void run(float seconds) {
        runUntil(seconds, () -> false);
    }

    boolean everyoneIdle() {
        return world.getIdleUnitCount() == world.getUnits().size;
    }

    /** Ergue paredes instantâneas, para testar o que acontece quando o caminho some. */
    void wallOff(int... xyPairs) {
        for (int i = 0; i < xyPairs.length; i += 2) {
            world.getMap().setBlocked(xyPairs[i], xyPairs[i + 1], true);
        }
    }
}
