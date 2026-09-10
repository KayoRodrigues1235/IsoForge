package isoforge.render;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.glutils.ShapeRenderer;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ScreenUtils;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import isoforge.entity.Building;
import isoforge.entity.BuildingType;
import isoforge.entity.Tree;
import isoforge.entity.Unit;
import isoforge.fx.Particles;
import isoforge.sim.World;
import isoforge.world.GridMap;
import isoforge.world.IsoProjector;

import java.util.Arrays;

/**
 * O renderizador 2.5D: o mundo desenhado como geometria chapada, sem uma única
 * textura, em projeção isométrica 2:1.
 *
 * <p>Toda a matemática da projeção é detalhe desta classe — o {@link
 * IsoProjector} nasce aqui e não sai daqui. Um renderizador 3D não vai precisar
 * dele: naquele mundo a câmera faz a projeção, e converter grid para posição é
 * trivial. Foi por isso que a projeção veio junto quando o desenho saiu do
 * jogo; deixá-la solta seria deixar a segunda implementação nascer com uma
 * dependência que não usa.
 *
 * <p>O que dá volume aqui é uma soma de truques baratos: faces laterais em
 * duas intensidades para imitar luz vinda de cima e da direita, ordenação por
 * diagonais para resolver oclusão, e a cor de tudo multiplicada pela luz do
 * dia. Nenhum deles sobrevive à migração — e nenhum precisa, porque a
 * simulação não sabe que existem.
 */
public final class IsoShapeRenderer implements WorldRenderer {

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

    private static final float UNIT_BODY_HEIGHT = 24f;
    private static final float UNIT_SCALE = 0.34f;

    private static final float TREE_SCALE = 0.5f;
    private static final float TREE_TRUNK_HEIGHT = 10f;
    private static final float TREE_CANOPY_HEIGHT = 22f;

    /** Carga visível nas mãos da unidade quando ela está transportando algo. */
    private static final float CARGO_SIZE = 7f;

    /**
     * Deslocamento no desenho para unidades na mesma célula não se sobreporem.
     * O ângulo áureo espalha os índices de forma uniforme sem repetir direção,
     * e o eixo Y é achatado à metade para respeitar a projeção 2:1.
     */
    private static final float CROWD_OFFSET = 7f;
    private static final float GOLDEN_ANGLE = 2.3999632f;

    private static final Color BACKGROUND = new Color(0.09f, 0.10f, 0.13f, 1f);
    private static final Color GRID_LINE = new Color(0f, 0f, 0f, 0.22f);
    private static final Color TREE_TRUNK = new Color(0.45f, 0.32f, 0.20f, 1f);
    private static final Color TREE_LEAVES = new Color(0.25f, 0.55f, 0.28f, 1f);
    private static final Color DEPOT_MARKER = new Color(0.95f, 0.75f, 0.25f, 0.35f);

    /** Andaime: o topo de uma obra que ainda não ganhou telhado. */
    private static final Color SCAFFOLD = new Color(0.74f, 0.68f, 0.52f, 1f);
    private static final Color SITE_MARKER = new Color(0.55f, 0.75f, 1f, 0.35f);
    private static final Color GHOST_OK = new Color(0.45f, 0.95f, 0.60f, 0.45f);
    private static final Color GHOST_BAD = new Color(0.95f, 0.35f, 0.35f, 0.45f);
    private static final Color HOVER_FILL = new Color(1f, 1f, 1f, 0.28f);
    private static final Color HOVER_OUTLINE = new Color(1f, 0.85f, 0.35f, 1f);
    private static final Color PATH_MARKER = new Color(1f, 0.84f, 0.35f, 0.30f);
    private static final Color JOB_MARKER = new Color(0.40f, 0.90f, 0.60f, 0.55f);
    private static final Color DRAG_MARKER = new Color(1f, 1f, 1f, 0.45f);

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

    private final IsoProjector projector =
            new IsoProjector(TILE_WIDTH, TILE_HEIGHT, ELEVATION_STEP);

    private final OrthographicCamera camera = new OrthographicCamera();
    private final ScreenViewport viewport = new ScreenViewport(camera);

    // Capacidade folgada: o mapa inteiro passa dos 5000 vértices padrão do
    // ShapeRenderer. Ele se vira sozinho fazendo flush no meio do desenho,
    // mas um buffer maior evita esses draw calls extras.
    private final ShapeRenderer shapes = new ShapeRenderer(20000);

    private boolean gridVisible = true;

    /**
     * Máscaras de destaque no terreno. São detalhe desta implementação: um
     * renderizador 3D pode muito bem destacar um tile de outro jeito, e não
     * teria uso para arrays indexados por célula.
     */
    private boolean[] pathMask;
    private boolean[] jobMask;
    private boolean[] dragMask;

    // Buffers reaproveitados a cada frame. Alocar Vector2/float[]/Color dentro
    // do desenho geraria lixo 60x por segundo e faria o GC aparecer no gráfico
    // de frametime — é o erro de performance mais comum em libGDX.
    private final Vector2 scratch = new Vector2();
    private final float[] topFace = new float[8];
    private final float[] localShape = new float[8];
    private final float[] worldShape = new float[8];
    private final Color shade = new Color();
    private final Color skyColor = new Color();

    /** A luz do frame corrente, guardada para as primitivas não repassarem o mundo. */
    private final Color light = new Color(1f, 1f, 1f, 1f);

    @Override
    public String getName() {
        return "2.5D (ShapeRenderer)";
    }

    // ------------------------------------------------------------------
    // Câmera e picking
    // ------------------------------------------------------------------

    @Override
    public void pan(float dirX, float dirY, float delta) {
        // A velocidade escala com o zoom: afastado, o mesmo movimento de tecla
        // precisa cobrir mais mundo para a câmera não parecer travada.
        float speed = PAN_SPEED * camera.zoom * delta;
        camera.position.add(dirX * speed, dirY * speed, 0f);
    }

    @Override
    public void zoom(float steps) {
        // Zoom multiplicativo: cada passo muda a escala em ~10%. Somar um valor
        // fixo daria passos gigantes no zoom próximo e imperceptíveis no distante.
        camera.zoom = MathUtils.clamp(camera.zoom * (1f + steps * 0.1f), MIN_ZOOM, MAX_ZOOM);
    }

    @Override
    public void centerOn(float gridX, float gridY) {
        projector.gridToWorld(gridX, gridY, scratch);
        camera.position.set(scratch.x, scratch.y, 0f);
    }

    @Override
    public float getZoom() {
        return camera.zoom;
    }

    @Override
    public void setGridVisible(boolean visible) {
        gridVisible = visible;
    }

    @Override
    public boolean isGridVisible() {
        return gridVisible;
    }

    @Override
    public void resize(int width, int height) {
        // false = não recentraliza a câmera; redimensionar a janela não deve
        // teleportar o jogador para o meio do mapa.
        viewport.update(width, height, false);
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
    @Override
    public boolean pickCell(World world, int screenX, int screenY, GridPoint2 out) {
        scratch.set(screenX, screenY);
        // unproject() resolve dois problemas de uma vez: a origem do mouse fica
        // no topo da tela enquanto o mundo tem Y para cima, e a posição/zoom da
        // câmera precisam ser desfeitos.
        viewport.unproject(scratch);

        GridMap map = world.getMap();
        int maxSum = (map.getWidth() - 1) + (map.getHeight() - 1);
        for (int sum = 0; sum <= maxSum; sum++) {
            int xStart = Math.max(0, sum - (map.getHeight() - 1));
            int xEnd = Math.min(map.getWidth() - 1, sum);
            for (int x = xStart; x <= xEnd; x++) {
                int y = sum - x;
                if (projector.topFaceContains(x, y, map.getLevel(x, y), scratch.x, scratch.y)) {
                    out.set(x, y);
                    return true;
                }
            }
        }
        return false;
    }

    // ------------------------------------------------------------------
    // Desenho
    // ------------------------------------------------------------------

    @Override
    public void render(World world, Cursor cursor) {
        light.set(world.getDayCycle().getLight());

        // O céu é o fundo multiplicado pela luz: sem isso, a noite deixaria o
        // terreno azul-escuro flutuando sobre um fundo com a mesma cor do
        // meio-dia, e o efeito desmonta na hora.
        skyColor.set(BACKGROUND).mul(light.r, light.g, light.b, 1f);
        ScreenUtils.clear(skyColor);

        viewport.apply();
        camera.update();
        shapes.setProjectionMatrix(camera.combined);

        rebuildMasks(world, cursor);
        drawWorld(world);
        drawParticles(world);
        drawCursor(world, cursor);
    }

    /** Rastros a percorrer, tarefas sem dono e o arrasto em andamento. */
    private void rebuildMasks(World world, Cursor cursor) {
        GridMap map = world.getMap();
        int cells = map.getWidth() * map.getHeight();
        if (pathMask == null || pathMask.length != cells) {
            pathMask = new boolean[cells];
            jobMask = new boolean[cells];
            dragMask = new boolean[cells];
        }
        Arrays.fill(pathMask, false);
        Arrays.fill(jobMask, false);
        Arrays.fill(dragMask, false);

        int width = map.getWidth();
        for (Unit unit : world.getUnits()) {
            Array<GridPoint2> path = unit.getPath();
            for (int i = unit.getPathIndex(); i < path.size; i++) {
                GridPoint2 cell = path.get(i);
                pathMask[cell.y * width + cell.x] = true;
            }
        }
        world.getJobBoard().markOpenJobs(jobMask, width);
        for (GridPoint2 cell : cursor.getDragCells()) {
            dragMask[cell.y * width + cell.x] = true;
        }
    }

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
    private void drawWorld(World world) {
        GridMap map = world.getMap();
        int maxSum = (map.getWidth() - 1) + (map.getHeight() - 1);

        for (int sum = maxSum; sum >= 0; sum--) {
            int xStart = Math.max(0, sum - (map.getHeight() - 1));
            int xEnd = Math.min(map.getWidth() - 1, sum);

            shapes.begin(ShapeRenderer.ShapeType.Filled);
            for (int x = xStart; x <= xEnd; x++) {
                drawTile(world, x, sum - x);
            }
            for (Building building : world.getBuildings()) {
                if (building.getX() + building.getY() == sum) {
                    drawBuilding(world, building);
                }
            }
            for (Tree tree : world.getTrees()) {
                if (!tree.isChopped() && tree.getX() + tree.getY() == sum) {
                    drawTree(world, tree);
                }
            }
            for (Unit unit : world.getUnits()) {
                Vector2 pos = unit.getPosition();
                if (Math.round(pos.x + pos.y) == sum) {
                    drawUnit(unit);
                }
            }
            shapes.end();

            if (gridVisible) {
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
    private void drawTile(World world, int gridX, int gridY) {
        GridMap map = world.getMap();
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

        GridPoint2 depot = world.getDepot();
        if (gridX == depot.x && gridY == depot.y) {
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
    private void drawBuilding(World world, Building building) {
        int level = world.getMap().getLevel(building.getX(), building.getY());
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
    private void drawTree(World world, Tree tree) {
        int level = world.getMap().getLevel(tree.getX(), tree.getY());
        projector.gridToWorld(tree.getX(), tree.getY(), level, scratch);

        float baseX = scratch.x;
        float baseY = scratch.y;
        if (tree.isShaking()) {
            baseX += MathUtils.sin(world.getClock() * 38f) * 1.8f;
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

    /**
     * As partículas chegam em coordenadas de simulação e são projetadas aqui —
     * horizontalmente como qualquer tile, verticalmente pelo mesmo passo de
     * elevação que ergue as unidades.
     */
    private void drawParticles(World world) {
        Particles particles = world.getParticles();

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        for (int i = 0; i < particles.getCapacity(); i++) {
            if (!particles.isAlive(i)) {
                continue;
            }
            projector.gridToWorld(particles.getX(i), particles.getZ(i), scratch);
            float px = scratch.x;
            float py = scratch.y + particles.getHeight(i) * ELEVATION_STEP;
            float size = particles.getSize(i) * TILE_WIDTH;

            shade.set(particles.getRed(i) * light.r,
                    particles.getGreen(i) * light.g,
                    particles.getBlue(i) * light.b,
                    particles.getAlpha(i));
            shapes.setColor(shade);
            shapes.rect(px - size * 0.5f, py - size * 0.5f, size, size);
        }
        shapes.end();
    }

    /** O destaque sob o cursor e, no modo construção, o fantasma do prédio. */
    private void drawCursor(World world, Cursor cursor) {
        if (!cursor.isOnMap()) {
            return;
        }
        GridPoint2 cell = cursor.getCell();
        int level = world.getMap().getLevel(cell.x, cell.y);
        projector.topFacePolygon(cell.x, cell.y, level, topFace);

        boolean buildMode = cursor.isBuildMode() && cursor.getBuildType() != null;
        boolean canBuild = buildMode
                && world.checkBuild(cell.x, cell.y) == World.BuildRefusal.NONE;
        Color tint = buildMode ? (canBuild ? GHOST_OK : GHOST_BAD) : HOVER_FILL;

        shapes.begin(ShapeRenderer.ShapeType.Filled);
        shapes.setColor(tint);
        fillDiamond(topFace[0], topFace[1], topFace[2], topFace[3],
                topFace[4], topFace[5], topFace[6], topFace[7]);
        shapes.end();

        shapes.begin(ShapeRenderer.ShapeType.Line);
        shapes.setColor(buildMode ? (canBuild ? GHOST_OK : GHOST_BAD) : HOVER_OUTLINE);
        shapes.polygon(topFace);

        // Fantasma da construção: o contorno da caixa que vai ocupar o tile,
        // para o jogador ver o tamanho antes de gastar madeira nele.
        if (buildMode) {
            float height = cursor.getBuildType().getVisualHeight();
            for (int i = 0; i < 8; i += 2) {
                int j = (i + 2) % 8;
                shapes.line(topFace[i], topFace[i + 1] + height, topFace[j], topFace[j + 1] + height);
                shapes.line(topFace[i], topFace[i + 1], topFace[i], topFace[i + 1] + height);
            }
        }
        shapes.end();
    }

    // ------------------------------------------------------------------
    // Primitivas
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

    @Override
    public void dispose() {
        shapes.dispose();
    }
}
