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
 *
 * <p>Entre "de pé" e "sumiu" existe um terceiro estado: <b>caindo</b>. Ele não
 * serve a nenhuma regra — serve ao olho. Uma árvore que desaparece no instante
 * em que o cronômetro zera faz o corte parecer um bug de renderização; a mesma
 * árvore tombando por meio segundo faz o jogador entender o que aconteceu sem
 * ler o painel. Por isso a animação mora aqui, no modelo, e não no
 * renderizador: quem desenha só pergunta o progresso da queda.
 */
public final class Tree {

    /** Segundos que a árvore leva para tombar depois do último machadada. */
    public static final float FALL_DURATION = 0.7f;

    /** Quanto tempo o tremor dura depois do último aviso de que estão cortando. */
    private static final float SHAKE_MEMORY = 0.15f;

    private final int id;
    private final GridPoint2 cell = new GridPoint2();
    private boolean reserved;
    private boolean chopped;
    private boolean falling;
    private float fallTimer;
    private float shakeTimer;
    private boolean justChopped;

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
        return !reserved && !chopped && !falling;
    }

    /** Ainda em pé e inteira — só nesse estado faz sentido mandar cortar. */
    public boolean isStanding() {
        return !chopped && !falling;
    }

    /** Já sumiu do mapa: não desenha mais, não ocupa mais nada. */
    public boolean isChopped() {
        return chopped;
    }

    /** Enquanto cai ainda é desenhada, tombando. */
    public boolean isFalling() {
        return falling;
    }

    /** 0 = em pé, 1 = deitada no chão. */
    public float getFallProgress() {
        if (chopped) {
            return 1f;
        }
        return falling ? 1f - fallTimer / FALL_DURATION : 0f;
    }

    public boolean isShaking() {
        return shakeTimer > 0f;
    }

    public void update(float delta) {
        if (shakeTimer > 0f) {
            shakeTimer -= delta;
        }
        if (falling) {
            fallTimer -= delta;
            if (fallTimer <= 0f) {
                falling = false;
                chopped = true;
            }
        }
    }

    void reserve() {
        reserved = true;
    }

    void release() {
        reserved = false;
    }

    /** Aviso, vindo da tarefa de corte, de que o machado está batendo agora. */
    void markBeingChopped() {
        shakeTimer = SHAKE_MEMORY;
    }

    void chop() {
        falling = true;
        fallTimer = FALL_DURATION;
        shakeTimer = 0f;
        justChopped = true;
    }

    /**
     * Consome o aviso de "acabou de ser derrubada". Existe para o jogo soltar
     * as lascas no frame certo sem a árvore precisar conhecer quem desenha.
     */
    public boolean pollJustChopped() {
        boolean value = justChopped;
        justChopped = false;
        return value;
    }
}
