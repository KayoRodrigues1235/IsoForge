package isoforge.render;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Disposable;
import isoforge.sim.World;

/**
 * Como o mundo aparece na tela — e a única coisa que precisa mudar para ele
 * aparecer de outro jeito.
 *
 * <p>Esta interface existe para que duas implementações possam coexistir: o
 * desenho 2.5D de geometria chapada que o jogo tem hoje, e o renderizador 3D
 * com texturas que é o alvo do projeto. Enquanto a migração acontece, as duas
 * ficam vivas ao mesmo tempo e uma tecla alterna entre elas. É a diferença
 * entre reescrever o desenho com o jogo funcionando e reescrevê-lo com o jogo
 * quebrado por três semanas.
 *
 * <p><b>Câmera e <i>picking</i> entram aqui, e não no jogo.</b> Não é
 * arrumação: só quem desenha sabe como o mundo vira tela. Descobrir o tile sob
 * o cursor é, no 2.5D, uma varredura das faces de topo na ordem inversa da
 * pintura; no 3D é um raio de câmera contra a malha do terreno. As duas
 * respondem à mesma pergunta e não têm uma linha em comum — se o jogo
 * soubesse fazer isso, saberia fazer de um jeito só, e seria o errado para
 * metade dos casos.
 *
 * <p>Pelo mesmo motivo o movimento de câmera chega como <b>intenção</b>, não
 * como pixels: {@link #pan} recebe uma direção, e cada implementação decide o
 * quanto isso vale. Afastado, o mesmo toque de tecla precisa cobrir mais mundo.
 *
 * <p>O que <b>não</b> está aqui: a interface de usuário. O HUD é desenhado pelo
 * jogo, por fora, para poder ser trocado (por Scene2D, mais adiante) sem tocar
 * em nenhum renderizador — e para continuar legível independentemente de como
 * o mundo estiver sendo desenhado.
 */
public interface WorldRenderer extends Disposable {

    /** Nome curto para a interface dizer qual renderizador está ativo. */
    String getName();

    /** Desenha o mundo inteiro, incluindo limpar a tela. */
    void render(World world, Cursor cursor);

    void resize(int width, int height);

    /**
     * Descobre qual célula do mapa está sob um ponto da tela.
     *
     * @param out preenchido com a célula quando há uma
     * @return false se o ponto não cai sobre o mapa
     */
    boolean pickCell(World world, int screenX, int screenY, GridPoint2 out);

    /**
     * Move a câmera na direção indicada.
     *
     * @param dirX  -1, 0 ou 1 — esquerda, parado, direita
     * @param dirY  -1, 0 ou 1 — baixo, parado, cima
     * @param delta tempo real desde o último frame; a câmera nunca anda no
     *              tempo simulado, senão pausar o jogo travaria a câmera junto
     */
    void pan(float dirX, float dirY, float delta);

    /** Aproxima ou afasta. Positivo afasta, na convenção da roda do mouse. */
    void zoom(float steps);

    /** Aponta a câmera para uma posição do grid. */
    void centerOn(float gridX, float gridY);

    void setGridVisible(boolean visible);

    boolean isGridVisible();

    /** Fator de aproximação atual, só para a interface mostrar. */
    float getZoom();
}
