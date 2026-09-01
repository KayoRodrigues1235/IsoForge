package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;
import isoforge.world.GridMap;
import isoforge.world.PathFinder;

/**
 * O quadro de tarefas: a fila de trabalho que as unidades consultam sozinhas.
 *
 * <p>Esta classe é o que separa "gestão" de "RTS". Numa interface RTS o jogador
 * seleciona quem e manda para onde; aqui ele publica o que precisa ser feito e
 * o quadro resolve a atribuição. A consequência prática é que o jogo não
 * precisa de seleção por caixa, grupos nem formação — e continua funcionando
 * igual quando as unidades passarem de 5 para 50.
 *
 * <p><b>A tarefa escolhe a unidade, não o contrário.</b> A ordem importa: se
 * cada unidade ociosa procurasse trabalho por conta própria, a primeira a
 * perguntar levaria a tarefa mesmo havendo outra bem mais perto — e o jogador
 * veria alguém atravessar o mapa enquanto a vizinha fica parada. Percorrendo
 * as tarefas e escolhendo a melhor unidade para cada uma, esse absurdo some.
 *
 * <p><b>Distância é por caminho real, não por linha reta.</b> Num mapa com
 * penhascos e um único gargalo a linha reta mente: um tile do outro lado da
 * parede do platô fica a um passo em linha reta e a vinte passos de caminhada.
 * Por isso a comparação usa o comprimento do caminho do A*.
 *
 * <p>A atribuição é gulosa, tarefa a tarefa, e não um casamento ótimo entre
 * todas as tarefas e todas as unidades. Guloso é o suficiente para um jogo e
 * evita trazer um algoritmo de atribuição bipartida para cá.
 */
public final class JobBoard {

    private final Array<Job> jobs = new Array<>();
    private final GridPoint2 originCell = new GridPoint2();
    private final Array<GridPoint2> candidatePath = new Array<>();
    private final Array<GridPoint2> bestPath = new Array<>();

    public void post(Job job) {
        jobs.add(job);
    }

    /** Publica uma tarefa de deslocamento. Devolve null se o alvo é intransitável. */
    public Job postMove(int x, int y, GridMap map) {
        if (!map.contains(x, y) || !map.get(x, y).isWalkable()) {
            return null;
        }
        Job job = new Job(Job.Type.MOVE, x, y);
        post(job);
        return job;
    }

    /**
     * Distribui as tarefas abertas entre as unidades ociosas.
     *
     * <p>Para cada tarefa sem dono, calcula o caminho a partir de cada unidade
     * livre e entrega à mais próxima <i>por caminho</i>. Unidades sem rota até
     * a tarefa são ignoradas — se ninguém alcança, a tarefa fica no quadro
     * esperando alguém em posição melhor.
     *
     * <p>Custo: (tarefas abertas × unidades ociosas) buscas A*. Por isso o jogo
     * chama isto a cada fração de segundo, e não a cada frame.
     *
     * @return quantas tarefas foram atribuídas nesta passada
     */
    public int assignPass(Array<Unit> units, PathFinder finder, GridMap map) {
        int assigned = 0;

        for (Job job : jobs) {
            if (!job.isOpen()) {
                continue;
            }
            GridPoint2 target = job.getTarget();

            Unit best = null;
            int bestSteps = Integer.MAX_VALUE;

            for (Unit unit : units) {
                if (!unit.isIdle()) {
                    continue;
                }
                unit.getCell(originCell);
                if (!finder.findPath(originCell.x, originCell.y, target.x, target.y, candidatePath)) {
                    continue;
                }
                if (candidatePath.size < bestSteps) {
                    bestSteps = candidatePath.size;
                    best = unit;
                    bestPath.clear();
                    bestPath.addAll(candidatePath);
                }
            }

            if (best != null) {
                job.claim();
                best.assign(job, bestPath, map);
                assigned++;
            }
        }

        return assigned;
    }

    /** Pinta no mask as células de tarefas que ainda não têm dono. */
    public void markOpenJobs(boolean[] mask, int mapWidth) {
        for (Job job : jobs) {
            if (job.isOpen()) {
                GridPoint2 target = job.getTarget();
                mask[target.y * mapWidth + target.x] = true;
            }
        }
    }

    /** Remove as tarefas concluídas. Chamar uma vez por frame. */
    public void purgeCompleted() {
        for (int i = jobs.size - 1; i >= 0; i--) {
            if (jobs.get(i).isDone()) {
                jobs.removeIndex(i);
            }
        }
    }

    /** Esvazia o quadro. As unidades já em trabalho precisam ser paradas à parte. */
    public void clear() {
        jobs.clear();
    }

    public int getOpenCount() {
        int open = 0;
        for (Job job : jobs) {
            if (job.isOpen()) {
                open++;
            }
        }
        return open;
    }

    public int getTotalCount() {
        return jobs.size;
    }
}
