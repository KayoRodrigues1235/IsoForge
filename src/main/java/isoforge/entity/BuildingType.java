package isoforge.entity;

import com.badlogic.gdx.graphics.Color;

/**
 * O catálogo de construções.
 *
 * <p>Custo, tempo de obra e aparência ficam juntos aqui porque são o que
 * distingue uma construção da outra — o resto do fluxo (buscar madeira, andar
 * até a obra, erguer) é idêntico para todas. Acrescentar um prédio novo ao
 * jogo deve ser acrescentar uma constante a este enum, e nada mais.
 */
public enum BuildingType {

    CABANA("Cabana", 2, 2.5f, 22f,
            new Color(0.62f, 0.47f, 0.31f, 1f),
            new Color(0.72f, 0.31f, 0.24f, 1f)),

    TORRE("Torre", 5, 5f, 52f,
            new Color(0.55f, 0.56f, 0.60f, 1f),
            new Color(0.30f, 0.42f, 0.62f, 1f));

    private final String label;
    private final int woodCost;
    private final float buildSeconds;
    private final float visualHeight;
    private final Color wallColor;
    private final Color roofColor;

    BuildingType(String label, int woodCost, float buildSeconds, float visualHeight,
                 Color wallColor, Color roofColor) {
        this.label = label;
        this.woodCost = woodCost;
        this.buildSeconds = buildSeconds;
        this.visualHeight = visualHeight;
        this.wallColor = wallColor;
        this.roofColor = roofColor;
    }

    public String getLabel() {
        return label;
    }

    public int getWoodCost() {
        return woodCost;
    }

    /** Segundos que a unidade fica parada erguendo a construção. */
    public float getBuildSeconds() {
        return buildSeconds;
    }

    /** Altura em pixels de tela quando pronta. Só desenho, não é nível de terreno. */
    public float getVisualHeight() {
        return visualHeight;
    }

    /** Cores compartilhadas: leia, nunca chame mutadores sobre elas. */
    public Color getWallColor() {
        return wallColor;
    }

    public Color getRoofColor() {
        return roofColor;
    }

    public BuildingType next() {
        BuildingType[] all = values();
        return all[(ordinal() + 1) % all.length];
    }
}
