package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;
import isoforge.world.GridMap;

/**
 * Uma unidade de trabalho disponível no {@link JobBoard}.
 *
 * <p>O jogador marca <b>o quê</b>, não <b>quem</b>: clicar num tile não move
 * uma unidade específica, publica uma tarefa que a unidade livre mais próxima
 * assume. É o modelo do Castle Story, e tem a propriedade de escalar sem mudar
 * de interface — com 3 ou com 30 unidades, o jogador faz o mesmo gesto.
 *
 * <p>Os três tipos existentes se distinguem só pela <b>sequência de fases</b>:
 *
 * <pre>
 *   MOVE   TO_TARGET → DONE
 *   CHOP   TO_TARGET → WORKING → TO_DEPOT → DONE
 *   BUILD  TO_SUPPLY → TO_TARGET → WORKING → DONE
 * </pre>
 *
 * <p>Repare que a {@code BUILD} começa indo ao <i>depósito</i>, não à obra: o
 * destino de uma tarefa depende da fase em que ela está, e é por isso que
 * existe {@link #getDestination()}. Tanto o quadro (para escolher a unidade
 * mais próxima) quanto a unidade (para se rotear entre fases) perguntam a ele
 * em vez de assumirem que "a tarefa acontece no alvo".
 *
 * <p>A outra responsabilidade desta classe é o <b>desfazer</b>. Uma tarefa
 * cancelada pode ter uma árvore reservada, madeira reservada no estoque e um
 * canteiro marcado no mapa; se cada ponto de cancelamento tivesse que lembrar
 * disso, um deles esqueceria. {@link #abort()} concentra o rollback num lugar
 * só e é idempotente, então cancelar duas vezes não devolve madeira duas vezes.
 */
public final class Job {

    public enum Type {
        /** Ir até a célula alvo. É o "clique direto" do jogador. */
        MOVE,
        /** Ir até a árvore, cortar e levar a lenha ao depósito. */
        CHOP,
        /** Buscar madeira no depósito, levar até o canteiro e erguer a construção. */
        BUILD
    }

    /**
     * Fase da tarefa. {@code TO_SUPPLY} e {@code TO_DEPOT} apontam ambas para o
     * depósito, e a distinção é de intenção, não de lugar: uma vai buscar, a
     * outra vai entregar.
     */
    public enum Phase {
        TO_SUPPLY,
        TO_TARGET,
        WORKING,
        TO_DEPOT,
        DONE
    }

    /** Segundos parada cortando antes da lenha sair e a árvore cair. */
    public static final float CHOP_DURATION = 2f;

    /** Lenha que uma árvore rende. */
    public static final int WOOD_PER_TREE = 1;

    private final Type type;
    private final GridPoint2 target = new GridPoint2();
    private final GridPoint2 depot = new GridPoint2();
    private final Stockpile stockpile;
    private Tree tree;
    private Building building;
    private Phase phase = Phase.TO_TARGET;
    private float workTimer;
    private float workDuration;
    private boolean claimed;
    private boolean done;
    private boolean aborted;

    /** Madeira já reservada no estoque para esta obra, ainda não retirada. */
    private int reservedWood;

    /** Madeira que a unidade está de fato carregando (já saiu do depósito). */
    private int carriedWood;

    private Job(Type type, int targetX, int targetY, Stockpile stockpile) {
        this.type = type;
        this.target.set(targetX, targetY);
        this.stockpile = stockpile;
    }

    static Job move(int x, int y) {
        return new Job(Type.MOVE, x, y, null);
    }

    /** Tarefa de corte: a árvore é o alvo, e a lenha vai para o depósito. */
    static Job chop(Tree tree, GridPoint2 depot, Stockpile stockpile) {
        Job job = new Job(Type.CHOP, tree.getX(), tree.getY(), stockpile);
        job.tree = tree;
        job.depot.set(depot);
        job.workDuration = CHOP_DURATION;
        return job;
    }

    /** Tarefa de obra: começa no depósito, buscando material. */
    static Job build(Building building, GridPoint2 depot, Stockpile stockpile) {
        Job job = new Job(Type.BUILD, building.getX(), building.getY(), stockpile);
        job.building = building;
        job.depot.set(depot);
        job.workDuration = building.getType().getBuildSeconds();
        job.phase = Phase.TO_SUPPLY;
        return job;
    }

    public Type getType() {
        return type;
    }

    public GridPoint2 getTarget() {
        return target;
    }

    public GridPoint2 getDepot() {
        return depot;
    }

    public Phase getPhase() {
        return phase;
    }

    /** Para onde a unidade tem que ir <i>agora</i>, dada a fase atual. */
    public GridPoint2 getDestination() {
        return (phase == Phase.TO_SUPPLY || phase == Phase.TO_DEPOT) ? depot : target;
    }

    /** A unidade está com as mãos ocupadas (lenha ou material de obra). */
    public boolean isCarrying() {
        return (type == Type.CHOP && phase == Phase.TO_DEPOT)
                || (type == Type.BUILD && phase == Phase.TO_TARGET);
    }

    /** 0..1 do trabalho parado da fase atual. Fora dela, 0. */
    public float getWorkProgress() {
        if (phase != Phase.WORKING || workDuration <= 0f) {
            return 0f;
        }
        return 1f - workTimer / workDuration;
    }

    Tree getTree() {
        return tree;
    }

    public Building getBuilding() {
        return building;
    }

    /** Aberta = ninguém pegou e ninguém terminou. */
    public boolean isOpen() {
        return !claimed && !done;
    }

    public boolean isDone() {
        return done;
    }

    /** Quanto de madeira falta reservar para esta tarefa poder começar. */
    public int getWoodCost() {
        return type == Type.BUILD ? building.getType().getWoodCost() : 0;
    }

    /**
     * Reserva no estoque o material da obra. Chamado pelo quadro no instante
     * em que uma unidade assume a tarefa — antes disso, a madeira continua
     * disponível para quem chegar primeiro.
     */
    boolean reserveMaterial() {
        if (type != Type.BUILD || reservedWood > 0 || carriedWood > 0) {
            return true;
        }
        int cost = getWoodCost();
        if (!stockpile.reserveWood(cost)) {
            return false;
        }
        reservedWood = cost;
        return true;
    }

    void claim() {
        claimed = true;
    }

    /** Devolve a tarefa ao quadro, por exemplo se a unidade desistir. */
    void release() {
        claimed = false;
    }

    void complete() {
        done = true;
    }

    /**
     * Cancela a tarefa desfazendo tudo o que ela havia prendido: a reserva da
     * árvore, o canteiro e a madeira — tanto a ainda reservada quanto a que a
     * unidade já tinha nas mãos.
     *
     * <p>Devolver a carga foi decidido depois de ver o caso que quebra a
     * alternativa: uma unidade que acabou de entregar lenha fica <i>parada em
     * cima do depósito</i>, então ela retira o material da obra seguinte no
     * mesmo frame em que assume a tarefa. Com a madeira carregada sendo
     * perdida, cancelar um clique errado custaria recurso sem o jogador nunca
     * ter visto ninguém andar. Só vira construção o que chegou ao canteiro.
     */
    void abort() {
        if (aborted) {
            return;
        }
        aborted = true;
        if (tree != null && tree.isStanding()) {
            tree.release();
        }
        if (reservedWood > 0) {
            stockpile.releaseWood(reservedWood);
            reservedWood = 0;
        }
        if (carriedWood > 0) {
            stockpile.addWood(carriedWood);
            carriedWood = 0;
        }
        if (building != null && !building.isComplete()) {
            building.cancel();
        }
        done = true;
    }

    /**
     * A unidade chegou ao destino da fase atual e a tarefa avança.
     *
     * <p>Cada tipo lê esta transição de um jeito: para a {@code MOVE} chegar é
     * terminar; para a {@code CHOP} chegar à árvore é <i>começar</i>; para a
     * {@code BUILD} chegar ao depósito é carregar a madeira e sair de novo.
     */
    void arrive() {
        switch (type) {
            case MOVE:
                phase = Phase.DONE;
                complete();
                return;

            case CHOP:
                if (phase == Phase.TO_TARGET) {
                    startWork();
                } else if (phase == Phase.TO_DEPOT) {
                    stockpile.addWood(WOOD_PER_TREE);
                    phase = Phase.DONE;
                    complete();
                }
                return;

            case BUILD:
                if (phase == Phase.TO_SUPPLY) {
                    // A reserva vira retirada: a madeira sai do estoque aqui, e
                    // não na hora de erguer, porque é aqui que ela muda de mãos.
                    stockpile.withdrawWood(reservedWood);
                    carriedWood = reservedWood;
                    reservedWood = 0;
                    phase = Phase.TO_TARGET;
                } else if (phase == Phase.TO_TARGET) {
                    startWork();
                }
                return;

            default:
        }
    }

    private void startWork() {
        phase = Phase.WORKING;
        workTimer = workDuration;
    }

    /**
     * Avança o cronômetro do trabalho parado. Devolve {@code true} no instante
     * em que a fase termina — sinal para a unidade decidir o próximo passo.
     */
    boolean tickWork(float delta, GridMap map) {
        if (phase != Phase.WORKING) {
            return false;
        }
        workTimer -= delta;

        if (type == Type.CHOP && tree != null) {
            tree.markBeingChopped();
        } else if (type == Type.BUILD && building != null) {
            building.setProgress(getWorkProgress());
        }

        if (workTimer > 0f) {
            return false;
        }

        if (type == Type.CHOP) {
            phase = Phase.TO_DEPOT;
            if (tree != null) {
                tree.chop();
            }
        } else {
            carriedWood = 0;
            building.finish(map);
            phase = Phase.DONE;
            complete();
        }
        return true;
    }

    @Override
    public String toString() {
        return type + "(" + target.x + "," + target.y + ") " + phase;
    }
}
