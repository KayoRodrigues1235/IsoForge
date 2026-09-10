package isoforge;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import isoforge.entity.Building;
import isoforge.entity.BuildingType;
import isoforge.entity.JobBoard;
import isoforge.entity.Stockpile;
import isoforge.entity.Tree;
import isoforge.entity.Unit;
import isoforge.world.GridMap;
import isoforge.world.PathFinder;

import java.util.function.BooleanSupplier;

/**
 * O laço do jogo rodando sem janela nenhuma.
 *
 * <p>Esta classe existe porque a simulação do IsoForge <b>não conhece um
 * pixel</b>: nada em {@code entity} ou {@code world} importa OpenGL, contexto
 * gráfico ou renderizador. Dá para plantar uma árvore, mandar cortá-la e ver a
 * lenha chegar ao depósito num processo sem tela. Se um dia um teste daqui
 * precisar abrir uma janela para passar, a separação vazou — e é justamente
 * isso que se quer descobrir cedo.
 *
 * <p>O corpo de {@link #tick} é deliberadamente o mesmo do passo de simulação
 * do jogo, na mesma ordem. Ele é, de propósito, um rascunho da classe
 * {@code World} que ainda vai ser extraída de {@code IsoForgeGame}: quando ela
 * existir, este arreio deve encolher para uma linha só.
 */
final class ColonySimulation {

    /** Um frame a 60 Hz. Passo fixo: teste que depende de relógio real mente. */
    static final float FRAME = 1f / 60f;

    final GridMap map;
    final PathFinder finder;
    final Stockpile stockpile;
    final JobBoard board;
    final GridPoint2 depot;

    final Array<Unit> units = new Array<>();
    final Array<Tree> trees = new Array<>();
    final Array<Building> buildings = new Array<>();

    private int nextTreeId;
    private int nextBuildingId;

    ColonySimulation(int depotX, int depotY) {
        map = new GridMap(GridMap.DEFAULT_SIZE, GridMap.DEFAULT_SIZE);
        finder = new PathFinder(map);
        stockpile = new Stockpile();
        depot = new GridPoint2(depotX, depotY);
        board = new JobBoard(depot, stockpile);
    }

    Unit addUnit(String name, int x, int y) {
        Unit unit = new Unit(units.size, name, x, y, map);
        units.add(unit);
        return unit;
    }

    /** Planta uma árvore. Não publica tarefa: quem manda cortar é o teste. */
    Tree addTree(int x, int y) {
        Tree tree = new Tree(nextTreeId++, x, y);
        trees.add(tree);
        return tree;
    }

    /** Marca um canteiro e publica a obra, como faz o clique em modo construção. */
    Building markBuilding(int x, int y, BuildingType type) {
        Building building = new Building(nextBuildingId++, x, y, type);
        buildings.add(building);
        board.postBuild(building);
        return building;
    }

    /** Um frame: distribui tarefas, move todo mundo, recolhe o que terminou. */
    void tick(float delta) {
        board.assignPass(units, finder, map);
        for (Unit unit : units) {
            unit.update(delta, map, finder);
        }
        for (Tree tree : trees) {
            tree.update(delta);
        }
        board.purgeCompleted();
        for (int i = buildings.size - 1; i >= 0; i--) {
            if (buildings.get(i).isCancelled()) {
                buildings.removeIndex(i);
            }
        }
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
            tick(FRAME);
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
        for (Unit unit : units) {
            if (!unit.isIdle()) {
                return false;
            }
        }
        return true;
    }

    /** Ergue paredes instantâneas, para testar o que acontece quando o caminho some. */
    void wallOff(int... xyPairs) {
        for (int i = 0; i < xyPairs.length; i += 2) {
            map.setBlocked(xyPairs[i], xyPairs[i + 1], true);
        }
    }
}
