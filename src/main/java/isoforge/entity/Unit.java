package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import isoforge.world.GridMap;

/**
 * Um personagem que pega tarefas do {@link JobBoard} e as executa.
 *
 * <p>A posição é guardada em <b>coordenadas de grid contínuas</b>: (3.4, 7.8)
 * significa "entre o tile 3 e o 4, na coluna 7-8". Isso mantém o movimento
 * independente da projeção — a unidade não sabe que o mundo é isométrico, só
 * anda no grid, e a conversão para pixels é problema do renderizador. É a
 * mesma separação que deixaria trocar o desenho por malhas 3D sem tocar aqui.
 *
 * <p><b>Unidades não se bloqueiam.</b> Elas se atravessam, e o desenho aplica
 * um pequeno deslocamento para não ficarem exatamente sobrepostas. A escolha
 * não é preguiça: o único acesso ao platô é uma rampa de 3 tiles, e colisão
 * dura num gargalo desses produz fila, unidade parada em cima do caminho e
 * deadlock — o problema em que o Castle Story penou anos. Sem bloqueio, o
 * caminho calculado uma vez continua válido até o fim, e o A* pode seguir
 * estático e barato.
 *
 * <p>O nível visual é interpolado ao longo de cada trecho. Sem isso a unidade
 * "teleporta" verticalmente ao pisar na rampa, um degrau inteiro de uma vez.
 */
public final class Unit {

    /** Tiles por segundo. */
    private static final float SPEED = 3.2f;

    private final int id;
    private final Vector2 position = new Vector2();
    private final Array<GridPoint2> path = new Array<>();
    private int pathIndex;

    private Job currentJob;

    /** Níveis das pontas do trecho atual, usados só para interpolar o desenho. */
    private int segmentFromLevel;
    private int segmentToLevel;
    private float visualLevel;

    public Unit(int id, int gridX, int gridY, GridMap map) {
        this.id = id;
        position.set(gridX, gridY);
        int level = map.getLevel(gridX, gridY);
        segmentFromLevel = level;
        segmentToLevel = level;
        visualLevel = level;
    }

    /** Sem tarefa e sem caminho: pronta para pegar trabalho. */
    public boolean isIdle() {
        return currentJob == null && !isMoving();
    }

    /** Recebe uma tarefa do quadro junto do caminho já calculado. */
    public void assign(Job job, Array<GridPoint2> jobPath, GridMap map) {
        currentJob = job;
        setPath(jobPath, map);
    }

    /**
     * Marca a tarefa atual como concluída. O jogo chama isto quando a unidade
     * para de andar tendo trabalho em mãos — no M1.5, chegar ao destino <i>é</i>
     * concluir. No M2, chegar até a árvore será só o começo da tarefa.
     */
    public void finishJob() {
        if (currentJob != null) {
            currentJob.complete();
            currentJob = null;
        }
    }

    /** Abandona tarefa e caminho. Usado quando o jogador cancela tudo. */
    public void stop() {
        if (currentJob != null) {
            currentJob.complete();
            currentJob = null;
        }
        path.clear();
        pathIndex = 0;
    }

    /** Substitui a rota atual. O caminho vem do A*, sem a célula de partida. */
    public void setPath(Array<GridPoint2> newPath, GridMap map) {
        path.clear();
        path.addAll(newPath);
        pathIndex = 0;
        if (path.size > 0) {
            beginSegment(map);
        }
    }

    private void beginSegment(GridMap map) {
        int cx = Math.round(position.x);
        int cy = Math.round(position.y);
        segmentFromLevel = map.getLevel(cx, cy);
        GridPoint2 target = path.get(pathIndex);
        segmentToLevel = map.getLevel(target.x, target.y);
    }

    public void update(float delta, GridMap map) {
        if (pathIndex >= path.size) {
            return;
        }

        GridPoint2 target = path.get(pathIndex);
        float dx = target.x - position.x;
        float dy = target.y - position.y;
        float remaining = (float) Math.sqrt(dx * dx + dy * dy);
        float step = SPEED * delta;

        if (remaining <= step || remaining < 1e-4f) {
            position.set(target.x, target.y);
            visualLevel = segmentToLevel;
            pathIndex++;
            if (pathIndex < path.size) {
                beginSegment(map);
            } else {
                path.clear();
                pathIndex = 0;
            }
            return;
        }

        position.add(dx / remaining * step, dy / remaining * step);

        // Trechos ligam células vizinhas, então o comprimento é sempre 1 e o
        // progresso é o complemento do que falta.
        float progress = MathUtils.clamp(1f - (remaining - step), 0f, 1f);
        visualLevel = MathUtils.lerp(segmentFromLevel, segmentToLevel, progress);
    }

    /** Célula em que a unidade está (a mais próxima, se estiver entre duas). */
    public GridPoint2 getCell(GridPoint2 out) {
        return out.set(Math.round(position.x), Math.round(position.y));
    }

    public int getId() {
        return id;
    }

    public Vector2 getPosition() {
        return position;
    }

    public float getVisualLevel() {
        return visualLevel;
    }

    public boolean isMoving() {
        return pathIndex < path.size;
    }

    public Job getCurrentJob() {
        return currentJob;
    }

    public Array<GridPoint2> getPath() {
        return path;
    }

    public int getPathIndex() {
        return pathIndex;
    }
}
