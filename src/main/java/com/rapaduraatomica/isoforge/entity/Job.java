package com.rapaduraatomica.isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;

/**
 * Uma unidade de trabalho disponível no {@link JobBoard}.
 *
 * <p>O jogador marca <b>o quê</b>, não <b>quem</b>: clicar num tile não move
 * uma unidade específica, publica uma tarefa que a unidade livre mais próxima
 * assume. É o modelo do Castle Story, e tem a propriedade de escalar sem mudar
 * de interface — com 3 ou com 30 unidades, o jogador faz o mesmo gesto.
 *
 * <p>No M1.5 só existe {@link Type#MOVE}. O tipo já é um enum porque o M2 vai
 * acrescentar CHOP (cortar árvore) e o M3, BUILD.
 */
public final class Job {

    public enum Type {
        /** Ir até a célula alvo. É o "clique direto" do jogador. */
        MOVE
    }

    private final Type type;
    private final GridPoint2 target = new GridPoint2();
    private boolean claimed;
    private boolean done;

    public Job(Type type, int targetX, int targetY) {
        this.type = type;
        this.target.set(targetX, targetY);
    }

    public Type getType() {
        return type;
    }

    public GridPoint2 getTarget() {
        return target;
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

    @Override
    public String toString() {
        return type + "(" + target.x + "," + target.y + ")";
    }
}
