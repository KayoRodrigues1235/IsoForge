package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.Array;
import isoforge.world.GridMap;
import isoforge.world.PathFinder;

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
 *
 * <p><b>Chegar quase nunca termina a tarefa.</b> Só a {@code MOVE} acaba ao
 * chegar; nas outras, cada chegada é o fim de uma fase e o começo da próxima,
 * que pode exigir um caminho novo (da árvore ao depósito, do depósito à obra).
 * Por isso o {@link PathFinder} entra em {@link #update}: a unidade se rota
 * sozinha entre as fases, sem o jogo precisar perguntar a cada frame quem
 * terminou o quê.
 *
 * <p>Uma consequência sutil e útil: quando a tarefa não tem caminho para a
 * fase seguinte (o jogador muraram o canteiro, por exemplo), a unidade
 * <b>aborta</b> em vez de ficar presa. Tarefa impossível volta a ser tarefa
 * inexistente, e o material reservado é devolvido.
 */
public final class Unit {

    /** Tiles por segundo. */
    private static final float SPEED = 3.2f;

    private final int id;
    private final String name;
    private final Vector2 position = new Vector2();
    private final Array<GridPoint2> path = new Array<>();
    private final Array<GridPoint2> routeScratch = new Array<>();
    private final GridPoint2 cellScratch = new GridPoint2();
    private int pathIndex;

    private Job currentJob;

    /** Níveis das pontas do trecho atual, usados só para interpolar o desenho. */
    private int segmentFromLevel;
    private int segmentToLevel;
    private float visualLevel;

    public Unit(int id, String name, int gridX, int gridY, GridMap map) {
        this.id = id;
        this.name = name;
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

    /** Marca a tarefa atual como concluída e solta a referência. */
    public void finishJob() {
        if (currentJob != null) {
            currentJob.complete();
            currentJob = null;
        }
    }

    /**
     * Larga a tarefa atual e o caminho. O desfazer das reservas é do {@link
     * Job#abort()}, chamado por quem cancelou — a unidade só solta a mão.
     */
    public void stop() {
        currentJob = null;
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

    public void update(float delta, GridMap map, PathFinder finder) {
        if (isMoving()) {
            advance(delta, map);
            if (!isMoving() && currentJob != null) {
                onArrival(map, finder);
            }
            return;
        }
        if (currentJob == null) {
            return;
        }
        if (currentJob.getPhase() == Job.Phase.WORKING) {
            if (currentJob.tickWork(delta, map)) {
                afterPhase(map, finder);
            }
            return;
        }
        // Parada numa fase de deslocamento: o destino é onde ela já está. Vale
        // para o clique no próprio tile da unidade e para a obra marcada em
        // cima do depósito — casos que, sem isto, deixariam a tarefa eterna.
        onArrival(map, finder);
    }

    private void advance(float delta, GridMap map) {
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

    private void onArrival(GridMap map, PathFinder finder) {
        currentJob.arrive();
        afterPhase(map, finder);
    }

    /** Fase encerrada: ou a tarefa acabou, ou há um novo destino para andar. */
    private void afterPhase(GridMap map, PathFinder finder) {
        if (currentJob.isDone()) {
            finishJob();
            return;
        }
        if (currentJob.getPhase() == Job.Phase.WORKING) {
            return; // trabalho parado: nada a percorrer
        }
        routeToDestination(map, finder);
    }

    private void routeToDestination(GridMap map, PathFinder finder) {
        GridPoint2 destination = currentJob.getDestination();
        getCell(cellScratch);

        if (!finder.findPath(cellScratch.x, cellScratch.y,
                destination.x, destination.y, routeScratch)) {
            abandonJob();
            return;
        }
        if (routeScratch.size == 0) {
            onArrival(map, finder); // já está lá; a chegada é imediata
            return;
        }
        setPath(routeScratch, map);
    }

    /** Desiste da tarefa devolvendo o que ela tinha reservado. */
    private void abandonJob() {
        if (currentJob != null) {
            currentJob.abort();
            currentJob = null;
        }
        path.clear();
        pathIndex = 0;
    }

    /** Célula em que a unidade está (a mais próxima, se estiver entre duas). */
    public GridPoint2 getCell(GridPoint2 out) {
        return out.set(Math.round(position.x), Math.round(position.y));
    }

    /** Está com as mãos ocupadas — o desenho põe uma carga sobre a cabeça. */
    public boolean isCarrying() {
        return currentJob != null && currentJob.isCarrying();
    }

    /** Rótulo curto do que a unidade está fazendo agora, para o painel lateral. */
    public String describeState() {
        if (currentJob == null) {
            return "Ocioso";
        }
        switch (currentJob.getType()) {
            case CHOP:
                switch (currentJob.getPhase()) {
                    case WORKING:
                        return "Cortando";
                    case TO_DEPOT:
                        return "Levando lenha";
                    default:
                        return "Andando";
                }
            case BUILD:
                switch (currentJob.getPhase()) {
                    case TO_SUPPLY:
                        return "Buscando madeira";
                    case TO_TARGET:
                        return "Levando material";
                    case WORKING:
                        return "Construindo";
                    default:
                        return "Andando";
                }
            default:
                return "Andando";
        }
    }

    public int getId() {
        return id;
    }

    public String getName() {
        return name;
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
