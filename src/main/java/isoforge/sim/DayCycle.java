package isoforge.sim;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector3;

/**
 * O sol: que horas são no mundo, de que cor está a luz e de onde ela vem.
 *
 * <p>Vive na simulação, e não no renderizador, por duas razões. A primeira é
 * que ele anda no tempo simulado — pausar o jogo tem que parar o sol, e
 * acelerar tem que acelerá-lo, o que sai de graça quando ele recebe o mesmo
 * {@code delta} de todo mundo. A segunda é que <b>dois renderizadores
 * diferentes precisam da mesma resposta</b>: o desenho 2.5D de hoje multiplica
 * as cores do mundo por {@link #getLight()}, e o renderizador 3D vai alimentar
 * uma luz direcional com {@link #getLight()} <i>e</i> {@link #getSunDirection()}.
 * Se a cor do dia morasse dentro do desenho, a segunda implementação teria que
 * recalculá-la do zero e as duas divergiriam no primeiro ajuste.
 *
 * <p>A cor sai de uma tabela de marcos interpolada: {@code STOPS[i]} é o
 * instante em que a luz vale exatamente {@code COLORS[i]}, e entre dois marcos
 * se interpola. É o jeito mais simples de ter amanhecer alaranjado sem
 * escrever uma curva por canal.
 */
public final class DayCycle {

    /** Segundos de tempo simulado que um dia inteiro leva. */
    public static final float DEFAULT_DAY_LENGTH = 150f;

    /** Meio da manhã: começar o jogo à meia-noite seria uma primeira impressão ruim. */
    public static final float MORNING = 0.34f;

    private static final float[] STOPS = {0f, 0.20f, 0.29f, 0.42f, 0.62f, 0.74f, 0.85f, 1f};
    private static final Color[] COLORS = {
            new Color(0.34f, 0.40f, 0.70f, 1f), // madrugada
            new Color(0.34f, 0.40f, 0.70f, 1f),
            new Color(1.00f, 0.76f, 0.60f, 1f), // amanhecer
            new Color(1.00f, 1.00f, 0.97f, 1f), // manhã alta
            new Color(1.00f, 1.00f, 0.97f, 1f), // tarde
            new Color(1.00f, 0.66f, 0.44f, 1f), // entardecer
            new Color(0.34f, 0.40f, 0.70f, 1f), // anoitecer
            new Color(0.34f, 0.40f, 0.70f, 1f),
    };

    /**
     * Inclinação do arco do sol. Zero poria o sol exatamente no plano leste-oeste
     * e as sombras cairiam alinhadas com o grid o dia inteiro, o que denuncia a
     * grade em vez de esconder.
     */
    private static final float ARC_TILT = 0.35f;

    private final float dayLength;
    private float timeOfDay;

    private final Color light = new Color(1f, 1f, 1f, 1f);
    private final Vector3 sunDirection = new Vector3();

    public DayCycle() {
        this(MORNING, DEFAULT_DAY_LENGTH);
    }

    public DayCycle(float startTimeOfDay, float dayLength) {
        this.dayLength = dayLength;
        this.timeOfDay = normalize(startTimeOfDay);
        recompute();
    }

    public void update(float delta) {
        timeOfDay = normalize(timeOfDay + delta / dayLength);
        recompute();
    }

    private static float normalize(float value) {
        float wrapped = value % 1f;
        return wrapped < 0f ? wrapped + 1f : wrapped;
    }

    private void recompute() {
        int i = 0;
        while (i < STOPS.length - 2 && timeOfDay > STOPS[i + 1]) {
            i++;
        }
        float span = STOPS[i + 1] - STOPS[i];
        float t = span <= 0f ? 0f : MathUtils.clamp((timeOfDay - STOPS[i]) / span, 0f, 1f);
        light.set(COLORS[i]).lerp(COLORS[i + 1], t);

        // O sol nasce a leste em 0,25, cruza o alto ao meio-dia e se põe a
        // oeste em 0,75. O vetor guardado é a direção em que a luz *viaja*,
        // que é o oposto de onde o sol está — é o que uma luz direcional espera.
        float angle = (timeOfDay - 0.25f) * MathUtils.PI2;
        sunDirection.set(MathUtils.cos(angle), MathUtils.sin(angle), ARC_TILT).nor().scl(-1f);
    }

    /** 0 = meia-noite, 0,5 = meio-dia. */
    public float getTimeOfDay() {
        return timeOfDay;
    }

    /**
     * Cor da luz do momento. Devolve a instância interna: trate como somente
     * leitura e nunca chame mutadores sobre ela.
     */
    public Color getLight() {
        return light;
    }

    /**
     * Direção em que a luz do sol viaja, normalizada.
     *
     * <p><b>À noite este vetor aponta de baixo para cima</b>, porque o sol está
     * literalmente abaixo do horizonte. Não é um defeito a corrigir aqui: o
     * renderizador que usar isto é que decide o que fazer com a noite — trocar
     * por uma luz de lua, cair para luz ambiente, ou apenas deixar escuro.
     * Ver {@link #isDaylight()}.
     */
    public Vector3 getSunDirection() {
        return sunDirection;
    }

    /** True enquanto o sol está acima do horizonte. */
    public boolean isDaylight() {
        return timeOfDay > 0.25f && timeOfDay < 0.75f;
    }

    /** O relógio do mundo em HH:MM. */
    public String getClockLabel() {
        int minutes = (int) (timeOfDay * 24f * 60f);
        return String.format("%02d:%02d", minutes / 60, minutes % 60);
    }
}
