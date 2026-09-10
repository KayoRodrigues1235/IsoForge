package isoforge.ui;

import isoforge.entity.BuildingType;

/**
 * O contrato entre o HUD e o jogo: o que a interface pode mandar fazer, e o
 * que ela precisa perguntar para se desenhar coerente.
 *
 * <p>Existe para o HUD não conhecer {@code IsoForgeGame}. A dependência anda só
 * num sentido — a interface pede, o jogo decide — e é isso que permite trocar
 * a interface inteira mais tarde sem tocar em quem executa os comandos.
 *
 * <p>Os métodos de consulta não são luxo: um HUD com botões precisa mostrar
 * <b>qual</b> está ativo, e o jogador pode ter trocado de modo pelo teclado
 * meio segundo antes. Sem eles, o botão e a realidade divergem no primeiro
 * atalho apertado.
 */
public interface HudActions {

    /** Volta ao modo em que o clique publica tarefas. */
    void selectTaskMode();

    /** Entra no modo construção com o prédio indicado. */
    void selectBuilding(BuildingType type);

    void setPaused(boolean paused);

    /** Índice dentro da tabela de velocidades do jogo. */
    void setTimeScaleIndex(int index);

    void cancelAllJobs();

    boolean isPaused();

    int getTimeScaleIndex();

    /** Velocidades disponíveis, para o HUD montar um botão por multiplicador. */
    float[] getTimeScales();

    /** O prédio selecionado, ou null quando o jogo está em modo tarefa. */
    BuildingType getSelectedBuilding();

    /** A última mensagem de ordem, mostrada ao jogador. */
    String getStatusMessage();

    String getRendererName();

    float getZoom();
}
