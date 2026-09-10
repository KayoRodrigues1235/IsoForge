package isoforge;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import isoforge.entity.Building;
import isoforge.entity.BuildingType;
import isoforge.entity.Job;
import isoforge.entity.JobBoard;
import isoforge.entity.Stockpile;
import isoforge.entity.Tree;
import isoforge.entity.Unit;
import isoforge.fx.Particles;
import isoforge.world.GridMap;
import isoforge.world.IsoProjector;
import isoforge.world.PathFinder;

import java.util.Arrays;

/**
 * M3 — construção, ciclo de dia e controle de tempo.
 *
 * <p>O gesto continua o mesmo desde o M1.5: o clique publica uma tarefa no
 * quadro e a unidade livre mais próxima <i>por caminho real</i> a assume. O que
 * o M3 acrescenta é a primeira tarefa que <b>consome</b> o que a colônia
 * produziu: erguer uma construção exige passar no depósito, carregar madeira
 * até o canteiro e ficar parado ali trabalhando. Terminada, ela vira obstáculo
 * de verdade no A* — dá para se murar sozinho, e isso é mecânica.
 *
 * <p>Três decisões desta classe que valem ser ditas em voz alta:
 *
 * <ul>
 *   <li><b>Simulação e apresentação têm relógios diferentes.</b> A câmera e o
 *       cursor andam com o tempo real; unidades, obras e o sol andam com o
 *       tempo simulado, que pausa e acelera. Misturar os dois faria a câmera
 *       congelar junto com o jogo.</li>
 *   <li><b>A luz do dia multiplica as cores do mundo, mas não as da
 *       interface.</b> Marcadores de tarefa e de caminho precisam ser legíveis
 *       às três da manhã.</li>
 *   <li><b>Partículas são desenhadas por cima de tudo</b>, fora da ordenação
 *       por profundidade. Ver a nota em {@link Particles}.</li>
 * </ul>
 */
public class IsoForgeGame extends ApplicationAdapter {

    private static final float TILE_WIDTH = 64f;
    private static final float TILE_HEIGHT = 32f;

    /**
     * Pixels que cada nível de altura sobe na tela. Metade da altura do tile é
     * o valor que faz a parede lateral ficar quadrada na projeção 2:1 — é o
     * que dá a leitura de "cubo" em vez de "losango flutuando".
     */
    private static final float ELEVATION_STEP = 16f;

    private static final float PAN_SPEED = 600f;
    private static final float MIN_ZOOM = 0.25f;
    private static final float MAX_ZOOM = 4f;

    /** Onde as unidades começam: a oeste da rampa, longe do lago. */
    private static final int[][] UNIT_SPAWNS = {
            {5, 21}, {4, 21}, {6, 21}, {4, 20}, {6, 20}
    };

    /** Sem critério nenhum na escolha — placeholder até o jogador trocar. */
    private static final String[] UNIT_NAMES = {"Tico", "Teco", "Bento", "Duda", "Nico"};

    private static final float UNIT_BODY_HEIGHT = 24f;
    private static final float UNIT_SCALE = 0.34f;

    /** Onde a lenha é entregue e de onde o material de obra sai. */
    private static final GridPoint2 DEPOT = new GridPoint2(5, 23);

    /** Um bosque a oeste do spawn — grama plana, fora do alcance do lago. */
    private static final int[][] TREE_SPOTS = {
            {2, 18}, {3, 19}, {1, 20}, {2, 21}, {3, 23}, {2, 25},
            {1, 27}, {4, 27}, {6, 26}, {7, 24}
    };

    private static final float TREE_SCALE = 0.5f;
    private static final float TREE_TRUNK_HEIGHT = 10f;
    private static final float TREE_CANOPY_HEIGHT = 22f;
    private static final Color TREE_TRUNK = new Color(0.45f, 0.32f, 0.20f, 1f);
    private static final Color TREE_LEAVES = new Color(0.25f, 0.55f, 0.28f, 1f);
    private static final Color DEPOT_MARKER = new Color(0.95f, 0.75f, 0.25f, 0.35f);

    /** Andaime: o topo de uma obra que ainda não ganhou telhado. */
    private static final Color SCAFFOLD = new Color(0.74f, 0.68f, 0.52f, 1f);
    private static final Color SITE_MARKER = new Color(0.55f, 0.75f, 1f, 0.35f);
    private static final Color GHOST_OK = new Color(0.45f, 0.95f, 0.60f, 0.45f);
    private static final Color GHOST_BAD = new Color(0.95f, 0.35f, 0.35f, 0.45f);
    private static final Color DUST = new Color(0.86f, 0.82f, 0.70f, 1f);

    /** Carga visível nas mãos da unidade quando ela está transportando algo. */
    private static final float CARGO_SIZE = 7f;

    /**
     * Deslocamento no desenho para unidades na mesma célula não se sobreporem.
     * O ângulo áureo espalha os índices de forma uniforme sem repetir direção,
     * e o eixo Y é achatado à metade para respeitar a projeção 2:1.
     */
    private static final float CROWD_OFFSET = 7f;
    private static final float GOLDEN_ANGLE = 2.3999632f;

    /**
     * Intervalo entre passadas de atribuição de tarefas. A atribuição roda um
     * A* por tarefa aberta e por unidade ociosa, então rodá-la a cada frame
     * seria desperdício puro — o quadro não muda 60 vezes por segundo.
     */
    private static final float CLAIM_INTERVAL = 0.25f;

    /** Segundos de tempo simulado que um dia inteiro leva. */
    private static final float DAY_LENGTH = 150f;

    /** Multiplicadores de velocidade da simulação, ciclados por vírgula/ponto. */
    private static final float[] TIME_SCALES = {1f, 2f, 4f};

    private static final Color BACKGROUND = new Color(0.09f, 0.10f, 0.13f, 1f);
    private static final Color GRID_LINE = new Color(0f, 0f, 0f, 0.22f);
    private static final Color HOVER_FILL = new Color(1f, 1f, 1f, 0.28f);
    private static final Color HOVER_OUTLINE = new Color(1f, 0.85f, 0.35f, 1f);
    private static final Color PATH_MARKER = new Color(1f, 0.84f, 0.35f, 0.30f);
    private static final Color JOB_MARKER = new Color(0.40f, 0.90f, 0.60f, 0.55f);
    private static final Color DRAG_MARKER = new Color(1f, 1f, 1f, 0.45f);

    /**
     * As cores do sol ao longo do dia, com {@code 0} na meia-noite. As duas
     * tabelas andam juntas: {@code LIGHT_STOPS[i]} é o instante em que a luz
     * vale exatamente {@code LIGHT_COLORS[i]}, e entre dois marcos se
     * interpola. É o jeito mais simples de ter amanhecer alaranjado sem
     * escrever uma curva por canal de cor.
     */
    private static final float[] LIGHT_STOPS = {0f, 0.20f, 0.29f, 0.42f, 0.62f, 0.74f, 0.85f, 1f};
    private static final Color[] LIGHT_COLORS = {
            new Color(0.34f, 0.40f, 0.70f, 1f), // madrugada
            new Color(0.34f, 0.40f, 0.70f, 1f),
            new Color(1.00f, 0.76f, 0.60f, 1f), // amanhecer
            new Color(1.00f, 1.00f, 0.97f, 1f), // manhã alta
            new Color(1.00f, 1.00f, 0.97f, 1f), // tarde
            new Color(1.00f, 0.66f, 0.44f, 1f), // entardecer
            new Color(0.34f, 0.40f, 0.70f, 1f), // anoitecer
            new Color(0.34f, 0.40f, 0.70f, 1f),
    };

    /** Uma cor por unidade, para dar para acompanhar quem foi para onde. */
    private static final Color[] UNIT_COLORS = {
            new Color(0.93f, 0.56f, 0.22f, 1f),
            new Color(0.85f, 0.36f, 0.36f, 1f),
            new Color(0.42f, 0.68f, 0.92f, 1f),
            new Color(0.76f, 0.80f, 0.34f, 1f),
            new Color(0.78f, 0.50f, 0.82f, 1f),
    };

    // Faces laterais escurecidas. Duas intensidades diferentes para os dois
    // lados: é o truque mais barato que existe para o olho ler volume, imitando
    // uma luz vindo de cima e da direita.
    private static final float LEFT_FACE_SHADE = 0.52f;
    private static final float RIGHT_FACE_SHADE = 0.74f;

    private GridMap map;
    private IsoProjector projector;
    private PathFinder pathFinder;
    private JobBoard jobBoard;
    private Stockpile stockpile;
    private final Particles particles = new Particles();
    private final Array<Unit> units = new Array<>();
    private final Array<Tree> trees = new Array<>();
    private final Array<Building> buildings = new Array<>();
    private int nextBuildingId;

    private OrthographicCamera camera;
    private ScreenViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private final Matrix4 hudMatrix = new Matrix4();

    private final GridPoint2 hoveredCell = new GridPoint2();
    private boolean hoverValid;

    private boolean[] pathMask;
    private boolean[] jobMask;
    private boolean[] dragMask;
    private final Array<GridPoint2> dragCells = new Array<>();
    private boolean dragging;

    private float claimTimer;
    private float worldClock;
    private float timeOfDay = 0.34f; // começa no meio da manhã
    private int timeScaleIndex;
    private boolean paused;

    private boolean buildMode;
    private BuildingType selectedType = BuildingType.CABANA;

    private String orderStatus = "clique (ou arraste) para publicar tarefas";

    // Buffers reaproveitados a cada frame. Alocar Vector2/float[]/Color dentro
    // do render() geraria lixo 60x por segundo e faria o GC aparecer no gráfico
    // de frametime — é o erro de performance mais comum em libGDX.
    private final Vector2 scratch = new Vector2();
    private final float[] topFace = new float[8];
    private final float[] localShape = new float[8];
    private final float[] worldShape = new float[8];
    private final Color shade = new Color();
    private final Color light = new Color(1f, 1f, 1f, 1f);
    private final Color skyColor = new Color();

    private boolean showGrid = true;

    @Override
    public void create() {
        map = new GridMap(GridMap.DEFAULT_SIZE, GridMap.DEFAULT_SIZE);
        projector = new IsoProjector(TILE_WIDTH, TILE_HEIGHT, ELEVATION_STEP);
        pathFinder = new PathFinder(map);
        stockpile = new Stockpile();
        jobBoard = new JobBoard(DEPOT, stockpile);

        for (int i = 0; i < UNIT_SPAWNS.length; i++) {
            String name = UNIT_NAMES[i % UNIT_NAMES.length];
            units.add(new Unit(i, name, UNIT_SPAWNS[i][0], UNIT_SPAWNS[i][1], map));
        }

        for (int i = 0; i < TREE_SPOTS.length; i++) {
            trees.add(new Tree(i, TREE_SPOTS[i][0], TREE_SPOTS[i][1]));
        }

        int cells = map.getWidth() * map.getHeight();
        pathMask = new boolean[cells];
        jobMask = new boolean[cells];
        dragMask = new boolean[cells];

        camera = new OrthographicCamera();
        viewport = new ScreenViewport(camera);

        // Começa olhando para o centro do mapa em vez do canto (0,0).
        projector.gridToWorld(map.getWidth() / 2f, map.getHeight() / 2f, scratch);
        camera.position.set(scratch.x, scratch.y, 0f);

        // Capacidade folgada: o mapa inteiro passa dos 5000 vértices padrão do
        // ShapeRenderer. Ele se vira sozinho fazendo flush no meio do desenho,
        // mas um buffer maior evita esses draw calls extras.
        shapes = new ShapeRenderer(20000);
        batch = new SpriteBatch();
        font = new BitmapFont();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.input.setInputProcessor(buildInputProcessor());
    }

    private InputAdapter buildInputProcessor() {
        return new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                // Zoom multiplicativo: cada passo muda a escala em ~10%. Somar
                // um valor fixo daria passos gigantes no zoom próximo e
                // imperceptíveis no distante.
                camera.zoom = MathUtils.clamp(camera.zoom * (1f + amountY * 0.1f), MIN_ZOOM, MAX_ZOOM);
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT && hoverValid) {
                    if (buildMode) {
                        placeBuilding(hoveredCell.x, hoveredCell.y);
                    } else {
                        beginDrag();
                    }
                    return true;
                }
                if (button == Input.Buttons.RIGHT && hoverValid) {
                    cancelAt(hoveredCell.x, hoveredCell.y);
                    return true;
                }
                return false;
            }

            @Override
            public boolean touchUp(int screenX, int screenY, int pointer, int button) {
                if (button == Input.Buttons.LEFT && dragging) {
                    endDrag();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                return handleKey(keycode);
            }
        };
    }

    private boolean handleKey(int keycode) {
        switch (keycode) {
            case Input.Keys.G:
                showGrid = !showGrid;
                return true;
            case Input.Keys.B:
                buildMode = !buildMode;
                orderStatus = buildMode
                        ? "modo construcao: " + selectedType.getLabel()
                                + " (" + selectedType.getWoodCost() + " madeira)"
                        : "modo tarefa";
                return true;
            case Input.Keys.TAB:
                selectedType = selectedType.next();
                buildMode = true;
                orderStatus = "modo construcao: " + selectedType.getLabel()
                        + " (" + selectedType.getWoodCost() + " madeira)";
                return true;
            case Input.Keys.SPACE:
                paused = !paused;
                orderStatus = paused ? "pausado" : "retomado";
                return true;
            case Input.Keys.PERIOD:
                timeScaleIndex = Math.min(timeScaleIndex + 1, TIME_SCALES.length - 1);
                return true;
            case Input.Keys.COMMA:
                timeScaleIndex = Math.max(timeScaleIndex - 1, 0);
                return true;
            case Input.Keys.X:
                cancelEverything();
                return true;
            case Input.Keys.ESCAPE:
                Gdx.app.exit();
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------
    // Comando
    // ------------------------------------------------------------------

    /**
     * Começa um arrasto de publicação. Arrastar existe porque marcar seis
     * árvores de um bosque com seis cliques separados é o tipo de trabalho
     * que o jogo deveria estar fazendo pelo jogador.
     */
    private void beginDrag() {
        dragging = true;
        Arrays.fill(dragMask, false);
        dragCells.clear();
        addDragCell();
    }

    private void addDragCell() {
        int index = hoveredCell.y * map.getWidth() + hoveredCell.x;
        if (dragMask[index]) {
            return;
        }
        dragMask[index] = true;
        dragCells.add(new GridPoint2(hoveredCell));
    }

    private void endDrag() {
        dragging = false;
        int posted = 0;
        String lastFailure = null;

        for (GridPoint2 cell : dragCells) {
            String failure = postJobAt(cell.x, cell.y);
            if (failure == null) {
                posted++;
            } else {
                lastFailure = failure;
            }
        }

        Arrays.fill(dragMask, false);
        dragCells.clear();

        if (posted == 0) {
            orderStatus = lastFailure != null ? lastFailure : "nada a publicar";
        } else if (posted == 1) {
            orderStatus = "1 tarefa publicada";
        } else {
            orderStatus = posted + " tarefas publicadas";
        }
        if (!paused) {
            runClaimPass();
        }
    }

    /**
     * Publica a tarefa cabível no tile: cortar, se houver árvore de pé; andar,
     * caso contrário. O quadro decide o tipo pelo que está no tile, e o gesto
     * do jogador é sempre o mesmo.
     *
     * @return null se publicou, ou o motivo da recusa
     */
    private String postJobAt(int x, int y) {
        Tree tree = findStandingTreeAt(x, y);
        Job job = tree != null ? jobBoard.postChop(tree) : jobBoard.postMove(x, y, map);

        if (job != null) {
            return null;
        }
        if (tree != null) {
            return "arvore (" + x + ", " + y + ") ja tem tarefa";
        }
        return "(" + x + ", " + y + ") nao e caminhavel";
    }

    /**
     * Marca um canteiro e publica a obra. A construção aparece na hora, mesmo
     * sem madeira no estoque: o canteiro é a memória visual da ordem, e a
     * tarefa espera no quadro até a colônia ter material.
     */
    private void placeBuilding(int x, int y) {
        String rejection = buildRejection(x, y);
        if (rejection != null) {
            orderStatus = rejection;
            return;
        }

        Building building = new Building(nextBuildingId++, x, y, selectedType);
        buildings.add(building);
        jobBoard.postBuild(building);

        int cost = selectedType.getWoodCost();
        orderStatus = selectedType.getLabel() + " marcada em (" + x + ", " + y + ")"
                + (stockpile.getAvailableWood() >= cost ? "" : " — aguardando madeira");
        if (!paused) {
            runClaimPass();
        }
    }

    private String buildRejection(int x, int y) {
        if (!map.contains(x, y)) {
            return "fora do mapa";
        }
        if (!map.get(x, y).isWalkable()) {
            return "nao da para construir em " + map.get(x, y);
        }
        if (map.isBlocked(x, y)) {
            return "ja tem construcao em (" + x + ", " + y + ")";
        }
        if (x == DEPOT.x && y == DEPOT.y) {
            return "o deposito ocupa esse tile";
        }
        if (findStandingTreeAt(x, y) != null) {
            return "tem arvore em (" + x + ", " + y + ") — corte primeiro";
        }
        if (findSiteAt(x, y) != null) {
            return "ja tem obra marcada ai";
        }
        return null;
    }

    /**
     * Cancela a tarefa publicada naquele tile — e só ela. O cancelamento em
     * massa continua existindo no {@code X}, mas ele deixou de ser a única
     * saída: desfazer um clique errado não deveria custar a fila inteira.
     */
    private void cancelAt(int x, int y) {
        Job job = jobBoard.findByTarget(x, y);
        if (job == null) {
            orderStatus = "nenhuma tarefa em (" + x + ", " + y + ")";
            return;
        }
        releaseJob(job);
        orderStatus = "tarefa em (" + x + ", " + y + ") cancelada";
    }

    private void cancelEverything() {
        Array<Job> open = jobBoard.getJobs();
        for (int i = open.size - 1; i >= 0; i--) {
            releaseJob(open.get(i));
        }
        for (Unit unit : units) {
            unit.stop();
        }
        purgeCancelledBuildings();
        orderStatus = "tudo cancelado";
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

    private Tree findStandingTreeAt(int x, int y) {
        for (Tree tree : trees) {
            if (tree.isStanding() && tree.getX() == x && tree.getY() == y) {
                return tree;
            }
        }
        return null;
    }

    private Building findSiteAt(int x, int y) {
        for (Building building : buildings) {
            if (building.getX() == x && building.getY() == y && !building.isCancelled()) {
                return building;
            }
        }
        return null;
    }

    private void purgeCancelledBuildings() {
        for (int i = buildings.size - 1; i >= 0; i--) {
            if (buildings.get(i).isCancelled()) {
                buildings.removeIndex(i);
            }
        }
    }

    /** Cada tarefa aberta procura a unidade ociosa mais próxima por caminho. */
    private void runClaimPass() {
        jobBoard.assignPass(units, pathFinder, map);
    }

    // ------------------------------------------------------------------
    // Loop
    // ------------------------------------------------------------------

    @Override
    public void render() {
        float realDelta = Gdx.graphics.getDeltaTime();
        float simDelta = paused ? 0f : realDelta * TIME_SCALES[timeScaleIndex];

        handlePan(realDelta);
        updateHoveredCell();
        if (dragging && hoverValid) {
            addDragCell();
        }

        if (simDelta > 0f) {
            stepSimulation(simDelta);
        }
        updateLight();
        rebuildMasks();

        ScreenUtils.clear(skyColor);

        viewport.apply();
        camera.update();
        shapes.setProjectionMatrix(camera.combined);

        drawWorld();
        drawParticles();
        drawHover();
        drawHud();
    }

    private void stepSimulation(float delta) {
        worldClock += delta;
        timeOfDay = (timeOfDay + delta / DAY_LENGTH) % 1f;

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
                burstAtCell(tree.getX(), tree.getY(), TREE_TRUNK_HEIGHT,
                        18, 90f, 0.8f, 3f, TREE_TRUNK);
            } else if (tree.isShaking() && MathUtils.randomBoolean(0.25f)) {
                // Uma lasca solta de vez em quando enquanto o machado bate:
                // barato, e é o que faz o corte parecer estar acontecendo em
                // vez de ser só um cronômetro escondido.
                burstAtCell(tree.getX(), tree.getY(), TREE_TRUNK_HEIGHT * 0.6f,
                        1, 55f, 0.5f, 2.5f, TREE_TRUNK);
            }
        }

        for (Building building : buildings) {
            if (building.pollJustCompleted()) {
                burstAtCell(building.getX(), building.getY(), 6f,
                        24, 110f, 0.9f, 3.5f, DUST);
            }
        }

        jobBoard.purgeCompleted();
        purgeCancelledBuildings();
        particles.update(delta);
    }

    private void burstAtCell(int gridX, int gridY, float heightOffset,
                             int count, float speed, float life, float size, Color color) {
        int level = map.getLevel(gridX, gridY);
        projector.gridToWorld(gridX, gridY, level, scratch);
        particles.burst(scratch.x, scratch.y + heightOffset, count, speed, life, size, color);
    }

    private void handlePan(float delta) {
        // A velocidade escala com o zoom: afastado, o mesmo movimento de tecla
        // precisa cobrir mais mundo para a câmera não parecer travada.
        float speed = PAN_SPEED * camera.zoom * delta;

        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            camera.position.y += speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            camera.position.y -= speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            camera.position.x -= speed;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            camera.position.x += speed;
        }
    }

    /**
     * Interpola a cor do sol entre os marcos da tabela e deriva o céu dela.
     *
     * <p>O céu é o fundo multiplicado pela luz: sem isso, a noite deixaria o
     * terreno azul-escuro flutuando sobre um fundo que continua com a mesma
     * cor do meio-dia, e o efeito desmonta na hora.
     */
    private void updateLight() {
        int i = 0;
        while (i < LIGHT_STOPS.length - 2 && timeOfDay > LIGHT_STOPS[i + 1]) {
            i++;
        }
        float span = LIGHT_STOPS[i + 1] - LIGHT_STOPS[i];
        float t = span <= 0f ? 0f : MathUtils.clamp((timeOfDay - LIGHT_STOPS[i]) / span, 0f, 1f);

        light.set(LIGHT_COLORS[i]).lerp(LIGHT_COLORS[i + 1], t);
        skyColor.set(BACKGROUND).mul(light.r, light.g, light.b, 1f);
    }

    /** Rastros a percorrer e tarefas ainda sem dono, para desenhar no terreno. */
    private void rebuildMasks() {
        Arrays.fill(pathMask, false);
        Arrays.fill(jobMask, false);

        for (Unit unit : units) {
            Array<GridPoint2> path = unit.getPath();
            for (int i = unit.getPathIndex(); i < path.size; i++) {
                GridPoint2 cell = path.get(i);
                pathMask[cell.y * map.getWidth() + cell.x] = true;
            }
        }
        jobBoard.markOpenJobs(jobMask, map.getWidth());
    }

    /**
     * Descobre qual tile está sob o cursor.
     *
     * <p>Com terreno plano bastava inverter a projeção. Com elevação não basta:
     * o mesmo ponto da tela pode pertencer ao topo de um tile alto ao fundo ou
     * ao topo de um tile baixo à frente, e quem vence é o que está desenhado
     * por cima — ou seja, o da <b>frente</b>.
     *
     * <p>Por isso a varredura vai da frente para o fundo (soma x+y crescente,
     * o inverso da ordem de desenho) e o primeiro acerto ganha. É a ordem de
     * pintura ao contrário, que é exatamente a definição de "o que o jogador
     * está enxergando neste pixel".
     *
     * <p>Limitação conhecida: só as faces de topo entram no teste. Passar o
     * mouse sobre a parede de um penhasco — ou sobre uma construção alta —
     * seleciona o tile atrás dela.
     */
    private void updateHoveredCell() {
        scratch.set(Gdx.input.getX(), Gdx.input.getY());
        // unproject() resolve dois problemas de uma vez: a origem do mouse fica
        // no topo da tela enquanto o mundo tem Y para cima, e a posição/zoom da
        // câmera precisam ser desfeitos.
        viewport.unproject(scratch);

        hoverValid = false;
        int maxSum = (map.getWidth() - 1) + (map.getHeight() - 1);
        for (int sum = 0; sum <= maxSum && !hoverValid; sum++) {
            int xStart = Math.max(0, sum - (map.getHeight() - 1));
            int xEnd = Math.min(map.getWidth() - 1, sum);
            for (int x = xStart; x <= xEnd; x++) {
                int y = sum - x;
                if (projector.topFaceContains(x, y, map.getLevel(x, y), scratch.x, scratch.y)) {
                    hoveredCell.set(x, y);
                    hoverValid = true;
                    break;
                }
            }
        }
    }

    // ------------------------------------------------------------------
    // Desenho
    // ------------------------------------------------------------------

    /**
     * Desenha o mundo do fundo para a frente, diagonal por diagonal.
     *
     * <p>Uma "diagonal" aqui é o conjunto de tiles com a mesma soma x+y, que na
     * projeção caem todos na mesma linha da tela. Somas maiores estão mais ao
     * fundo, então percorremos do maior para o menor.
     *
     * <p>Árvores, construções e unidades são desenhadas dentro da diagonal a
     * que pertencem, e não numa passada separada no fim. É o que as faz sumir
     * atrás do platô e reaparecer na frente dele: quem decide a oclusão é a
     * posição no mundo, não a ordem em que o código foi escrito.
     *
     * <p>As linhas de grade seguem a mesma lógica. Se fossem desenhadas depois
     * de tudo, o contorno dos tiles do fundo vazaria através dos platôs que
     * estão na frente. Custa um par begin/end por diagonal, o que é barato
     * nesta escala e mantém a oclusão correta.
     */
    private void drawWorld() {
        int maxSum = (map.getWidth() - 1) + (map.getHeight() - 1);

        for (int sum = maxSum; sum >= 0; sum--) {
            int xStart = Math.max(0, sum - (map.getHeight() - 1));
            int xEnd = Math.min(map.getWidth() - 1, sum);

            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int x = xStart; x <= xEnd; x++) {
                drawTile(x, sum - x);
            }
            for (Building building : buildings) {
                if (building.getX() + building.getY() == sum) {
                    drawBuilding(building);
                }
            }
            for (Tree tree : trees) {
                if (!tree.isChopped() && tree.getX() + tree.getY() == sum) {
                    drawTree(tree);
                }
            }
            for (Unit unit : units) {
                Vector2 pos = unit.getPosition();
                if (Math.round(pos.x + pos.y) == sum) {
                    drawUnit(unit);
                }
            }
            shapes.end();

            if (showGrid) {
                shapes.begin(ShapeRenderer.ShapeType.Line);
                shapes.setColor(GRID_LINE);
                for (int x = xStart; x <= xEnd; x++) {
                    int y = sum - x;
                    shapes.polygon(projector.topFacePolygon(x, y, map.getLevel(x, y), topFace));
                }
                shapes.end();
            }
        }
    }

    /** Um tile = duas paredes laterais (se houver altura) mais a face de topo. */
    private void drawTile(int gridX, int gridY) {
        int level = map.getLevel(gridX, gridY);
        Color base = map.get(gridX, gridY).getColor();
        projector.topFacePolygon(gridX, gridY, level, topFace);

        float topX = topFace[0], topY = topFace[1];
        float rightX = topFace[2], rightY = topFace[3];
        float bottomX = topFace[4], bottomY = topFace[5];
        float leftX = topFace[6], leftY = topFace[7];

        if (level > 0) {
            float drop = level * ELEVATION_STEP;

            setLitColor(base, LEFT_FACE_SHADE, 1f);
            fillQuad(leftX, leftY, bottomX, bottomY, bottomX, bottomY - drop, leftX, leftY - drop);

            setLitColor(base, RIGHT_FACE_SHADE, 1f);
            fillQuad(bottomX, bottomY, rightX, rightY, rightX, rightY - drop, bottomX, bottomY - drop);
        }

        setLitColor(base, 1f, 1f);
        fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);

        // Marcadores de interface não recebem a luz do dia: eles precisam ser
        // igualmente legíveis ao meio-dia e às três da manhã.
        int index = gridY * map.getWidth() + gridX;
        if (dragMask[index]) {
            shapes.setColor(DRAG_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        } else if (jobMask[index]) {
            shapes.setColor(JOB_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        } else if (pathMask[index]) {
            shapes.setColor(PATH_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        }

        if (gridX == DEPOT.x && gridY == DEPOT.y) {
            shapes.setColor(DEPOT_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        }
    }

    /**
     * Uma construção: o canteiro marcado no chão e, acima dele, uma caixa cuja
     * altura é o progresso da obra. Ver o prédio subir tile a tile é a forma
     * mais barata de mostrar trabalho acontecendo — nenhuma barra de progresso
     * na interface faz isso tão bem.
     */
    private void drawBuilding(Building building) {
        int level = map.getLevel(building.getX(), building.getY());
        projector.topFacePolygon(building.getX(), building.getY(), level, topFace);

        float topX = topFace[0], topY = topFace[1];
        float rightX = topFace[2], rightY = topFace[3];
        float bottomX = topFace[4], bottomY = topFace[5];
        float leftX = topFace[6], leftY = topFace[7];

        if (!building.isComplete()) {
            shapes.setColor(SITE_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        }

        BuildingType type = building.getType();
        float height = type.getVisualHeight() * building.getProgress();
        if (height < 1f) {
            return;
        }

        setLitColor(type.getWallColor(), LEFT_FACE_SHADE, 1f);
        fillQuad(leftX, leftY, bottomX, bottomY, bottomX, bottomY + height, leftX, leftY + height);

        setLitColor(type.getWallColor(), RIGHT_FACE_SHADE, 1f);
        fillQuad(bottomX, bottomY, rightX, rightY, rightX, rightY + height, bottomX, bottomY + height);

        Color roof = building.isComplete() ? type.getRoofColor() : SCAFFOLD;
        setLitColor(roof, 1f, 1f);
        fillDiamond(topX, topY + height, rightX, rightY + height,
                bottomX, bottomY + height, leftX, leftY + height);
    }

    /**
     * Uma árvore: tronco retangular e copa em losango. Ela treme enquanto o
     * machado bate e tomba quando cai, girando em torno da base.
     *
     * <p>A rotação é feita em espaço de tela, o que é geometricamente uma
     * mentira num mundo isométrico — a árvore deveria tombar <i>dentro</i> do
     * plano do chão, não sobre ele. Na prática o olho aceita, e o custo da
     * alternativa (projetar a árvore como um objeto 3D só para isso) não se
     * paga enquanto o render for 2.5D.
     */
    private void drawTree(Tree tree) {
        int level = map.getLevel(tree.getX(), tree.getY());
        projector.gridToWorld(tree.getX(), tree.getY(), level, scratch);

        float baseX = scratch.x;
        float baseY = scratch.y;
        if (tree.isShaking()) {
            baseX += MathUtils.sin(worldClock * 38f) * 1.8f;
        }

        float fall = tree.getFallProgress();
        // Ao quadrado: a árvore hesita no começo e desaba no fim, que é como
        // uma queda se parece. Linear pareceria uma porta se abrindo.
        float angle = -78f * fall * fall;
        float cos = MathUtils.cosDeg(angle);
        float sin = MathUtils.sinDeg(angle);
        float alpha = fall > 0.8f ? 1f - (fall - 0.8f) / 0.2f : 1f;

        float hw = projector.getHalfWidth() * TREE_SCALE;
        float trunkHalf = hw * 0.3f;

        setLocalQuad(-trunkHalf, 0f, trunkHalf, 0f, trunkHalf, TREE_TRUNK_HEIGHT,
                -trunkHalf, TREE_TRUNK_HEIGHT);
        rotateShape(baseX, baseY, cos, sin);
        setLitColor(TREE_TRUNK, 1f, alpha);
        fillShapeQuad();

        float canopyY = TREE_TRUNK_HEIGHT + TREE_CANOPY_HEIGHT * 0.5f;
        setLocalQuad(0f, canopyY + TREE_CANOPY_HEIGHT * 0.5f, hw, canopyY,
                0f, canopyY - TREE_CANOPY_HEIGHT * 0.5f, -hw, canopyY);
        rotateShape(baseX, baseY, cos, sin);
        setLitColor(TREE_LEAVES, 1f, alpha);
        fillShapeQuad();
    }

    /** A unidade é uma coluninha: mesma técnica dos tiles, em escala menor. */
    private void drawUnit(Unit unit) {
        Vector2 pos = unit.getPosition();
        projector.gridToWorld(pos.x, pos.y, scratch);

        float angle = unit.getId() * GOLDEN_ANGLE;
        float centerX = scratch.x + MathUtils.cos(angle) * CROWD_OFFSET;
        float groundY = scratch.y + MathUtils.sin(angle) * CROWD_OFFSET * 0.5f
                + unit.getVisualLevel() * ELEVATION_STEP;

        float centerY = groundY + UNIT_BODY_HEIGHT;
        float hw = projector.getHalfWidth() * UNIT_SCALE;
        float hh = projector.getHalfHeight() * UNIT_SCALE;

        float topX = centerX,        topY = centerY + hh;
        float rightX = centerX + hw, rightY = centerY;
        float bottomX = centerX,     bottomY = centerY - hh;
        float leftX = centerX - hw,  leftY = centerY;

        Color base = UNIT_COLORS[unit.getId() % UNIT_COLORS.length];

        setLitColor(base, LEFT_FACE_SHADE, 1f);
        fillQuad(leftX, leftY, bottomX, bottomY,
                bottomX, bottomY - UNIT_BODY_HEIGHT, leftX, leftY - UNIT_BODY_HEIGHT);

        setLitColor(base, RIGHT_FACE_SHADE, 1f);
        fillQuad(bottomX, bottomY, rightX, rightY,
                rightX, rightY - UNIT_BODY_HEIGHT, bottomX, bottomY - UNIT_BODY_HEIGHT);

        setLitColor(base, 1f, 1f);
        fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);

        // A carga na cabeça é o único jeito de olhar para o mapa e saber quem
        // está indo buscar e quem está voltando cheio, sem ler o painel.
        if (unit.isCarrying()) {
            float cargoY = topY + CARGO_SIZE * 0.6f;
            setLitColor(TREE_TRUNK, 1f, 1f);
            fillQuad(centerX - CARGO_SIZE, cargoY, centerX + CARGO_SIZE, cargoY,
                    centerX + CARGO_SIZE, cargoY + CARGO_SIZE,
                    centerX - CARGO_SIZE, cargoY + CARGO_SIZE);
        }
    }

    private void drawParticles() {
        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < particles.getCapacity(); i++) {
            if (!particles.isAlive(i)) {
                continue;
            }
            float size = particles.getSize(i);
            shade.set(particles.getRed(i) * light.r, particles.getGreen(i) * light.g,
                    particles.getBlue(i) * light.b, particles.getAlpha(i));
            shapes.setColor(shade);
            shapes.rect(particles.getX(i) - size * 0.5f, particles.getY(i) - size * 0.5f, size, size);
        }
        shapes.end();
    }

    private void drawHover() {
        if (!hoverValid) {
            return;
        }
        int level = map.getLevel(hoveredCell.x, hoveredCell.y);
        projector.topFacePolygon(hoveredCell.x, hoveredCell.y, level, topFace);

        boolean canBuild = buildMode && buildRejection(hoveredCell.x, hoveredCell.y) == null;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(buildMode ? (canBuild ? GHOST_OK : GHOST_BAD) : HOVER_FILL);
        fillDiamond(topFace[0], topFace[1], topFace[2], topFace[3],
                topFace[4], topFace[5], topFace[6], topFace[7]);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(buildMode ? (canBuild ? GHOST_OK : GHOST_BAD) : HOVER_OUTLINE);
        shapes.polygon(topFace);

        // Fantasma da construção: o contorno da caixa que vai ocupar o tile,
        // para o jogador ver o tamanho antes de gastar madeira nele.
        if (buildMode) {
            float height = selectedType.getVisualHeight();
            for (int i = 0; i < 8; i += 2) {
                int j = (i + 2) % 8;
                shapes.line(topFace[i], topFace[i + 1] + height, topFace[j], topFace[j + 1] + height);
                shapes.line(topFace[i], topFace[i + 1], topFace[i], topFace[i + 1] + height);
            }
        }
        shapes.end();
    }

    // ------------------------------------------------------------------
    // Primitivas de desenho
    // ------------------------------------------------------------------

    /** Cor base multiplicada pela luz do dia e escurecida pela face. */
    private void setLitColor(Color base, float faceShade, float alpha) {
        shade.set(base.r * light.r * faceShade,
                base.g * light.g * faceShade,
                base.b * light.b * faceShade,
                alpha);
        shapes.setColor(shade);
    }

    private void setLocalQuad(float ax, float ay, float bx, float by,
                              float cx, float cy, float dx, float dy) {
        localShape[0] = ax; localShape[1] = ay;
        localShape[2] = bx; localShape[3] = by;
        localShape[4] = cx; localShape[5] = cy;
        localShape[6] = dx; localShape[7] = dy;
    }

    /** Gira os 4 pontos locais em torno da base e os leva para o mundo. */
    private void rotateShape(float baseX, float baseY, float cos, float sin) {
        for (int i = 0; i < 8; i += 2) {
            float lx = localShape[i];
            float ly = localShape[i + 1];
            worldShape[i] = baseX + lx * cos - ly * sin;
            worldShape[i + 1] = baseY + lx * sin + ly * cos;
        }
    }

    private void fillShapeQuad() {
        fillQuad(worldShape[0], worldShape[1], worldShape[2], worldShape[3],
                worldShape[4], worldShape[5], worldShape[6], worldShape[7]);
    }

    /** Losango (topo, direita, base, esquerda) como dois triângulos. */
    private void fillDiamond(float topX, float topY, float rightX, float rightY,
                             float bottomX, float bottomY, float leftX, float leftY) {
        shapes.triangle(topX, topY, rightX, rightY, bottomX, bottomY);
        shapes.triangle(topX, topY, bottomX, bottomY, leftX, leftY);
    }

    /** Quadrilátero convexo como dois triângulos, em torno do vértice A. */
    private void fillQuad(float ax, float ay, float bx, float by,
                          float cx, float cy, float dx, float dy) {
        shapes.triangle(ax, ay, bx, by, cx, cy);
        shapes.triangle(ax, ay, cx, cy, dx, dy);
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    private void drawHud() {
        String cellText = hoverValid
                ? hoveredCell.x + ", " + hoveredCell.y
                        + "  nivel " + map.getLevel(hoveredCell.x, hoveredCell.y)
                        + "  (" + map.get(hoveredCell.x, hoveredCell.y) + ")"
                : "fora do mapa";

        int idle = 0;
        for (Unit unit : units) {
            if (unit.isIdle()) {
                idle++;
            }
        }

        batch.setProjectionMatrix(hudMatrix);
        batch.begin();
        float top = Gdx.graphics.getHeight() - 10f;
        font.draw(batch, "Tile: " + cellText, 12f, top);
        font.draw(batch, "Unidades: " + units.size + "   ociosas: " + idle, 12f, top - 20f);

        int starved = jobBoard.getStarvedCount();
        font.draw(batch, "Tarefas: " + jobBoard.getTotalCount()
                + "   sem dono: " + jobBoard.getOpenCount()
                + (starved > 0 ? "   sem madeira: " + starved : ""), 12f, top - 40f);

        font.draw(batch, "Madeira: " + stockpile.getWood()
                + (stockpile.getReservedWood() > 0
                        ? " (" + stockpile.getReservedWood() + " reservada)" : "")
                + "   Construcoes: " + buildings.size, 12f, top - 60f);

        font.draw(batch, "Hora: " + describeClock()
                + "   Tempo: " + (paused ? "pausado" : (int) TIME_SCALES[timeScaleIndex] + "x")
                + "   Modo: " + (buildMode ? "construir " + selectedType.getLabel()
                        + " (" + selectedType.getWoodCost() + " mad.)" : "tarefas"),
                12f, top - 80f);

        font.draw(batch, "Ordem: " + orderStatus, 12f, top - 100f);
        font.draw(batch, String.format("Zoom: %.2f   FPS: %d",
                camera.zoom, Gdx.graphics.getFramesPerSecond()), 12f, top - 120f);

        font.draw(batch, "Esq: publicar (arraste p/ varias)   Dir: cancelar tarefa do tile   "
                + "X: cancelar tudo   B: construir   TAB: trocar predio", 12f, top - 145f);
        font.draw(batch, "SPACE: pausar   , .: velocidade   WASD: camera   "
                + "Scroll: zoom   G: grade   ESC: sair", 12f, top - 163f);

        drawUnitPanel(top);
        batch.end();
    }

    /** O relógio do mundo em HH:MM, com meia-noite em {@code timeOfDay == 0}. */
    private String describeClock() {
        int minutes = (int) (timeOfDay * 24f * 60f);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }

    /** Tabela à direita: uma linha por unidade, nome e o que está fazendo agora. */
    private void drawUnitPanel(float top) {
        float panelX = Gdx.graphics.getWidth() - 200f;
        font.draw(batch, "Unidades", panelX, top);
        for (int i = 0; i < units.size; i++) {
            Unit unit = units.get(i);
            font.setColor(UNIT_COLORS[unit.getId() % UNIT_COLORS.length]);
            font.draw(batch, unit.getName() + ": " + unit.describeState(), panelX, top - 20f - i * 20f);
        }
        font.setColor(Color.WHITE);
    }

    @Override
    public void resize(int width, int height) {
        // false = não recentraliza a câmera; redimensionar a janela não deve
        // teleportar o jogador para o meio do mapa.
        viewport.update(width, height, false);
        hudMatrix.setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void dispose() {
        shapes.dispose();
        batch.dispose();
        font.dispose();
    }
}
