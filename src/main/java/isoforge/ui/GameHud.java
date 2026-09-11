package isoforge.ui;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.scenes.scene2d.ui.ButtonGroup;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Disposable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import isoforge.assets.Assets;
import isoforge.entity.BuildingType;
import isoforge.entity.Unit;
import isoforge.render.Cursor;
import isoforge.sim.World;
import isoforge.world.GridMap;

/**
 * A interface do jogo: recursos, controles de tempo, paleta de construção e o
 * painel de unidades.
 *
 * <p>Substitui as oito linhas de texto de depuração que o jogo tinha no canto.
 * A diferença que importa não é estética: <b>todo comando que existia só como
 * tecla escondida agora tem um botão</b>. Antes era preciso saber que TAB troca
 * o prédio e que vírgula desacelera o tempo; o teclado continua funcionando,
 * mas deixou de ser a única porta. Um jogo cuja única documentação é uma linha
 * de ajuda no rodapé não é um jogo, é um protótipo com legenda.
 *
 * <p>O texto de depuração não sumiu — mudou de status. Ele vive num painel
 * ligado por {@code F3}, que é onde esse tipo de informação pertence.
 *
 * <p><b>O HUD é desenhado por fora de qualquer renderizador.</b> Ele não sabe se
 * o mundo atrás dele está sendo desenhado em geometria chapada ou em malhas 3D,
 * e não deve saber: quando o renderizador 3D existir, nada aqui é tocado.
 */
public final class GameHud implements Disposable {

    private static final float PANEL_PAD = 10f;
    private static final float EDGE_PAD = 12f;

    private final HudActions actions;
    private final Skin skin;
    private final Stage stage;

    private final Vector2 pointerScratch = new Vector2();

    // Recursos
    private final Label woodValue;
    private final Label woodNote;
    private final Label unitsValue;
    private final Label jobsValue;
    private final Label jobsNote;

    // Tempo
    private final Label clockLabel;
    private final TextButton pauseButton;
    private final Array<TextButton> speedButtons = new Array<>();

    // Construção
    private final TextButton taskButton;
    private final Array<TextButton> buildButtons = new Array<>();

    private final Label statusLabel;
    private final Table statusRow;

    // Unidades
    private final Table unitTable;
    private final Array<Label> unitRows = new Array<>();

    // Depuração
    private final Table debugPanel;
    private final Label debugLabel;

    public GameHud(HudActions actions, Assets assets) {
        this.actions = actions;
        this.skin = ProceduralSkin.build(assets);
        this.stage = new Stage(new ScreenViewport());

        woodValue = new Label("0", skin, "title");
        woodValue.setColor(ProceduralSkin.TEXT_ACCENT);
        woodNote = new Label("", skin, "caption");
        unitsValue = new Label("0", skin, "title");
        jobsValue = new Label("0", skin, "title");
        jobsNote = new Label("", skin, "caption");

        clockLabel = new Label("00:00", skin, "title");
        statusLabel = new Label("", skin, "default");
        statusLabel.setColor(ProceduralSkin.TEXT_MUTED);
        // A mensagem fica sobre o mundo, então precisa do mesmo fundo dos
        // painéis: texto claro sobre grama clara é ilegível na hora errada.
        statusRow = new Table();
        statusRow.add(statusLabel).left();

        unitTable = new Table();
        debugLabel = new Label("", skin, "caption");
        debugPanel = new Table();

        pauseButton = new TextButton("Pausa", skin);
        taskButton = new TextButton("Tarefas", skin);

        Table root = new Table();
        root.setFillParent(true);
        root.pad(EDGE_PAD);
        stage.addActor(root);

        Table left = new Table();
        Table right = new Table();

        // Sem isto, os três contêineres de layout responderiam ao teste de
        // acerto em qualquer ponto da tela — eles ocupam a janela inteira — e
        // o jogo acharia que o ponteiro está sempre sobre a interface. Só os
        // painéis com fundo é que representam superfície de fato.
        root.setTouchable(Touchable.childrenOnly);
        left.setTouchable(Touchable.childrenOnly);
        right.setTouchable(Touchable.childrenOnly);

        // Alinhamento no nível da tabela, e não só da célula. Uma Table do
        // Scene2D centraliza o bloco de conteúdo quando é maior que ele e
        // nenhuma coluna expande — e cell.left() alinha o ator *dentro da
        // célula*, não a coluna dentro da tabela. Sem estas três linhas o HUD
        // inteiro flutua no meio da janela.
        root.top();
        left.top().left();
        right.top().right();

        left.add(buildResourcePanel()).left().row();
        left.add(debugPanel).left().padTop(8f).row();
        left.add().growY().row();
        left.add(buildBuildPanel()).left().row();
        left.add(wrapPanel(statusRow)).left().padTop(8f).row();

        right.add(buildTimePanel()).right().row();
        right.add(wrapPanel(unitTable)).right().padTop(8f).minWidth(190f).row();
        right.add().growY().row();

        root.add(left).grow().top().left();
        root.add(right).top().right();

        buildDebugPanel();
        debugPanel.setVisible(false);
    }

    /** Envolve um conteúdo no fundo de painel padrão. */
    private Table wrapPanel(Table content) {
        Table panel = new Table();
        panel.setBackground(skin.getDrawable("panel"));
        panel.pad(PANEL_PAD);
        panel.add(content).grow();
        return panel;
    }

    /**
     * Um número grande com uma legenda em cima. É a única parte da interface
     * que usa a fonte grande — o estoque é o número que decide se a próxima
     * obra sai do papel, então merece ser lido de relance.
     */
    private Table statTile(String caption, Label value, Label note) {
        Table tile = new Table();
        Label captionLabel = new Label(caption, skin, "caption");
        tile.add(captionLabel).left().row();
        tile.add(value).left().padTop(-2f).row();
        if (note != null) {
            tile.add(note).left().row();
        }
        return tile;
    }

    private Table buildResourcePanel() {
        Table content = new Table();
        content.add(statTile("MADEIRA", woodValue, woodNote)).left().padRight(24f);
        content.add(statTile("OCIOSAS", unitsValue, null)).left().padRight(24f);
        content.add(statTile("TAREFAS", jobsValue, jobsNote)).left();
        return wrapPanel(content);
    }

    private Table buildTimePanel() {
        Table content = new Table();
        content.add(clockLabel).right().colspan(4).padBottom(6f).row();

        pauseButton.setProgrammaticChangeEvents(false);
        pauseButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actions.setPaused(pauseButton.isChecked());
            }
        });
        content.add(pauseButton).minWidth(72f).padRight(6f);

        // Um botão por multiplicador, num grupo de escolha única: a velocidade
        // corrente fica visível sem o jogador precisar contar quantas vezes
        // apertou o ponto.
        ButtonGroup<TextButton> speeds = new ButtonGroup<>();
        speeds.setMinCheckCount(1);
        speeds.setMaxCheckCount(1);
        float[] scales = actions.getTimeScales();
        for (int i = 0; i < scales.length; i++) {
            final int index = i;
            TextButton button = new TextButton((int) scales[i] + "x", skin);
            button.setProgrammaticChangeEvents(false);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    actions.setTimeScaleIndex(index);
                }
            });
            speeds.add(button);
            speedButtons.add(button);
            content.add(button).minWidth(44f).padRight(i < scales.length - 1 ? 4f : 0f);
        }
        return wrapPanel(content);
    }

    private Table buildBuildPanel() {
        Table content = new Table();
        content.add(new Label("O QUE O CLIQUE FAZ", skin, "caption")).left().colspan(8).padBottom(6f).row();

        ButtonGroup<TextButton> modes = new ButtonGroup<>();
        modes.setMinCheckCount(1);
        modes.setMaxCheckCount(1);

        taskButton.setProgrammaticChangeEvents(false);
        taskButton.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actions.selectTaskMode();
            }
        });
        modes.add(taskButton);
        content.add(taskButton).minWidth(88f).padRight(6f);

        for (BuildingType type : BuildingType.values()) {
            final BuildingType selected = type;
            TextButton button = new TextButton(
                    type.getLabel() + "  " + type.getWoodCost(), skin);
            button.setProgrammaticChangeEvents(false);
            button.addListener(new ChangeListener() {
                @Override
                public void changed(ChangeEvent event, Actor actor) {
                    actions.selectBuilding(selected);
                }
            });
            modes.add(button);
            buildButtons.add(button);
            content.add(button).minWidth(96f).padRight(6f);
        }

        TextButton cancelAll = new TextButton("Cancelar tudo", skin);
        cancelAll.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent event, Actor actor) {
                actions.cancelAllJobs();
            }
        });
        content.add(cancelAll).minWidth(110f).padLeft(14f);

        return wrapPanel(content);
    }

    private void buildDebugPanel() {
        debugPanel.setBackground(skin.getDrawable("panel"));
        debugPanel.pad(PANEL_PAD);
        debugPanel.add(debugLabel).left();
    }

    // ------------------------------------------------------------------

    /**
     * Sincroniza os widgets com o estado do jogo.
     *
     * <p>Os botões são atualizados com os eventos programáticos desligados. Sem
     * isso, marcar um botão daqui dispararia o ouvinte dele, que mandaria o
     * jogo trocar de modo — que é o que acabou de acontecer. O laço fecharia em
     * si mesmo e uma troca pelo teclado viraria duas.
     */
    public void update(World world, Cursor cursor) {
        int wood = world.getStockpile().getWood();
        int reserved = world.getStockpile().getReservedWood();
        woodValue.setText(Integer.toString(wood));
        woodNote.setText(reserved > 0 ? reserved + " reservada" : "");

        int idle = world.getIdleUnitCount();
        int total = world.getUnits().size;
        unitsValue.setText(idle + "/" + total);
        unitsValue.setColor(idle == 0 ? ProceduralSkin.TEXT_MUTED : ProceduralSkin.TEXT);

        int open = world.getJobBoard().getOpenCount();
        int starved = world.getJobBoard().getStarvedCount();
        jobsValue.setText(Integer.toString(world.getJobBoard().getTotalCount()));
        jobsNote.setText(starved > 0
                ? starved + " sem madeira"
                : (open > 0 ? open + " sem dono" : ""));
        jobsNote.setColor(starved > 0 ? ProceduralSkin.TEXT_WARN : ProceduralSkin.TEXT_MUTED);

        clockLabel.setText(world.getDayCycle().getClockLabel());
        clockLabel.setColor(world.getDayCycle().isDaylight()
                ? ProceduralSkin.TEXT : ProceduralSkin.TEXT_MUTED);

        pauseButton.setChecked(actions.isPaused());
        int scaleIndex = actions.getTimeScaleIndex();
        for (int i = 0; i < speedButtons.size; i++) {
            speedButtons.get(i).setChecked(i == scaleIndex);
        }

        BuildingType selected = actions.getSelectedBuilding();
        taskButton.setChecked(selected == null);
        BuildingType[] types = BuildingType.values();
        for (int i = 0; i < buildButtons.size; i++) {
            buildButtons.get(i).setChecked(types[i] == selected);
        }

        statusLabel.setText(actions.getStatusMessage());
        refreshUnitRows(world);

        if (debugPanel.isVisible()) {
            debugLabel.setText(describeDebug(world, cursor));
        }
    }

    /**
     * Uma linha por unidade, na cor dela no mundo. As linhas só são recriadas
     * quando o número de unidades muda — reconstruir a tabela a cada frame
     * jogaria fora o layout e o trabalho de medição sessenta vezes por segundo.
     */
    private void refreshUnitRows(World world) {
        Array<Unit> units = world.getUnits();
        if (unitRows.size != units.size) {
            unitTable.clear();
            unitRows.clear();
            unitTable.add(new Label("UNIDADES", skin, "caption")).left().padBottom(4f).row();
            for (int i = 0; i < units.size; i++) {
                Label row = new Label("", skin, "default");
                unitRows.add(row);
                unitTable.add(row).left().row();
            }
        }
        for (int i = 0; i < units.size; i++) {
            Unit unit = units.get(i);
            unitRows.get(i).setText(unit.getName() + " · " + unit.describeState());
            unitRows.get(i).setColor(unitColor(unit.getId()));
        }
    }

    /**
     * As mesmas cores que o renderizador dá às unidades. Está repetido de
     * propósito: o HUD não conhece nenhum renderizador, e ler a cor de lá o
     * amarraria ao desenho 2.5D. Um dia isso vira uma propriedade da unidade.
     */
    private Color unitColor(int id) {
        switch (id % 5) {
            case 0: return new Color(0.93f, 0.56f, 0.22f, 1f);
            case 1: return new Color(0.85f, 0.36f, 0.36f, 1f);
            case 2: return new Color(0.42f, 0.68f, 0.92f, 1f);
            case 3: return new Color(0.76f, 0.80f, 0.34f, 1f);
            default: return new Color(0.78f, 0.50f, 0.82f, 1f);
        }
    }

    private String describeDebug(World world, Cursor cursor) {
        GridMap map = world.getMap();
        GridPoint2 cell = cursor.getCell();
        String tile = cursor.isOnMap()
                ? cell.x + ", " + cell.y + "  nível " + map.getLevel(cell.x, cell.y)
                        + "  " + map.get(cell.x, cell.y)
                        + (map.isBlocked(cell.x, cell.y) ? "  [ocupado]" : "")
                : "fora do mapa";

        return "Tile: " + tile
                + "\nRender: " + actions.getRendererName()
                + String.format("   Zoom: %.2f   FPS: %d",
                        actions.getZoom(), Gdx.graphics.getFramesPerSecond())
                + "\nArraste: publicar várias   Direito: cancelar a tarefa do tile"
                + "\nB / TAB: construir   Espaço: pausar   , .: velocidade"
                + "\nWASD: câmera   Scroll: zoom   G: grade   F3: este painel";
    }

    public void toggleDebug() {
        debugPanel.setVisible(!debugPanel.isVisible());
    }

    public boolean isDebugVisible() {
        return debugPanel.isVisible();
    }

    /**
     * True quando o ponteiro está sobre um painel da interface.
     *
     * <p>O multiplexador já impede que um clique num botão vire também um
     * clique no mundo, mas o destaque sob o cursor é calculado todo frame, à
     * revelia do input — sem esta consulta, o tile atrás do painel ficaria
     * aceso enquanto o jogador mira num botão.
     */
    public boolean isPointerOverUi(int screenX, int screenY) {
        pointerScratch.set(screenX, screenY);
        stage.screenToStageCoordinates(pointerScratch);
        return stage.hit(pointerScratch.x, pointerScratch.y, true) != null;
    }

    /** Deve receber input antes do jogo, para os botões terem prioridade. */
    public Stage getStage() {
        return stage;
    }

    public void draw(float realDelta) {
        // O renderizador do mundo acabou de aplicar o viewport dele. A interface
        // tem o seu próprio e precisa reivindicá-lo antes de desenhar, senão
        // herda a câmera de quem desenhou por último.
        stage.getViewport().apply();
        stage.act(realDelta);
        stage.draw();
    }

    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        stage.dispose();
        // A skin descarta as texturas que ela mesma gerou. As fontes são do
        // AssetManager e não passam por aqui.
        skin.dispose();
    }
}
