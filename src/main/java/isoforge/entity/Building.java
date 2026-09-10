package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;
import isoforge.world.GridMap;

/**
 * Uma construção no mapa, do canteiro vazio até o prédio pronto.
 *
 * <p>Ela nasce como <b>canteiro</b> no instante do clique — antes de existir
 * madeira, unidade ou caminho. É de propósito: o jogador precisa ver onde
 * mandou construir enquanto a colônia ainda está juntando o material, senão a
 * ordem some da tela e ele manda de novo.
 *
 * <p><b>Só a construção pronta bloqueia o tile.</b> Durante a obra a unidade
 * precisa estar em cima dele para trabalhar; se o canteiro já bloqueasse, a
 * própria tarefa ficaria sem destino alcançável. Uma vez pronta, ela vira
 * obstáculo de verdade para o A* — e sim, dá para se murar sozinho com isso.
 * Isso é mecânica, não bug: é o mesmo relevo do mapa, só que construído.
 */
public final class Building {

    private final int id;
    private final GridPoint2 cell = new GridPoint2();
    private final BuildingType type;

    /** 0 = canteiro marcado, 1 = pronta. Avança durante a fase de trabalho. */
    private float progress;
    private boolean complete;
    private boolean cancelled;
    private boolean justCompleted;

    public Building(int id, int x, int y, BuildingType type) {
        this.id = id;
        this.cell.set(x, y);
        this.type = type;
    }

    public int getId() {
        return id;
    }

    public int getX() {
        return cell.x;
    }

    public int getY() {
        return cell.y;
    }

    public BuildingType getType() {
        return type;
    }

    public float getProgress() {
        return progress;
    }

    public boolean isComplete() {
        return complete;
    }

    public boolean isCancelled() {
        return cancelled;
    }

    void setProgress(float value) {
        progress = value < 0f ? 0f : Math.min(value, 1f);
    }

    /** Obra concluída: o tile passa a ser intransponível a partir de agora. */
    void finish(GridMap map) {
        progress = 1f;
        complete = true;
        justCompleted = true;
        map.setBlocked(cell.x, cell.y, true);
    }

    void cancel() {
        cancelled = true;
    }

    /**
     * Consome o aviso de "acabou de ficar pronta". Existe para o jogo disparar
     * efeitos (poeira, som) no frame certo sem a construção precisar conhecer
     * quem desenha.
     */
    public boolean pollJustCompleted() {
        boolean value = justCompleted;
        justCompleted = false;
        return value;
    }
}
