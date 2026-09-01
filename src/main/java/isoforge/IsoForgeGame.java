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
import isoforge.entity.Job;
import isoforge.entity.JobBoard;
import isoforge.entity.Unit;
import isoforge.world.GridMap;
import isoforge.world.IsoProjector;
import isoforge.world.PathFinder;

import java.util.Arrays;

/**
 * M1.5 — várias unidades comandadas por quadro de tarefas.
 *
 * <p>O M1 tinha uma unidade e o clique a movia. Agora há cinco, e o clique não
 * move ninguém em particular: ele <b>publica uma tarefa</b>, e a unidade livre
 * mais próxima a assume. É o modelo do Castle Story, e é o que dispensa
 * seleção por caixa, grupos e formação — o gesto do jogador é o mesmo com 5 ou
 * com 50 unidades.
 *
 * <p>"Mais próxima" é medida por <b>caminho real</b>, não por linha reta. Com
 * um platô no meio do mapa a linha reta mente: um destino do outro lado da
 * parede parece perto e está a trinta passos, do outro lado da rampa.
 *
 * <p>Unidades não se bloqueiam — atravessam umas às outras, com um pequeno
 * deslocamento no desenho para não sumirem uma dentro da outra. Ver a nota em
 * {@link Unit} sobre por que colisão dura seria uma armadilha no gargalo.
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

    private static final float UNIT_BODY_HEIGHT = 24f;
    private static final float UNIT_SCALE = 0.34f;

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

    private static final Color BACKGROUND = new Color(0.09f, 0.10f, 0.13f, 1f);
    private static final Color GRID_LINE = new Color(0f, 0f, 0f, 0.22f);
    private static final Color HOVER_FILL = new Color(1f, 1f, 1f, 0.28f);
    private static final Color HOVER_OUTLINE = new Color(1f, 0.85f, 0.35f, 1f);
    private static final Color PATH_MARKER = new Color(1f, 0.84f, 0.35f, 0.30f);
    private static final Color JOB_MARKER = new Color(0.40f, 0.90f, 0.60f, 0.55f);

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
    private final Array<Unit> units = new Array<>();

    private OrthographicCamera camera;
    private ScreenViewport viewport;
    private ShapeRenderer shapes;
    private SpriteBatch batch;
    private BitmapFont font;
    private final Matrix4 hudMatrix = new Matrix4();

    private final GridPoint2 hoveredCell = new GridPoint2();
    private boolean hoverValid;

    private final Array<GridPoint2> pathBuffer = new Array<>();
    private final GridPoint2 scratchCell = new GridPoint2();
    private boolean[] pathMask;
    private boolean[] jobMask;
    private float claimTimer;
    private String orderStatus = "clique num tile para publicar uma tarefa";

    // Buffers reaproveitados a cada frame. Alocar Vector2/float[]/Color dentro
    // do render() geraria lixo 60x por segundo e faria o GC aparecer no gráfico
    // de frametime — é o erro de performance mais comum em libGDX.
    private final Vector2 scratch = new Vector2();
    private final float[] topFace = new float[8];
    private final Color shade = new Color();

    private boolean showGrid = true;

    @Override
    public void create() {
        map = new GridMap(GridMap.DEFAULT_SIZE, GridMap.DEFAULT_SIZE);
        projector = new IsoProjector(TILE_WIDTH, TILE_HEIGHT, ELEVATION_STEP);
        pathFinder = new PathFinder(map);
        jobBoard = new JobBoard();

        for (int i = 0; i < UNIT_SPAWNS.length; i++) {
            units.add(new Unit(i, UNIT_SPAWNS[i][0], UNIT_SPAWNS[i][1], map));
        }

        int cells = map.getWidth() * map.getHeight();
        pathMask = new boolean[cells];
        jobMask = new boolean[cells];

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

        Gdx.input.setInputProcessor(new InputAdapter() {
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
                    postMoveJob(hoveredCell.x, hoveredCell.y);
                    return true;
                }
                if (button == Input.Buttons.RIGHT) {
                    cancelEverything();
                    return true;
                }
                return false;
            }

            @Override
            public boolean keyDown(int keycode) {
                if (keycode == Input.Keys.G) {
                    showGrid = !showGrid;
                    return true;
                }
                if (keycode == Input.Keys.ESCAPE) {
                    Gdx.app.exit();
                    return true;
                }
                return false;
            }
        });
    }

    /**
     * Publica uma tarefa de deslocamento e tenta atribuí-la na hora.
     *
     * <p>A atribuição imediata é só para o clique parecer responsivo: sem ela o
     * jogador esperaria até {@link #CLAIM_INTERVAL} para ver alguém se mexer.
     */
    private void postMoveJob(int x, int y) {
        Job job = jobBoard.postMove(x, y, map);
        if (job == null) {
            orderStatus = "(" + x + ", " + y + ") nao e caminhavel";
            return;
        }
        orderStatus = "tarefa publicada em (" + x + ", " + y + ")";
        runClaimPass();
    }

    private void cancelEverything() {
        jobBoard.clear();
        for (Unit unit : units) {
            unit.stop();
        }
        orderStatus = "tudo cancelado";
    }

    /** Cada tarefa aberta procura a unidade ociosa mais próxima por caminho. */
    private void runClaimPass() {
        jobBoard.assignPass(units, pathFinder, map);
    }

    @Override
    public void render() {
        float delta = Gdx.graphics.getDeltaTime();

        handlePan(delta);
        updateHoveredCell();

        claimTimer += delta;
        if (claimTimer >= CLAIM_INTERVAL) {
            claimTimer = 0f;
            runClaimPass();
        }

        for (Unit unit : units) {
            unit.update(delta, map);
            // Parou de andar com tarefa em mãos = chegou. No M2, chegar até a
            // árvore vai ser o começo do trabalho, não o fim dele.
            if (unit.getCurrentJob() != null && !unit.isMoving()) {
                unit.finishJob();
            }
        }
        jobBoard.purgeCompleted();
        rebuildMasks();

        ScreenUtils.clear(BACKGROUND);

        viewport.apply();
        camera.update();
        shapes.setProjectionMatrix(camera.combined);

        drawWorld();
        drawHover();
        drawHud();
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
     * mouse sobre a parede de um penhasco seleciona o tile atrás dela em vez do
     * penhasco.
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

    /**
     * Desenha o mundo do fundo para a frente, diagonal por diagonal.
     *
     * <p>Uma "diagonal" aqui é o conjunto de tiles com a mesma soma x+y, que na
     * projeção caem todos na mesma linha da tela. Somas maiores estão mais ao
     * fundo, então percorremos do maior para o menor.
     *
     * <p>As unidades são desenhadas dentro da diagonal a que pertencem, e não
     * numa passada separada no fim. É o que as faz sumir atrás do platô e
     * reaparecer na frente dele: quem decide a oclusão é a posição no mundo,
     * não a ordem em que o código foi escrito.
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

            shade.set(base).mul(LEFT_FACE_SHADE, LEFT_FACE_SHADE, LEFT_FACE_SHADE, 1f);
            shapes.setColor(shade);
            fillQuad(leftX, leftY, bottomX, bottomY, bottomX, bottomY - drop, leftX, leftY - drop);

            shade.set(base).mul(RIGHT_FACE_SHADE, RIGHT_FACE_SHADE, RIGHT_FACE_SHADE, 1f);
            shapes.setColor(shade);
            fillQuad(bottomX, bottomY, rightX, rightY, rightX, rightY - drop, bottomX, bottomY - drop);
        }

        shapes.setColor(base);
        fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);

        int index = gridY * map.getWidth() + gridX;
        if (jobMask[index]) {
            shapes.setColor(JOB_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        } else if (pathMask[index]) {
            shapes.setColor(PATH_MARKER);
            fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
        }
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

        shade.set(base).mul(LEFT_FACE_SHADE, LEFT_FACE_SHADE, LEFT_FACE_SHADE, 1f);
        shapes.setColor(shade);
        fillQuad(leftX, leftY, bottomX, bottomY,
                bottomX, bottomY - UNIT_BODY_HEIGHT, leftX, leftY - UNIT_BODY_HEIGHT);

        shade.set(base).mul(RIGHT_FACE_SHADE, RIGHT_FACE_SHADE, RIGHT_FACE_SHADE, 1f);
        shapes.setColor(shade);
        fillQuad(bottomX, bottomY, rightX, rightY,
                rightX, rightY - UNIT_BODY_HEIGHT, bottomX, bottomY - UNIT_BODY_HEIGHT);

        shapes.setColor(base);
        fillDiamond(topX, topY, rightX, rightY, bottomX, bottomY, leftX, leftY);
    }

    private void drawHover() {
        if (!hoverValid) {
            return;
        }
        int level = map.getLevel(hoveredCell.x, hoveredCell.y);
        projector.topFacePolygon(hoveredCell.x, hoveredCell.y, level, topFace);

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(HOVER_FILL);
        fillDiamond(topFace[0], topFace[1], topFace[2], topFace[3],
                topFace[4], topFace[5], topFace[6], topFace[7]);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(HOVER_OUTLINE);
        shapes.polygon(topFace);
        shapes.end();
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
        font.draw(batch, "Tarefas: " + jobBoard.getTotalCount()
                + "   sem dono: " + jobBoard.getOpenCount(), 12f, top - 40f);
        font.draw(batch, "Ordem: " + orderStatus, 12f, top - 60f);
        font.draw(batch, String.format("Zoom: %.2f   FPS: %d", camera.zoom, Gdx.graphics.getFramesPerSecond()), 12f, top - 80f);
        font.draw(batch, "Clique esq: publicar tarefa   Clique dir: cancelar tudo   "
                + "WASD: camera   Scroll: zoom   G: grid   ESC: sair", 12f, top - 100f);
        batch.end();
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
