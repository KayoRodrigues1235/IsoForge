package isoforge;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import isoforge.entity.BuildingType;
import isoforge.entity.Unit;
import isoforge.render.Cursor;
import isoforge.render.IsoShapeRenderer;
import isoforge.render.WorldRenderer;
import isoforge.sim.World;
import isoforge.world.GridMap;

/**
 * O jogo: quem ouve o jogador, decide quanto tempo entregar à simulação e
 * mostra os números.
 *
 * <p>Depois da fase 1 da migração esta classe não é mais "o jogo" no sentido
 * de conter o jogo. O estado vive em {@link World}, que não conhece um pixel; o
 * desenho, a câmera e o <i>picking</i> vivem atrás de {@link WorldRenderer},
 * que existe em duas versões (por ora, uma). Sobrou o meio de campo: traduzir
 * teclas e cliques em comandos, escolher o {@code delta} do frame, e desenhar
 * a interface.
 *
 * <p>Duas decisões que valem ser ditas em voz alta:
 *
 * <ul>
 *   <li><b>Simulação e apresentação têm relógios diferentes.</b> A câmera e o
 *       cursor andam com o tempo real; unidades, obras e o sol andam com o
 *       tempo simulado, que pausa e acelera. Misturar os dois faria a câmera
 *       congelar junto com o jogo.</li>
 *   <li><b>O HUD é desenhado aqui, por fora do renderizador.</b> Ele precisa
 *       sobreviver à troca do desenho do mundo sem ser tocado — e, mais
 *       adiante, ser substituído por Scene2D sem que nenhum renderizador
 *       fique sabendo.</li>
 * </ul>
 */
public class IsoForgeGame extends ApplicationAdapter {

    /** Onde a lenha é entregue e de onde o material de obra sai. */
    private static final int DEPOT_X = 5;
    private static final int DEPOT_Y = 23;

    /** Onde as unidades começam: a oeste da rampa, longe do lago. */
    private static final int[][] UNIT_SPAWNS = {
            {5, 21}, {4, 21}, {6, 21}, {4, 20}, {6, 20}
    };

    /** Sem critério nenhum na escolha — placeholder até o jogador trocar. */
    private static final String[] UNIT_NAMES = {"Tico", "Teco", "Bento", "Duda", "Nico"};

    /** Um bosque a oeste do spawn — grama plana, fora do alcance do lago. */
    private static final int[][] TREE_SPOTS = {
            {2, 18}, {3, 19}, {1, 20}, {2, 21}, {3, 23}, {2, 25},
            {1, 27}, {4, 27}, {6, 26}, {7, 24}
    };

    /** Multiplicadores de velocidade da simulação, ciclados por vírgula/ponto. */
    private static final float[] TIME_SCALES = {1f, 2f, 4f};

    /** Repetido do renderizador só para colorir o painel lateral. */
    private static final Color[] UNIT_COLORS = {
            new Color(0.93f, 0.56f, 0.22f, 1f),
            new Color(0.85f, 0.36f, 0.36f, 1f),
            new Color(0.42f, 0.68f, 0.92f, 1f),
            new Color(0.76f, 0.80f, 0.34f, 1f),
            new Color(0.78f, 0.50f, 0.82f, 1f),
    };

    private World world;
    private WorldRenderer renderer;
    private final Cursor cursor = new Cursor();

    private SpriteBatch batch;
    private BitmapFont font;
    private final Matrix4 hudMatrix = new Matrix4();

    private final Array<GridPoint2> dragCells = new Array<>();
    private boolean dragging;

    private int timeScaleIndex;
    private boolean paused;

    private boolean buildMode;
    private BuildingType selectedType = BuildingType.CABANA;

    private String orderStatus = "clique (ou arraste) para publicar tarefas";

    @Override
    public void create() {
        world = new World(DEPOT_X, DEPOT_Y);
        for (int i = 0; i < UNIT_SPAWNS.length; i++) {
            world.addUnit(UNIT_NAMES[i % UNIT_NAMES.length], UNIT_SPAWNS[i][0], UNIT_SPAWNS[i][1]);
        }
        for (int[] spot : TREE_SPOTS) {
            world.addTree(spot[0], spot[1]);
        }

        renderer = new IsoShapeRenderer();
        // Começa olhando para o centro do mapa em vez do canto (0,0).
        GridMap map = world.getMap();
        renderer.centerOn(map.getWidth() / 2f, map.getHeight() / 2f);

        batch = new SpriteBatch();
        font = new BitmapFont();

        Gdx.gl.glEnable(GL20.GL_BLEND);
        Gdx.input.setInputProcessor(buildInputProcessor());
    }

    private InputAdapter buildInputProcessor() {
        return new InputAdapter() {
            @Override
            public boolean scrolled(float amountX, float amountY) {
                renderer.zoom(amountY);
                return true;
            }

            @Override
            public boolean touchDown(int screenX, int screenY, int pointer, int button) {
                if (!cursor.isOnMap()) {
                    return false;
                }
                GridPoint2 cell = cursor.getCell();
                if (button == Input.Buttons.LEFT) {
                    if (buildMode) {
                        placeBuilding(cell.x, cell.y);
                    } else {
                        beginDrag();
                    }
                    return true;
                }
                if (button == Input.Buttons.RIGHT) {
                    cancelAt(cell.x, cell.y);
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
                renderer.setGridVisible(!renderer.isGridVisible());
                return true;
            case Input.Keys.B:
                buildMode = !buildMode;
                orderStatus = buildMode ? describeBuildMode() : "modo tarefa";
                return true;
            case Input.Keys.TAB:
                selectedType = selectedType.next();
                buildMode = true;
                orderStatus = describeBuildMode();
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
                world.cancelEverything();
                orderStatus = "tudo cancelado";
                return true;
            case Input.Keys.ESCAPE:
                Gdx.app.exit();
                return true;
            default:
                return false;
        }
    }

    private String describeBuildMode() {
        return "modo construcao: " + selectedType.getLabel()
                + " (" + selectedType.getWoodCost() + " madeira)";
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
        dragCells.clear();
        addDragCell();
    }

    private void addDragCell() {
        GridPoint2 hovered = cursor.getCell();
        for (GridPoint2 cell : dragCells) {
            if (cell.x == hovered.x && cell.y == hovered.y) {
                return;
            }
        }
        dragCells.add(new GridPoint2(hovered));
    }

    private void endDrag() {
        dragging = false;
        int posted = 0;
        String lastFailure = null;

        for (GridPoint2 cell : dragCells) {
            if (world.postJobAt(cell.x, cell.y) != null) {
                posted++;
            } else {
                lastFailure = describePostFailure(cell.x, cell.y);
            }
        }
        dragCells.clear();

        if (posted == 0) {
            orderStatus = lastFailure != null ? lastFailure : "nada a publicar";
        } else if (posted == 1) {
            orderStatus = "1 tarefa publicada";
        } else {
            orderStatus = posted + " tarefas publicadas";
        }
        if (!paused) {
            world.runClaimPass();
        }
    }

    private String describePostFailure(int x, int y) {
        // Se há árvore no tile e a publicação falhou, é porque ela já tem dono;
        // fora isso, o tile não é caminhável.
        if (world.standingTreeAt(x, y) != null) {
            return "arvore (" + x + ", " + y + ") ja tem tarefa";
        }
        return "(" + x + ", " + y + ") nao e caminhavel";
    }

    private void placeBuilding(int x, int y) {
        World.BuildRefusal refusal = world.checkBuild(x, y);
        if (refusal != World.BuildRefusal.NONE) {
            orderStatus = describeRefusal(refusal, x, y);
            return;
        }

        world.placeBuilding(x, y, selectedType);
        boolean afford = world.getStockpile().getAvailableWood() >= selectedType.getWoodCost();
        orderStatus = selectedType.getLabel() + " marcada em (" + x + ", " + y + ")"
                + (afford ? "" : " — aguardando madeira");
        if (!paused) {
            world.runClaimPass();
        }
    }

    /** Traduz a recusa do mundo em texto para o jogador. */
    private String describeRefusal(World.BuildRefusal refusal, int x, int y) {
        switch (refusal) {
            case OUT_OF_MAP:
                return "fora do mapa";
            case TERRAIN:
                return "nao da para construir em " + world.getMap().get(x, y);
            case OCCUPIED:
                return "ja tem construcao em (" + x + ", " + y + ")";
            case DEPOT:
                return "o deposito ocupa esse tile";
            case TREE:
                return "tem arvore em (" + x + ", " + y + ") — corte primeiro";
            case SITE:
                return "ja tem obra marcada ai";
            default:
                return "";
        }
    }

    /**
     * Cancela a tarefa publicada naquele tile — e só ela. O cancelamento em
     * massa continua existindo no {@code X}, mas ele deixou de ser a única
     * saída: desfazer um clique errado não deveria custar a fila inteira.
     */
    private void cancelAt(int x, int y) {
        orderStatus = world.cancelAt(x, y)
                ? "tarefa em (" + x + ", " + y + ") cancelada"
                : "nenhuma tarefa em (" + x + ", " + y + ")";
    }

    // ------------------------------------------------------------------
    // Loop
    // ------------------------------------------------------------------

    @Override
    public void render() {
        float realDelta = Gdx.graphics.getDeltaTime();
        float simDelta = paused ? 0f : realDelta * TIME_SCALES[timeScaleIndex];

        handlePan(realDelta);
        updateCursor();

        if (simDelta > 0f) {
            world.update(simDelta);
        }

        renderer.render(world, cursor);
        drawHud();
    }

    private void handlePan(float delta) {
        float dirX = 0f;
        float dirY = 0f;
        if (Gdx.input.isKeyPressed(Input.Keys.W) || Gdx.input.isKeyPressed(Input.Keys.UP)) {
            dirY += 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.S) || Gdx.input.isKeyPressed(Input.Keys.DOWN)) {
            dirY -= 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.A) || Gdx.input.isKeyPressed(Input.Keys.LEFT)) {
            dirX -= 1f;
        }
        if (Gdx.input.isKeyPressed(Input.Keys.D) || Gdx.input.isKeyPressed(Input.Keys.RIGHT)) {
            dirX += 1f;
        }
        if (dirX != 0f || dirY != 0f) {
            renderer.pan(dirX, dirY, delta);
        }
    }

    /**
     * Pergunta ao renderizador que célula está sob o mouse e passa adiante o
     * que o jogador está prestes a fazer com ela. É o único caminho por onde a
     * tela vira grid — o jogo não sabe fazer essa conta, e não deve.
     */
    private void updateCursor() {
        cursor.setOnMap(renderer.pickCell(world, Gdx.input.getX(), Gdx.input.getY(),
                cursor.getCell()));
        cursor.setBuildMode(buildMode);
        cursor.setBuildType(buildMode ? selectedType : null);

        if (dragging && cursor.isOnMap()) {
            addDragCell();
        }
        cursor.getDragCells().clear();
        cursor.getDragCells().addAll(dragCells);
    }

    // ------------------------------------------------------------------
    // Interface
    // ------------------------------------------------------------------

    private void drawHud() {
        GridMap map = world.getMap();
        GridPoint2 cell = cursor.getCell();
        String cellText = cursor.isOnMap()
                ? cell.x + ", " + cell.y
                        + "  nivel " + map.getLevel(cell.x, cell.y)
                        + "  (" + map.get(cell.x, cell.y) + ")"
                : "fora do mapa";

        batch.setProjectionMatrix(hudMatrix);
        batch.begin();
        float top = Gdx.graphics.getHeight() - 10f;
        font.draw(batch, "Tile: " + cellText, 12f, top);
        font.draw(batch, "Unidades: " + world.getUnits().size
                + "   ociosas: " + world.getIdleUnitCount(), 12f, top - 20f);

        int starved = world.getJobBoard().getStarvedCount();
        font.draw(batch, "Tarefas: " + world.getJobBoard().getTotalCount()
                + "   sem dono: " + world.getJobBoard().getOpenCount()
                + (starved > 0 ? "   sem madeira: " + starved : ""), 12f, top - 40f);

        font.draw(batch, "Madeira: " + world.getStockpile().getWood()
                + (world.getStockpile().getReservedWood() > 0
                        ? " (" + world.getStockpile().getReservedWood() + " reservada)" : "")
                + "   Construcoes: " + world.getBuildings().size, 12f, top - 60f);

        font.draw(batch, "Hora: " + world.getDayCycle().getClockLabel()
                + "   Tempo: " + (paused ? "pausado" : (int) TIME_SCALES[timeScaleIndex] + "x")
                + "   Modo: " + (buildMode ? "construir " + selectedType.getLabel()
                        + " (" + selectedType.getWoodCost() + " mad.)" : "tarefas"),
                12f, top - 80f);

        font.draw(batch, "Ordem: " + orderStatus, 12f, top - 100f);
        font.draw(batch, String.format("Render: %s   Zoom: %.2f   FPS: %d",
                renderer.getName(), renderer.getZoom(),
                Gdx.graphics.getFramesPerSecond()), 12f, top - 120f);

        font.draw(batch, "Esq: publicar (arraste p/ varias)   Dir: cancelar tarefa do tile   "
                + "X: cancelar tudo   B: construir   TAB: trocar predio", 12f, top - 145f);
        font.draw(batch, "SPACE: pausar   , .: velocidade   WASD: camera   "
                + "Scroll: zoom   G: grade   ESC: sair", 12f, top - 163f);

        drawUnitPanel(top);
        batch.end();
    }

    /** Tabela à direita: uma linha por unidade, nome e o que está fazendo agora. */
    private void drawUnitPanel(float top) {
        float panelX = Gdx.graphics.getWidth() - 200f;
        Array<Unit> units = world.getUnits();
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
        renderer.resize(width, height);
        hudMatrix.setToOrtho2D(0f, 0f, width, height);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        batch.dispose();
        font.dispose();
    }
}
