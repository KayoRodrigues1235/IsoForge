package isoforge.entity;

import com.badlogic.gdx.math.GridPoint2;

/**
 * Uma árvore no mapa: fonte de madeira até alguém terminar de cortá-la.
 *
 * <p><b>Reservada</b> é diferente de <b>cortada</b>. Reservar impede que duas
 * tarefas mirem a mesma árvore ao mesmo tempo, enquanto a unidade ainda está a
 * caminho ou cortando; cortada é definitivo, a árvore some do mapa. Cancelar
 * uma tarefa de corte libera a reserva sem desfazer nada, já que nesse ponto a
 * árvore continua de pé.
 */
public final class Tree {

    private final int id;
    private final GridPoint2 cell = new GridPoint2();
    private boolean reserved;
    private boolean chopped;

    public Tree(int id, int x, int y) {
        this.id = id;
        cell.set(x, y);
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

    /** Livre para uma nova tarefa: nem reservada, nem já cortada. */
    public boolean isAvailable() {
        return !reserved && !chopped;
    }

    public boolean isChopped() {
        return chopped;
    }

    void reserve() {
        reserved = true;
    }

    void release() {
        reserved = false;
    }

    void chop() {
        chopped = true;
    }
}
