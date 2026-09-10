package isoforge.fx;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;

/**
 * Um punhado de partículas: lascas de madeira ao cortar, poeira ao terminar
 * uma obra. Enfeite puro — nada aqui afeta a simulação.
 *
 * <p><b>As posições estão em coordenadas de simulação, não em pixels.</b>
 * {@code x} e {@code z} são coordenadas de grid, no mesmo sistema contínuo em
 * que uma unidade ocupa (3,4 · 7,8); {@code height} é altura em <i>níveis de
 * terreno</i>, a mesma unidade de {@code GridMap.getLevel} e de
 * {@code Unit.getVisualLevel}. Quem desenha é que projeta.
 *
 * <p>Isso não era assim: a primeira versão guardava pixels de tela já
 * projetados, com gravidade em px/s². Funcionava, e teria custado uma
 * reescrita no dia em que existisse um segundo renderizador — a lasca não
 * precisa saber que o mundo é desenhado em losangos, e agora não sabe. Os dois
 * eixos usam escalas diferentes (um passo horizontal é um tile, um passo
 * vertical é um nível) porque é exatamente assim que o resto da simulação
 * já media as duas coisas.
 *
 * <p>Continua valendo a limitação de sempre: partículas são desenhadas por
 * cima de tudo, fora da ordenação por profundidade. Uma lasca não some atrás
 * do platô. Reordená-las por frame custaria mais do que o efeito vale.
 *
 * <p>O armazenamento é um pool de arrays paralelos percorrido em anel: nada é
 * alocado depois do construtor, então o efeito pode disparar à vontade sem
 * aparecer no gráfico de GC.
 */
public final class Particles {

    private static final int CAPACITY = 512;

    /** Níveis por segundo ao quadrado. Negativo porque a altura cresce para cima. */
    private static final float GRAVITY = -16f;

    private final float[] x = new float[CAPACITY];
    private final float[] height = new float[CAPACITY];
    private final float[] z = new float[CAPACITY];
    private final float[] vx = new float[CAPACITY];
    private final float[] vHeight = new float[CAPACITY];
    private final float[] vz = new float[CAPACITY];
    private final float[] life = new float[CAPACITY];
    private final float[] maxLife = new float[CAPACITY];
    private final float[] size = new float[CAPACITY];
    private final float[] red = new float[CAPACITY];
    private final float[] green = new float[CAPACITY];
    private final float[] blue = new float[CAPACITY];

    private int next;

    /**
     * Espalha {@code count} partículas a partir de um ponto.
     *
     * @param spread velocidade horizontal, em tiles por segundo
     * @param particleSize tamanho em frações da largura de um tile — nada de
     *               pixels, pelo mesmo motivo das posições
     * @param lift   velocidade vertical inicial, em níveis por segundo. É um
     *               parâmetro separado do horizontal de propósito: sem um
     *               impulso para cima a explosão vira um borrão simétrico em
     *               vez de algo saltando do chão.
     */
    public void burst(float gridX, float gridHeight, float gridZ, int count,
                      float spread, float lift, float lifeSeconds,
                      float particleSize, Color color) {
        for (int i = 0; i < count; i++) {
            float angle = MathUtils.random(MathUtils.PI2);
            float speed = spread * MathUtils.random(0.4f, 1f);
            spawn(gridX, gridHeight, gridZ,
                    MathUtils.cos(angle) * speed,
                    lift * MathUtils.random(0.6f, 1.1f),
                    MathUtils.sin(angle) * speed,
                    lifeSeconds * MathUtils.random(0.6f, 1f),
                    particleSize, color);
        }
    }

    public void spawn(float gridX, float gridHeight, float gridZ,
                      float velX, float velHeight, float velZ,
                      float lifeSeconds, float particleSize, Color color) {
        // Anel: a partícula mais velha é sobrescrita quando o pool enche. Com
        // 512 posições isso só acontece em rajadas grandes, e perder a lasca
        // mais antiga é exatamente o que menos se nota.
        int i = next;
        next = (next + 1) % CAPACITY;

        x[i] = gridX;
        height[i] = gridHeight;
        z[i] = gridZ;
        vx[i] = velX;
        vHeight[i] = velHeight;
        vz[i] = velZ;
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
            vHeight[i] += GRAVITY * delta;
            x[i] += vx[i] * delta;
            height[i] += vHeight[i] * delta;
            z[i] += vz[i] * delta;
        }
    }

    public int getCapacity() {
        return CAPACITY;
    }

    public boolean isAlive(int i) {
        return life[i] > 0f;
    }

    /** Coordenada de grid no eixo X, contínua. */
    public float getX(int i) {
        return x[i];
    }

    /** Altura em níveis de terreno acima do chão do tile. */
    public float getHeight(int i) {
        return height[i];
    }

    /** Coordenada de grid no eixo Y do mapa — chamada Z por ser a profundidade. */
    public float getZ(int i) {
        return z[i];
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
