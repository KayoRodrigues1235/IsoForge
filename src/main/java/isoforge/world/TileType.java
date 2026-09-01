package isoforge.world;

import com.badlogic.gdx.graphics.Color;

/**
 * Tipos de terreno do mapa.
 *
 * <p>No M0 cada tipo é só uma cor chapada — não há arte ainda. Desenhar o
 * losango por geometria em vez de sprite mantém o marco sem pipeline de
 * assets, e força a matemática da projeção a ficar explícita no código.
 *
 * <p>O campo {@code walkable} já existe porque o A* do M1 vai precisar dele;
 * é barato deixar preparado agora.
 */
public enum TileType {

    GRASS(new Color(0.34f, 0.53f, 0.29f, 1f), true),
    DIRT(new Color(0.45f, 0.36f, 0.24f, 1f), true),
    STONE(new Color(0.44f, 0.45f, 0.48f, 1f), true),
    WATER(new Color(0.20f, 0.36f, 0.55f, 1f), false);

    private final Color color;
    private final boolean walkable;

    TileType(Color color, boolean walkable) {
        this.color = color;
        this.walkable = walkable;
    }

    /**
     * Cor base do terreno. Devolve a instância compartilhada: trate como
     * somente leitura e nunca chame mutadores (
     * {@code set}, {@code mul}, {@code lerp}) sobre ela.
     */
    public Color getColor() {
        return color;
    }

    public boolean isWalkable() {
        return walkable;
    }
}
