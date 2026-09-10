package isoforge.sim;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.utils.Array;
import isoforge.entity.Building;
import isoforge.entity.BuildingType;
import isoforge.entity.Job;
import isoforge.entity.JobBoard;
import isoforge.entity.Stockpile;
import isoforge.entity.Tree;
import isoforge.entity.Unit;
import isoforge.fx.Particles;
import isoforge.world.GridMap;
import isoforge.world.PathFinder;

/**
 * A colônia inteira: o mapa, quem vive nele, o que há para fazer e o relógio.
 *
 * <p>Esta classe é o lado da simulação da separação que sustenta o projeto.
 * <b>Nada aqui conhece um pixel</b> — nem câmera, nem contexto gráfico, nem
 * projeção isométrica. A prova não é retórica: os testes rodam o laço de
 * produção do machado ao telhado num processo sem tela.
 *
 * <p>Ela existe para que exista um segundo renderizador. Enquanto o desenho e
 * o estado moravam na mesma classe, trocar a forma de desenhar significava
 * mexer no jogo; agora significa escrever outra implementação que lê estes
 * mesmos getters. O que o renderizador precisa saber, ele pergunta; o que ele
 * quer que aconteça, ele pede por um dos comandos abaixo.
 *
 * <p><b>Pausa e velocidade não moram aqui.</b> Quem chama {@link #update} é que
 * decide qual {@code delta} entregar — zero congela tudo, incluindo o sol, e
 * quatro vezes o real acelera tudo junto. A simulação não precisa saber que a
 * palavra "pausa" existe.
 */
public final class World {

    /**
     * Intervalo entre passadas de atribuição de tarefas. A atribuição roda um
     * A* por tarefa aberta e por unidade ociosa, então rodá-la a cada frame
     * seria desperdício puro — o quadro não muda 60 vezes por segundo.
     */
    private static final float CLAIM_INTERVAL = 0.25f;

    /**
     * Cores dos efeitos. São dado de material — de que cor é uma lasca de
     * tronco, de que cor é a poeira de uma obra — e não decisão de desenho, por
     * isso ficam do lado da simulação junto de quem as dispara. Ver a nota
     * sobre coordenadas em {@link Particles}.
     */
    private static final Color WOOD_CHIP = new Color(0.45f, 0.32f, 0.20f, 1f);
    private static final Color DUST = new Color(0.86f, 0.82f, 0.70f, 1f);

    /**
     * Altura, em níveis, de onde saem as lascas de um tronco sendo cortado.
     *
     * <p>As velocidades das rajadas abaixo foram recalibradas quando as
     * partículas deixaram os pixels de tela: uma lasca a 1,4 tile/s se espalha
     * <i>no plano do chão</i> e aparece como ~50 px/s na projeção, contra os
     * 90 px/s que a versão anterior aplicava direto na tela. O número subiu
     * para a explosão manter a mesma energia — o movimento, esse ficou mais
     * certo, porque lasca salta no chão e não na diagonal da tela.
     */
    private static final float CHIP_HEIGHT = 0.6f;

    /** Por que uma construção não pode ser marcada num tile. */
    public enum BuildRefusal {
        NONE,
        OUT_OF_MAP,
        TERRAIN,
        OCCUPIED,
        DEPOT,
        TREE,
        SITE
    }

    private final GridMap map;
    private final PathFinder pathFinder;
    private final Stockpile stockpile;
    private final JobBoard jobBoard;
    private final Particles particles = new Particles();
    private final DayCycle dayCycle;

    private final Array<Unit> units = new Array<>();
    private final Array<Tree> trees = new Array<>();
    private final Array<Building> buildings = new Array<>();

    private final GridPoint2 depot = new GridPoint2();

    private int nextUnitId;
    private int nextTreeId;
    private int nextBuildingId;

    private float claimTimer;

    /** Relógio contínuo da simulação, para animações cíclicas como o tremor. */
    private float clock;

    public World(int depotX, int depotY) {
        this(depotX, depotY, new DayCycle());
    }

    public World(int depotX, int depotY, DayCycle dayCycle) {
        this.map = new GridMap(GridMap.DEFAULT_SIZE, GridMap.DEFAULT_SIZE);
        this.pathFinder = new PathFinder(map);
        this.stockpile = new Stockpile();
        this.depot.set(depotX, depotY);
        this.jobBoard = new JobBoard(depot, stockpile);
        this.dayCycle = dayCycle;
    }

    // ------------------------------------------------------------------
    // Povoamento
    // ------------------------------------------------------------------

    public Unit addUnit(String name, int gridX, int gridY) {
        Unit unit = new Unit(nextUnitId++, name, gridX, gridY, map);
        units.add(unit);
        return unit;
    }

    /** Planta uma árvore. Não publica tarefa: cortar é ordem do jogador. */
    public Tree addTree(int gridX, int gridY) {
        Tree tree = new Tree(nextTreeId++, gridX, gridY);
        trees.add(tree);
        return tree;
    }

    // ------------------------------------------------------------------
    // O passo
    // ------------------------------------------------------------------

    public void update(float delta) {
        clock += delta;
        dayCycle.update(delta);

        claimTimer += delta;
        if (claimTimer >= CLAIM_INTERVAL) {
            claimTimer = 0f;
            runClaimPass();
        }

        for (Unit unit : units) {
            unit.update(delta, map, pathFinder);
        }

        for (Tree tree : trees) {
            tree.update(delta);
            if (tree.pollJustChopped()) {
                particles.burst(tree.getX(), CHIP_HEIGHT, tree.getY(),
                        18, 2.2f, 5.5f, 0.8f, 0.05f, WOOD_CHIP);
            } else if (tree.isShaking() && MathUtils.randomBoolean(0.25f)) {
                // Uma lasca solta de vez em quando enquanto o machado bate:
                // barato, e é o que faz o corte parecer estar acontecendo em
                // vez de ser só um cronômetro escondido.
                particles.burst(tree.getX(), CHIP_HEIGHT, tree.getY(),
                        1, 1.4f, 3.4f, 0.5f, 0.04f, WOOD_CHIP);
            }
        }

        for (Building building : buildings) {
            if (building.pollJustCompleted()) {
                particles.burst(building.getX(), 0.3f, building.getY(),
                        24, 2.6f, 6.5f, 0.9f, 0.055f, DUST);
            }
        }

        jobBoard.purgeCompleted();
        purgeCancelledBuildings();
        particles.update(delta);
    }

    /** Cada tarefa aberta procura a unidade ociosa mais próxima por caminho. */
    public void runClaimPass() {
        jobBoard.assignPass(units, pathFinder, map);
    }

    // ------------------------------------------------------------------
    // Comandos
    // ------------------------------------------------------------------

    /**
     * Publica a tarefa cabível no tile: cortar, se houver árvore de pé; andar,
     * caso contrário. Quem decide o tipo é o que está no tile, e não o jogador
     * — o gesto dele é sempre o mesmo.
     *
     * @return a tarefa publicada, ou null se o tile não aceita nenhuma
     */
    public Job postJobAt(int gridX, int gridY) {
        Tree tree = standingTreeAt(gridX, gridY);
        return tree != null ? jobBoard.postChop(tree) : jobBoard.postMove(gridX, gridY, map);
    }

    /**
     * Diz se dá para marcar uma construção no tile, e por que não quando não dá.
     * O renderizador usa isto para colorir o fantasma sob o cursor, e a
     * interface para explicar a recusa — nenhum dos dois precisa repetir as
     * regras.
     */
    public BuildRefusal checkBuild(int gridX, int gridY) {
        if (!map.contains(gridX, gridY)) {
            return BuildRefusal.OUT_OF_MAP;
        }
        if (!map.get(gridX, gridY).isWalkable()) {
            return BuildRefusal.TERRAIN;
        }
        if (map.isBlocked(gridX, gridY)) {
            return BuildRefusal.OCCUPIED;
        }
        if (gridX == depot.x && gridY == depot.y) {
            return BuildRefusal.DEPOT;
        }
        if (standingTreeAt(gridX, gridY) != null) {
            return BuildRefusal.TREE;
        }
        if (siteAt(gridX, gridY) != null) {
            return BuildRefusal.SITE;
        }
        return BuildRefusal.NONE;
    }

    /**
     * Marca um canteiro e publica a obra. <b>Não</b> exige que a madeira exista
     * agora: o canteiro é a memória visual da ordem, e a tarefa espera no quadro
     * até a colônia ter material.
     *
     * @return a construção marcada, ou null se o tile a recusa
     */
    public Building placeBuilding(int gridX, int gridY, BuildingType type) {
        if (checkBuild(gridX, gridY) != BuildRefusal.NONE) {
            return null;
        }
        Building building = new Building(nextBuildingId++, gridX, gridY, type);
        buildings.add(building);
        jobBoard.postBuild(building);
        return building;
    }

    /** Cancela a tarefa publicada naquele tile — e só ela. */
    public boolean cancelAt(int gridX, int gridY) {
        Job job = jobBoard.findByTarget(gridX, gridY);
        if (job == null) {
            return false;
        }
        releaseJob(job);
        return true;
    }

    public void cancelEverything() {
        Array<Job> open = jobBoard.getJobs();
        for (int i = open.size - 1; i >= 0; i--) {
            releaseJob(open.get(i));
        }
        for (Unit unit : units) {
            unit.stop();
        }
        purgeCancelledBuildings();
    }

    /** Solta a unidade que estivesse na tarefa e desfaz as reservas dela. */
    private void releaseJob(Job job) {
        for (Unit unit : units) {
            if (unit.getCurrentJob() == job) {
                unit.stop();
            }
        }
        jobBoard.cancel(job);
        purgeCancelledBuildings();
    }

    private void purgeCancelledBuildings() {
        for (int i = buildings.size - 1; i >= 0; i--) {
            if (buildings.get(i).isCancelled()) {
                buildings.removeIndex(i);
            }
        }
    }

    // ------------------------------------------------------------------
    // Consultas
    // ------------------------------------------------------------------

    public Tree standingTreeAt(int gridX, int gridY) {
        for (Tree tree : trees) {
            if (tree.isStanding() && tree.getX() == gridX && tree.getY() == gridY) {
                return tree;
            }
        }
        return null;
    }

    public Building siteAt(int gridX, int gridY) {
        for (Building building : buildings) {
            if (building.getX() == gridX && building.getY() == gridY && !building.isCancelled()) {
                return building;
            }
        }
        return null;
    }

    public int getIdleUnitCount() {
        int idle = 0;
        for (Unit unit : units) {
            if (unit.isIdle()) {
                idle++;
            }
        }
        return idle;
    }

    public GridMap getMap() {
        return map;
    }

    public PathFinder getPathFinder() {
        return pathFinder;
    }

    public Stockpile getStockpile() {
        return stockpile;
    }

    public JobBoard getJobBoard() {
        return jobBoard;
    }

    public Particles getParticles() {
        return particles;
    }

    public DayCycle getDayCycle() {
        return dayCycle;
    }

    public Array<Unit> getUnits() {
        return units;
    }

    public Array<Tree> getTrees() {
        return trees;
    }

    public Array<Building> getBuildings() {
        return buildings;
    }

    /** Onde a lenha é entregue e de onde o material de obra sai. */
    public GridPoint2 getDepot() {
        return depot;
    }

    /** Segundos simulados desde o início. Para animações cíclicas do desenho. */
    public float getClock() {
        return clock;
    }
}
