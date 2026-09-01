package isoforge.world;

import com.badlogic.gdx.math.GridPoint2;
import com.badlogic.gdx.utils.Array;

import java.util.Arrays;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * A* sobre o {@link GridMap}, ciente de elevação.
 *
 * <p>Três regras definem o que é um caminho válido, e juntas são o que
 * transforma o relevo em geografia jogável:
 *
 * <ol>
 *   <li>Água não é caminhável.</li>
 *   <li>Um degrau maior que {@link GridMap#MAX_CLIMB} é penhasco — bloqueia.
 *       É esta regra que faz a borda do platô virar muralha natural.</li>
 *   <li>Subir custa mais que andar no plano ({@link #CLIMB_COST}). Descer é
 *       de graça. Assim a unidade prefere contornar um morro a escalá-lo
 *       quando o desvio é curto — comportamento que parece inteligente sem
 *       precisar de nenhuma inteligência.</li>
 * </ol>
 *
 * <p><b>Vizinhança:</b> 4 direções no array — (x±1, y) e (x, y±1). Na tela
 * elas aparecem como diagonais, o que é a projeção fazendo seu trabalho, não
 * um erro. Ver a nota sobre vizinhança em {@link IsoProjector}.
 *
 * <p>A classe é pura lógica: nenhuma dependência de renderização, e os buffers
 * de trabalho são alocados uma vez no construtor e reutilizados a cada busca.
 * Isso permite testá-la sem abrir janela e evita lixo quando houver dezenas de
 * unidades pedindo caminho.
 */
public final class PathFinder {

    /** Custo de andar para um vizinho no mesmo nível. */
    private static final float FLAT_COST = 1f;

    /**
     * Custo adicional por nível de subida. Precisa ser ≥ 0 para a heurística
     * continuar admissível — se subir desse "desconto", o A* poderia devolver
     * um caminho que não é o mais barato.
     */
    private static final float CLIMB_COST = 2f;

    private static final int[] NEIGHBOR_DX = {1, -1, 0, 0};
    private static final int[] NEIGHBOR_DY = {0, 0, 1, -1};

    private final GridMap map;
    private final int width;
    private final int height;

    private final float[] gScore;
    private final float[] fScore;
    private final int[] cameFrom;
    private final boolean[] closed;
    private final PriorityQueue<Integer> open;

    /** Quantos nós saíram da fila na última busca. Útil para diagnóstico. */
    private int lastExpandedNodes;

    public PathFinder(GridMap map) {
        this.map = map;
        this.width = map.getWidth();
        this.height = map.getHeight();

        int cells = width * height;
        this.gScore = new float[cells];
        this.fScore = new float[cells];
        this.cameFrom = new int[cells];
        this.closed = new boolean[cells];

        // A fila ordena índices de célula pelo fScore corrente. Guardar
        // Integer (e não um objeto Node) mantém o código curto ao custo de
        // autoboxing; com uma unidade isso é irrelevante, e vale revisitar
        // quando houver dezenas pedindo caminho no mesmo frame.
        Comparator<Integer> byLowestF = Comparator.comparingDouble(i -> fScore[i]);
        this.open = new PriorityQueue<>(byLowestF);
    }

    /**
     * Procura o caminho mais barato de (startX,startY) até (goalX,goalY).
     *
     * @param out preenchido com as células a percorrer, <b>excluindo</b> a de
     *            partida e incluindo a de destino. É limpo antes de tudo.
     * @return true se existe caminho. Um destino igual à origem devolve true
     *         com {@code out} vazio — já se está lá.
     */
    public boolean findPath(int startX, int startY, int goalX, int goalY, Array<GridPoint2> out) {
        out.clear();
        lastExpandedNodes = 0;

        if (!map.contains(startX, startY) || !map.contains(goalX, goalY)) {
            return false;
        }
        if (!map.get(goalX, goalY).isWalkable()) {
            return false;
        }
        if (startX == goalX && startY == goalY) {
            return true;
        }

        reset();

        int start = index(startX, startY);
        int goal = index(goalX, goalY);
        gScore[start] = 0f;
        fScore[start] = heuristic(startX, startY, goalX, goalY);
        open.add(start);

        while (!open.isEmpty()) {
            int current = open.poll();

            // Descarte preguiçoso: em vez de atualizar a prioridade de um nó
            // já enfileirado (que a PriorityQueue do Java não sabe fazer bem),
            // inserimos uma cópia com o custo melhor e ignoramos as sobras
            // quando elas saem. É a forma idiomática em Java.
            if (closed[current]) {
                continue;
            }
            closed[current] = true;
            lastExpandedNodes++;

            if (current == goal) {
                rebuild(start, goal, out);
                return true;
            }

            int cx = current % width;
            int cy = current / width;
            int currentLevel = map.getLevel(cx, cy);

            for (int d = 0; d < NEIGHBOR_DX.length; d++) {
                int nx = cx + NEIGHBOR_DX[d];
                int ny = cy + NEIGHBOR_DY[d];
                if (!map.contains(nx, ny)) {
                    continue;
                }

                int neighbor = index(nx, ny);
                if (closed[neighbor] || !map.get(nx, ny).isWalkable()) {
                    continue;
                }

                int climb = map.getLevel(nx, ny) - currentLevel;
                if (Math.abs(climb) > GridMap.MAX_CLIMB) {
                    continue; // penhasco
                }

                float stepCost = FLAT_COST + (climb > 0 ? climb * CLIMB_COST : 0f);
                float tentative = gScore[current] + stepCost;
                if (tentative < gScore[neighbor]) {
                    cameFrom[neighbor] = current;
                    gScore[neighbor] = tentative;
                    fScore[neighbor] = tentative + heuristic(nx, ny, goalX, goalY);
                    open.add(neighbor);
                }
            }
        }

        return false;
    }

    private void reset() {
        Arrays.fill(gScore, Float.MAX_VALUE);
        Arrays.fill(fScore, Float.MAX_VALUE);
        Arrays.fill(closed, false);
        Arrays.fill(cameFrom, -1);
        open.clear();
    }

    /**
     * Distância de Manhattan. Nunca superestima o custo real, porque todo passo
     * custa pelo menos {@link #FLAT_COST} — condição para o A* garantir o
     * caminho ótimo.
     */
    private float heuristic(int x, int y, int goalX, int goalY) {
        return (Math.abs(goalX - x) + Math.abs(goalY - y)) * FLAT_COST;
    }

    private void rebuild(int start, int goal, Array<GridPoint2> out) {
        int node = goal;
        while (node != start && node != -1) {
            out.add(new GridPoint2(node % width, node / width));
            node = cameFrom[node];
        }
        out.reverse(); // caminhamos de trás para frente ao reconstruir
    }

    private int index(int x, int y) {
        return y * width + x;
    }

    public int getLastExpandedNodes() {
        return lastExpandedNodes;
    }
}
