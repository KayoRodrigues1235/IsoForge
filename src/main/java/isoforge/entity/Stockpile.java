package isoforge.entity;

/**
 * O estoque do depósito: quanto de cada recurso a colônia tem em mãos.
 *
 * <p><b>Reservado é diferente de gasto.</b> Quando uma tarefa de construção é
 * assumida, a madeira que ela vai consumir é <i>reservada</i> na hora, mesmo
 * que a unidade ainda leve dez segundos para chegar ao depósito e pegá-la. Sem
 * isso, duas obras assumidas no mesmo instante veriam o mesmo tronco no
 * estoque e uma das duas chegaria ao depósito para encontrar a prateleira
 * vazia — o clássico "check-then-act" que quebra quando há concorrência.
 *
 * <p>Cancelar uma obra devolve o que ela prendeu, esteja a madeira ainda
 * reservada na prateleira ou já nas mãos de quem estava a caminho — só sai do
 * estoque de vez o que virou construção. Ver o motivo em {@link Job#abort()}.
 */
public final class Stockpile {

    private int wood;
    private int reservedWood;

    public int getWood() {
        return wood;
    }

    public int getReservedWood() {
        return reservedWood;
    }

    /** O que dá para prometer a uma tarefa nova. */
    public int getAvailableWood() {
        return wood - reservedWood;
    }

    public void addWood(int amount) {
        wood += amount;
    }

    /** Promete madeira a uma tarefa. Falha (sem efeito) se não houver livre. */
    public boolean reserveWood(int amount) {
        if (amount > getAvailableWood()) {
            return false;
        }
        reservedWood += amount;
        return true;
    }

    /** Devolve ao estoque uma reserva que não vai mais ser usada. */
    public void releaseWood(int amount) {
        reservedWood = Math.max(0, reservedWood - amount);
    }

    /** A unidade passou no depósito e levou a madeira reservada. */
    public void withdrawWood(int amount) {
        releaseWood(amount);
        wood = Math.max(0, wood - amount);
    }
}
