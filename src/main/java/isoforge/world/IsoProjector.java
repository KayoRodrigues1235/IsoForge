package isoforge.world;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.math.Vector2;

/**
 * Converte coordenadas entre o grid lógico do mapa (coluna/linha/altura) e o
 * espaço do mundo em pixels que a câmera enxerga.
 *
 * <p>Usamos a projeção "diamond" 2:1 clássica: cada tile é um losango com o
 * dobro de largura em relação à altura. As fórmulas são:
 *
 * <pre>
 *   mundoX = (gridX - gridY) * meiaLargura
 *   mundoY = (gridX + gridY) * meiaAltura  +  nivel * passoDeElevacao
 * </pre>
 *
 * <p>Andar +1 em gridX empurra o tile para a direita e para cima na tela; andar
 * +1 em gridY empurra para a esquerda e para cima. O tile (0,0) fica na ponta
 * de baixo do losango grande e os índices maiores recuam para o fundo.
 *
 * <p><b>A elevação é puramente um deslocamento vertical na tela.</b> Ela não
 * muda a identidade do tile: (5, 5) continua sendo (5, 5) esteja no nível 0 ou
 * no nível 4, só é desenhado mais acima. É por isso que o termo de altura entra
 * apenas em mundoY, e por isso que a inversa ({@link #worldToCell}) precisa
 * saber o nível para acertar — ver a nota em {@link #topFaceContains}.
 *
 * <p><b>Vizinhança não é cartesiana.</b> Uma consequência da projeção que
 * confunde na primeira vez e vai importar no pathfinding: mover-se na tela não
 * corresponde a mover-se no array.
 *
 * <pre>
 *   subir na tela      = (+1, +1) no grid
 *   descer na tela     = (-1, -1)
 *   direita na tela    = (+1, -1)
 *   esquerda na tela   = (-1, +1)
 * </pre>
 *
 * <p>Os vizinhos que compartilham <i>aresta</i> com um tile — os que um A* de
 * 4 direções deve considerar — continuam sendo (x±1, y) e (x, y±1); na tela
 * eles aparecem nas diagonais. Tratar os vizinhos do array como "cima/baixo/
 * esquerda/direita visuais" produz caminhos que parecem tortos ao jogador.
 *
 * <p>Esta classe é deliberadamente pura: só matemática, nenhuma dependência de
 * renderização, e nenhuma alocação nos métodos chamados a cada frame. Isso
 * permite testá-la sem abrir uma janela.
 */
public final class IsoProjector {

    private final float halfWidth;
    private final float halfHeight;
    private final float elevationStep;

    /**
     * @param tileWidth     largura do losango em pixels de mundo
     * @param tileHeight    altura do losango; metade da largura dá o ângulo 2:1
     * @param elevationStep quantos pixels cada nível de altura sobe na tela
     */
    public IsoProjector(float tileWidth, float tileHeight, float elevationStep) {
        this.halfWidth = tileWidth / 2f;
        this.halfHeight = tileHeight / 2f;
        this.elevationStep = elevationStep;
    }

    /**
     * Grid -> mundo, ao nível do chão. O ponto devolvido é o <b>centro</b> do
     * tile, não um canto. Aceita valores fracionários de propósito: mais
     * adiante, uma unidade caminhando entre dois tiles vai ocupar posições
     * como (3.4, 7.8).
     */
    public Vector2 gridToWorld(float gridX, float gridY, Vector2 out) {
        return out.set(
                (gridX - gridY) * halfWidth,
                (gridX + gridY) * halfHeight);
    }

    /** Como {@link #gridToWorld}, mas erguido para o nível informado. */
    public Vector2 gridToWorld(float gridX, float gridY, int level, Vector2 out) {
        return out.set(
                (gridX - gridY) * halfWidth,
                (gridX + gridY) * halfHeight + level * elevationStep);
    }

    /**
     * Mundo -> grid, em coordenadas contínuas (invertendo as fórmulas acima).
     * Ignora elevação: assume que o ponto está no nível do chão.
     */
    public Vector2 worldToGrid(float worldX, float worldY, Vector2 out) {
        float a = worldX / halfWidth;   // = gridX - gridY
        float b = worldY / halfHeight;  // = gridX + gridY
        return out.set((a + b) / 2f, (b - a) / 2f);
    }

    /**
     * Mundo -> célula inteira do grid, assumindo nível do chão.
     *
     * <p><b>Detalhe que engana todo mundo:</b> aqui se arredonda, não se trunca.
     * Como {@link #gridToWorld} devolve o <i>centro</i> do tile, a região
     * contínua que pertence ao tile (0,0) vai de -0,5 a +0,5 nos dois eixos.
     * Um {@code floor()} jogaria metade do losango para o vizinho errado e o
     * cursor pareceria "desalinhado" com o mapa. {@code round()} pega a célula
     * cujo centro está mais perto — que é a definição correta de "o tile sob o
     * mouse" <i>num mapa plano</i>.
     *
     * <p>Com elevação isto deixa de bastar sozinho: ver {@link #topFaceContains}.
     */
    public GridPoint2 worldToCell(float worldX, float worldY, GridPoint2 out) {
        float a = worldX / halfWidth;
        float b = worldY / halfHeight;
        return out.set(
                MathUtils.round((a + b) / 2f),
                MathUtils.round((b - a) / 2f));
    }

    /**
     * Testa se um ponto do mundo está sobre a <b>face de topo</b> do tile
     * informado, já considerando o nível dele.
     *
     * <p>Este é o teste que substitui o {@link #worldToCell} puro assim que o
     * terreno deixa de ser plano. O motivo: um tile no nível 3 é desenhado
     * bem acima de onde a projeção chapada o colocaria, então o cursor sobre
     * ele produziria, pelo cálculo plano, a célula de <i>outro</i> tile mais ao
     * fundo. Sem isso, clicar num platô seleciona o tile errado.
     *
     * <p>A conta é a "distância de losango": normaliza o deslocamento pelos
     * semi-eixos e soma. Dentro do losango essa soma é ≤ 1 — é o equivalente
     * diamante do teste de ponto-dentro-de-círculo.
     */
    public boolean topFaceContains(int gridX, int gridY, int level, float worldX, float worldY) {
        float centerX = (gridX - gridY) * halfWidth;
        float centerY = (gridX + gridY) * halfHeight + level * elevationStep;
        float dx = Math.abs(worldX - centerX) / halfWidth;
        float dy = Math.abs(worldY - centerY) / halfHeight;
        return dx + dy <= 1f;
    }

    /**
     * Preenche {@code out} com os 4 vértices da face de topo do tile, no
     * formato plano [x0,y0, x1,y1, x2,y2, x3,y3] que o ShapeRenderer consome.
     * A ordem é topo, direita, base, esquerda.
     */
    public float[] topFacePolygon(int gridX, int gridY, int level, float[] out) {
        float centerX = (gridX - gridY) * halfWidth;
        float centerY = (gridX + gridY) * halfHeight + level * elevationStep;
        out[0] = centerX;             out[1] = centerY + halfHeight; // topo
        out[2] = centerX + halfWidth; out[3] = centerY;              // direita
        out[4] = centerX;             out[5] = centerY - halfHeight; // base
        out[6] = centerX - halfWidth; out[7] = centerY;              // esquerda
        return out;
    }

    public float getHalfWidth() {
        return halfWidth;
    }

    public float getHalfHeight() {
        return halfHeight;
    }

    public float getElevationStep() {
        return elevationStep;
    }
}
