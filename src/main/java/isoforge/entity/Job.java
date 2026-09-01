package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;

/**
 * Uma unidade de trabalho disponível no {@link JobBoard}.
 *
 * <p>O jogador marca <b>o quê</b>, não <b>quem</b>: clicar num tile não move
 * uma unidade específica, publica uma tarefa que a unidade livre mais próxima
 * assume. É o modelo do Castle Story, e tem a propriedade de escalar sem mudar
 * de interface — com 3 ou com 30 unidades, o jogador faz o mesmo gesto.
 *
 * <p>{@link Type#MOVE} é uma tarefa de uma fase só: chegar ao alvo é concluir.
 * {@link Type#CHOP} tem três: ir até a árvore, cortar (parada, cronômetro
 * correndo) e levar a lenha até o depósito — chegar à árvore é só o começo.
 * O M3 (construção) deve seguir o mesmo padrão de fases em vez de reintroduzir
 * o atalho "chegou, terminou" do M1.5.
 */
public final class Job {

    public enum Type {
        /** Ir até a célula alvo. É o "clique direto" do jogador. */
        MOVE,
        /** Ir até a árvore, cortar e levar a lenha ao depósito. */
        CHOP
    }

    /**
     * Fase da tarefa. {@link Type#MOVE} só usa {@code TO_TARGET} → {@code DONE};
     * {@link Type#CHOP} percorre as quatro.
     */
    public enum Phase {
        TO_TARGET,
        WORKING,
        TO_DEPOT,
        DONE
    }

    /** Segundos parada cortando antes da lenha sair e a árvore cair. */
    public static final float CHOP_DURATION = 2f;

    private final Type type;
    private final GridPoint2 target = new GridPoint2();
    private final GridPoint2 depot = new GridPoint2();
    private Tree tree;
    private Phase phase = Phase.TO_TARGET;
    private float workTimer;
    private boolean claimed;
    private boolean done;

    public Job(Type type, int targetX, int targetY) {
        this.type = type;
        this.target.set(targetX, targetY);
    }

    /** Tarefa de corte: a árvore é o alvo, e a unidade precisa saber para onde levar a lenha. */
    static Job chop(Tree tree, int depotX, int depotY) {
        Job job = new Job(Type.CHOP, tree.getX(), tree.getY());
        job.tree = tree;
        job.depot.set(depotX, depotY);
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

    Tree getTree() {
        return tree;
    }

    /** Aberta = ninguém pegou e ninguém terminou. */
    public boolean isOpen() {
        return !claimed && !done;
    }

    public boolean isDone() {
        return done;
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
     * A unidade chegou ao destino da fase atual. Numa {@code MOVE} isso já
     * conclui a tarefa; numa {@code CHOP}, chegar à árvore inicia o corte, e
     * chegar ao depósito (depois de {@link #tickWork} liberar a volta) conclui.
     */
    void arrive() {
        if (type == Type.MOVE) {
            phase = Phase.DONE;
            complete();
            return;
        }
        if (phase == Phase.TO_TARGET) {
            phase = Phase.WORKING;
            workTimer = CHOP_DURATION;
        } else if (phase == Phase.TO_DEPOT) {
            phase = Phase.DONE;
            complete();
        }
    }

    /**
     * Avança o cronômetro de corte. Devolve {@code true} no instante em que
     * termina — sinal para a unidade calcular o caminho até o depósito.
     */
    boolean tickWork(float delta) {
        if (phase != Phase.WORKING) {
            return false;
        }
        workTimer -= delta;
        if (workTimer <= 0f) {
            phase = Phase.TO_DEPOT;
            if (tree != null) {
                tree.chop();
            }
            return true;
        }
        return false;
    }

    @Override
    public String toString() {
        return type + "(" + target.x + "," + target.y + ") " + phase;
    }
}
