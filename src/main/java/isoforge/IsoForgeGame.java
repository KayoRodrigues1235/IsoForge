package isoforge;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputAdapter;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import isoforge.entity.BuildingType;
import isoforge.render.Cursor;
import isoforge.render.IsoShapeRenderer;
import isoforge.render.WorldRenderer;
import isoforge.sim.World;
import isoforge.ui.GameHud;
import isoforge.ui.HudActions;
import isoforge.world.GridMap;

/**
 * O jogo: quem ouve o jogador, decide quanto tempo entregar à simulação e liga
 * as três camadas que fazem o resto.
 *
 * <p>Não é mais "o jogo" no sentido de conter o jogo. O estado vive em {@link
 * World}, que não conhece um pixel; o desenho, a câmera e o <i>picking</i>
 * vivem atrás de {@link WorldRenderer}; a interface vive em {@link GameHud} e
 * fala com esta classe pelo contrato {@link HudActions}. Sobrou o meio de
 * campo — traduzir teclas e cliques em comandos, e escolher o {@code delta} do
 * frame.
 *
 * <p>Duas decisões que valem ser ditas em voz alta:
 *
 * <ul>
 *   <li><b>Simulação e apresentação têm relógios diferentes.</b> A câmera, o
 *       cursor e as animações da interface andam com o tempo real; unidades,
 *       obras e o sol andam com o tempo simulado, que pausa e acelera.
 *       Misturar os dois faria a câmera congelar junto com o jogo.</li>
 *   <li><b>Teclado e botões são a mesma porta.</b> Toda tecla chama exatamente
 *       o mesmo método que o botão correspondente do HUD. Se fossem caminhos
 *       separados, um deles ficaria para trás na primeira mudança de regra.</li>
 * </ul>
 */
public class IsoForgeGame extends ApplicationAdapter implements HudActions {

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

    /** Multiplicadores de velocidade da simulação. */
    private static final float[] TIME_SCALES = {1f, 2f, 4f};

    private World world;
    private WorldRenderer renderer;
    private GameHud hud;
    private final Cursor cursor = new Cursor();

    private final Array<GridPoint2> dragCells = new Array<>();
    private boolean dragging;

    private int timeScaleIndex;
    private boolean paused;

    private boolean buildMode;
    private BuildingType selectedType = BuildingType.CABANA;

    private String orderStatus = "clique ou arraste para publicar tarefas";

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

        hud = new GameHud(this);

        Gdx.gl.glEnable(GL20.GL_BLEND);
        // A interface ouve primeiro: um clique num botão não pode virar também
        // um clique no mundo atrás dele.
        Gdx.input.setInputProcessor(new InputMultiplexer(hud.getStage(), worldInput()));
    }

    private InputAdapter worldInput() {
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

    /**
     * Atalhos de teclado. Cada um chama o mesmo método que o botão equivalente
     * do HUD — o teclado é um caminho mais rápido para o mesmo comando, não um
     * comando paralelo.
     */
    private boolean handleKey(int keycode) {
        switch (keycode) {
            case Input.Keys.G:
                renderer.setGridVisible(!renderer.isGridVisible());
                return true;
            case Input.Keys.F3:
                hud.toggleDebug();
                return true;
            case Input.Keys.B:
                if (buildMode) {
                    selectTaskMode();
                } else {
                    selectBuilding(selectedType);
                }
                return true;
            case Input.Keys.TAB:
                selectBuilding(buildMode ? selectedType.next() : selectedType);
                return true;
            case Input.Keys.SPACE:
                setPaused(!paused);
                return true;
            case Input.Keys.PERIOD:
                setTimeScaleIndex(timeScaleIndex + 1);
                return true;
            case Input.Keys.COMMA:
                setTimeScaleIndex(timeScaleIndex - 1);
                return true;
            case Input.Keys.X:
                cancelAllJobs();
                return true;
            case Input.Keys.ESCAPE:
                Gdx.app.exit();
                return true;
            default:
                return false;
        }
    }

    // ------------------------------------------------------------------
    // HudActions — os comandos, vindos de botão ou de tecla
    // ------------------------------------------------------------------

    @Override
    public void selectTaskMode() {
        buildMode = false;
        orderStatus = "clique ou arraste para publicar tarefas";
    }

    @Override
    public void selectBuilding(BuildingType type) {
        buildMode = true;
        selectedType = type;
        orderStatus = "clique para marcar " + type.getLabel().toLowerCase()
                + " (" + type.getWoodCost() + " madeira)";
    }

    @Override
    public void setPaused(boolean value) {
        paused = value;
        orderStatus = paused ? "pausado" : "retomado";
    }

    @Override
    public void setTimeScaleIndex(int index) {
        timeScaleIndex = Math.max(0, Math.min(index, TIME_SCALES.length - 1));
    }

    @Override
    public void cancelAllJobs() {
        world.cancelEverything();
        orderStatus = "tudo cancelado";
    }

    @Override
    public boolean isPaused() {
        return paused;
    }

    @Override
    public int getTimeScaleIndex() {
        return timeScaleIndex;
    }

    @Override
    public float[] getTimeScales() {
        return TIME_SCALES.clone();
    }

    @Override
    public BuildingType getSelectedBuilding() {
        return buildMode ? selectedType : null;
    }

    @Override
    public String getStatusMessage() {
        return orderStatus;
    }

    @Override
    public String getRendererName() {
        return renderer.getName();
    }

    @Override
    public float getZoom() {
        return renderer.getZoom();
    }

    // ------------------------------------------------------------------
    // Comandos sobre o mapa
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
            return "a arvore em (" + x + ", " + y + ") ja tem tarefa";
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
     * massa continua existindo, mas ele deixou de ser a única saída: desfazer
     * um clique errado não deveria custar a fila inteira.
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
        hud.update(world, cursor);
        hud.draw(realDelta);
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
     *
     * <p>Com o ponteiro sobre um painel, não há célula alguma: o destaque é
     * calculado todo frame, à revelia do input, e sem esta guarda o tile atrás
     * da interface ficaria aceso enquanto o jogador mira num botão.
     */
    private void updateCursor() {
        int pointerX = Gdx.input.getX();
        int pointerY = Gdx.input.getY();

        boolean overUi = hud.isPointerOverUi(pointerX, pointerY);
        cursor.setOnMap(!overUi
                && renderer.pickCell(world, pointerX, pointerY, cursor.getCell()));

        cursor.setBuildMode(buildMode);
        cursor.setBuildType(buildMode ? selectedType : null);

        if (dragging && cursor.isOnMap()) {
            addDragCell();
        }
        cursor.getDragCells().clear();
        cursor.getDragCells().addAll(dragCells);
    }

    @Override
    public void resize(int width, int height) {
        renderer.resize(width, height);
        hud.resize(width, height);
    }

    @Override
    public void dispose() {
        renderer.dispose();
        hud.dispose();
    }
}
