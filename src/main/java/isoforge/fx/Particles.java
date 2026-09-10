package isoforge.fx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Um punhado de partículas: lascas de madeira ao cortar, poeira ao terminar
 * uma obra. Enfeite puro — nada aqui afeta a simulação.
 *
 * <p><b>Elas vivem em pixels de mundo, já projetados</b>, e não em coordenadas
 * de grid. É a única parte do jogo que faz isso, e é intencional: uma lasca
 * não precisa saber em que tile está, só precisa cair na tela de um jeito que
 * convença. Em troca, elas são desenhadas por cima de tudo em vez de entrar na
 * ordenação por profundidade — some o efeito de uma lasca sumir atrás do
 * platô, mas não se paga a complexidade de reordenar partículas por frame.
 *
 * <p>O armazenamento é um pool de arrays paralelos percorrido em anel: nada é
 * alocado depois do construtor, então o efeito pode disparar à vontade sem
 * aparecer no gráfico de GC.
 */
public final class Particles {

    private static final int CAPACITY = 512;

    /** Pixels por segundo ao quadrado. Negativo porque o Y do mundo cresce para cima. */
    private static final float GRAVITY = -260f;

    private final float[] x = new float[CAPACITY];
    private final float[] y = new float[CAPACITY];
    private final float[] vx = new float[CAPACITY];
    private final float[] vy = new float[CAPACITY];
    private final float[] life = new float[CAPACITY];
    private final float[] maxLife = new float[CAPACITY];
    private final float[] size = new float[CAPACITY];
    private final float[] red = new float[CAPACITY];
    private final float[] green = new float[CAPACITY];
    private final float[] blue = new float[CAPACITY];

    private int next;

    /**
     * Espalha {@code count} partículas a partir de um ponto, com direção
     * sorteada e um viés para cima — sem o viés a explosão parece um borrão
     * simétrico em vez de algo saltando do chão.
     */
    public void burst(float worldX, float worldY, int count, float speed, float lifeSeconds,
                      float particleSize, Color color) {
        for (int i = 0; i < count; i++) {
            float angle = MathUtils.random(MathUtils.PI2);
            float magnitude = speed * MathUtils.random(0.4f, 1f);
            spawn(worldX, worldY,
                    MathUtils.cos(angle) * magnitude,
                    Math.abs(MathUtils.sin(angle)) * magnitude + speed * 0.5f,
                    lifeSeconds * MathUtils.random(0.6f, 1f),
                    particleSize, color);
        }
    }

    public void spawn(float worldX, float worldY, float velX, float velY,
                      float lifeSeconds, float particleSize, Color color) {
        // Anel: a partícula mais velha é sobrescrita quando o pool enche. Com
        // 512 posições isso só acontece em rajadas grandes, e perder a lasca
        // mais antiga é exatamente o que menos se nota.
        int i = next;
        next = (next + 1) % CAPACITY;

        x[i] = worldX;
        y[i] = worldY;
        vx[i] = velX;
        vy[i] = velY;
        life[i] = lifeSeconds;
        maxLife[i] = lifeSeconds;
        size[i] = particleSize;
        red[i] = color.r;
        green[i] = color.g;
        blue[i] = color.b;
    }

    public void update(float delta) {
        for (int i = 0; i < CAPACITY; i++) {
            if (life[i] <= 0f) {
                continue;
            }
            life[i] -= delta;
            vy[i] += GRAVITY * delta;
            x[i] += vx[i] * delta;
            y[i] += vy[i] * delta;
        }
    }

    public int getCapacity() {
        return CAPACITY;
    }

    public boolean isAlive(int i) {
        return life[i] > 0f;
    }

    public float getX(int i) {
        return x[i];
    }

    public float getY(int i) {
        return y[i];
    }

    public float getSize(int i) {
        return size[i];
    }

    public float getRed(int i) {
        return red[i];
    }

    public float getGreen(int i) {
        return green[i];
    }

    public float getBlue(int i) {
        return blue[i];
    }

    /** Some junto com a vida, para a partícula não desaparecer de um frame para o outro. */
    public float getAlpha(int i) {
        return maxLife[i] <= 0f ? 0f : Math.min(1f, life[i] / maxLife[i]);
    }
}
