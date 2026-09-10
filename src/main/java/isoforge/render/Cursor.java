package isoforge.render;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import isoforge.entity.BuildingType;

/**
 * O que o jogador está apontando neste frame.
 *
 * <p>É o único estado de interface que o renderizador precisa receber de fora.
 * Tudo o mais que ele desenha — o terreno, quem anda por ele, o que está sendo
 * construído — ele lê do mundo; isto aqui é a parte que só existe porque há
 * alguém com um mouse na mão.
 *
 * <p>A célula sob o cursor entra e sai por aqui: quem a calcula é o
 * renderizador, em {@link WorldRenderer#pickCell}, porque só ele sabe como o
 * mundo vira tela. O jogo guarda o resultado e o devolve no frame seguinte,
 * junto do que o jogador estiver arrastando ou prestes a construir.
 */
public final class Cursor {

    private final GridPoint2 cell = new GridPoint2();
    private boolean onMap;

    private boolean buildMode;
    private BuildingType buildType;

    private final Array<GridPoint2> dragCells = new Array<>();

    /** A célula sob o cursor. Só significa algo quando {@link #isOnMap()}. */
    public GridPoint2 getCell() {
        return cell;
    }

    public boolean isOnMap() {
        return onMap;
    }

    public void setOnMap(boolean value) {
        onMap = value;
    }

    /** True quando o jogador está prestes a marcar uma construção. */
    public boolean isBuildMode() {
        return buildMode;
    }

    public void setBuildMode(boolean value) {
        buildMode = value;
    }

    /** O prédio que o fantasma sob o cursor representa. Null fora do modo construção. */
    public BuildingType getBuildType() {
        return buildType;
    }

    public void setBuildType(BuildingType type) {
        buildType = type;
    }

    /** Células já tocadas pelo arrasto atual, para o renderizador prevê-las. */
    public Array<GridPoint2> getDragCells() {
        return dragCells;
    }
}
