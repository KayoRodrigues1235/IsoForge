package com.rapaduraatomica.isoforge.world;

/**
 * O mapa: uma grade fixa de tiles, cada um com terreno e altura.
 *
 * <p>32x32 não é um número provisório. Num mapa grande, você e a IA rival
 * crescem em cantos opostos e nunca se encontram — a camada geopolítica, que é
 * o núcleo do jogo, jamais é exercitada numa partida de teste. Apertado, a
 * escassez força o contato. O tamanho só deve crescer quando houver um motivo
 * de design, não por inércia.
 *
 * <p><b>A altura é mecânica, não decorativa.</b> É ela que faz um tile valer
 * mais que outro: num mapa plano de 32x32, disputar território com a IA é
 * aritmética (quem cerca mais quadrados). Com relevo existe vale defensável,
 * rampa que é gargalo e platô que domina a região — a disputa passa a ter
 * geografia. Por isso a altura entra no modelo antes do pathfinding, e não
 * depois: retrofitar elevação num A* já pronto é reescrevê-lo.
 *
 * <p>O armazenamento é unidimensional indexado por {@code y * width + x}. É o
 * layout que a CPU percorre mais rápido e evita o array-de-arrays do Java,
 * onde cada linha é um objeto separado espalhado na memória.
 */
public final class GridMap {

    public static final int DEFAULT_SIZE = 32;

    /** Maior nível de terreno que a geração produz. */
    public static final int MAX_LEVEL = 3;

    /**
     * Maior desnível que uma unidade consegue vencer entre tiles vizinhos.
     * Acima disso é penhasco: intransponível. É esta constante que transforma
     * a borda de um platô em muralha natural e a rampa em gargalo — o A* do M1
     * vai consultá-la.
     */
    public static final int MAX_CLIMB = 1;

    private final int width;
    private final int height;
    private final TileType[] tiles;
    private final int[] levels;

    public GridMap(int width, int height) {
        this.width = width;
        this.height = height;
        this.tiles = new TileType[width * height];
        this.levels = new int[width * height];
        generatePlaceholder();
    }

    /**
     * Terreno provisório e <b>determinístico</b> — de propósito.
     *
     * <p>A geração aleatória (o "cai numa área aleatória do mapa") vem depois.
     * Enquanto se depura projeção, câmera e picking, um mapa que muda a cada
     * execução atrapalha: não dá para saber se o que mudou na tela foi o seu
     * código ou o sorteio.
     *
     * <p>O layout é escolhido para exercitar a mecânica de altura: um platô
     * cercado de penhascos, alcançável por uma <b>única rampa de 3 tiles de
     * largura</b>. Quando o A* do M1 existir, esse gargalo é o primeiro teste
     * — se as unidades acharem outro caminho para cima, a regra de {@link
     * #MAX_CLIMB} está furada.
     */
    private void generatePlaceholder() {
        float lakeCenterX = width * 0.28f;
        float lakeCenterY = height * 0.30f;
        float lakeRadius = Math.min(width, height) * 0.15f;

        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                TileType type = TileType.GRASS;
                int level = 0;

                boolean onPlateau = x >= 18 && x <= 28 && y >= 16 && y <= 26;
                boolean onRamp = y >= 20 && y <= 22 && x >= 14 && x < 18;

                if (onPlateau) {
                    level = MAX_LEVEL;
                    // Afloramento rochoso no alto: recurso que só existe depois
                    // de vencer o gargalo.
                    type = (x >= 24 && y >= 22) ? TileType.STONE : TileType.GRASS;
                } else if (onRamp) {
                    // x=14 -> nivel 0 ... x=17 -> nivel 3, encostando no platô.
                    level = x - 14;
                    type = TileType.DIRT;
                } else {
                    float dx = x - lakeCenterX;
                    float dy = y - lakeCenterY;
                    if (dx * dx + dy * dy < lakeRadius * lakeRadius) {
                        type = TileType.WATER;
                    }
                }

                int index = y * width + x;
                tiles[index] = type;
                levels[index] = level;
            }
        }
    }

    /** True se a célula existe no mapa. Chame antes de qualquer get/set. */
    public boolean contains(int x, int y) {
        return x >= 0 && x < width && y >= 0 && y < height;
    }

    public TileType get(int x, int y) {
        requireInside(x, y);
        return tiles[y * width + x];
    }

    public void set(int x, int y, TileType type) {
        requireInside(x, y);
        tiles[y * width + x] = type;
    }

    /** Nível de terreno da célula. 0 é o chão. */
    public int getLevel(int x, int y) {
        requireInside(x, y);
        return levels[y * width + x];
    }

    public void setLevel(int x, int y, int level) {
        requireInside(x, y);
        levels[y * width + x] = level;
    }

    private void requireInside(int x, int y) {
        if (!contains(x, y)) {
            throw new IndexOutOfBoundsException("Célula fora do mapa: " + x + "," + y);
        }
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }
}
