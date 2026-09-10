package isoforge;

import com.badlogic.gdx.utils.Array;
import isoforge.entity.Building;
import isoforge.entity.BuildingType;
import isoforge.entity.Job;
import isoforge.entity.Tree;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * O laço de produção da colônia, do machado ao telhado.
 *
 * <p>Estes testes cobrem o que dá errado devagar: reserva que não é devolvida,
 * tarefa que fica sem dono para sempre, unidade que trava esperando um caminho
 * que não existe. São bugs que não aparecem na tela no dia em que são
 * escritos — aparecem numa partida longa, semanas depois, como "sumiu madeira".
 */
class ColonyLoopTest {

    /** Depósito no mesmo lugar do jogo: a oeste da rampa, longe do lago. */
    private static final int DEPOT_X = 5;
    private static final int DEPOT_Y = 23;

    /** Bosque de teste: grama plana a oeste, toda alcançável a pé do depósito. */
    private static final int[][] GROVE = {
            {2, 18}, {3, 19}, {1, 20}, {2, 21}, {3, 23}, {2, 25}
    };

    /** Terreno livre para obras, longe do lago, do platô e da rampa. */
    private static final int SITE_X = 8;
    private static final int SITE_Y = 21;

    private ColonySimulation sim;

    @BeforeEach
    void setUp() {
        sim = new ColonySimulation(DEPOT_X, DEPOT_Y);
        sim.addUnit("Tico", 5, 21);
        sim.addUnit("Teco", 4, 21);
    }

    /** Corta {@code count} árvores do bosque e espera a lenha chegar ao depósito. */
    private void harvest(int count) {
        for (int i = 0; i < count; i++) {
            sim.board.postChop(sim.addTree(GROVE[i][0], GROVE[i][1]));
        }
        int target = count;
        sim.runUntil(90f, () -> sim.stockpile.getWood() >= target);
        assertEquals(target, sim.stockpile.getWood(),
                "a colheita de apoio precisa fechar antes do teste começar");
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Corte")
    class Corte {

        @Test
        @DisplayName("a lenha cortada chega ao depósito e a unidade volta a ficar livre")
        void corteEntregaLenha() {
            Tree perto = sim.addTree(3, 23);
            Tree longe = sim.addTree(2, 18);
            assertNotNull(sim.board.postChop(perto));
            assertNotNull(sim.board.postChop(longe));

            float levou = sim.runUntil(60f, () -> sim.stockpile.getWood() >= 2);

            assertEquals(2, sim.stockpile.getWood(), "as duas árvores viraram lenha");
            assertFalse(perto.isStanding(), "a árvore cortada não fica de pé");
            assertFalse(longe.isStanding());
            assertEquals(0, sim.board.getTotalCount(), "o quadro esvazia ao terminar");
            assertTrue(sim.everyoneIdle(), "ninguém fica preso na tarefa concluída");
            assertTrue(levou < 60f, "o corte não pode depender do teto de tempo");
        }

        @Test
        @DisplayName("duas tarefas não miram a mesma árvore")
        void arvoreReservadaRecusaSegundaTarefa() {
            Tree tree = sim.addTree(3, 23);

            assertNotNull(sim.board.postChop(tree), "a primeira tarefa é aceita");
            assertNull(sim.board.postChop(tree), "a segunda é recusada enquanto a árvore está reservada");
            assertEquals(1, sim.board.getTotalCount());
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Obra")
    class Obra {

        @Test
        @DisplayName("sem madeira a obra espera, e não trava as tarefas seguintes")
        void obraSemMadeiraNaoBloqueiaAFila() {
            sim.markBuilding(SITE_X, SITE_Y, BuildingType.TORRE); // custa 5, temos 0
            Job andar = sim.board.postMove(7, 25, sim.map);
            assertNotNull(andar);

            sim.run(10f);

            assertEquals(1, sim.board.getOpenCount(), "só a obra continua sem dono");
            assertEquals(1, sim.board.getStarvedCount(), "e o motivo é falta de madeira");
            assertEquals(0, sim.stockpile.getReservedWood(), "nada é reservado antes de haver estoque");
            assertTrue(andar.isDone(), "a tarefa publicada depois foi executada mesmo assim");
        }

        @Test
        @DisplayName("cancelar devolve a madeira, esteja ela reservada ou já nas mãos")
        void cancelarDevolveMadeira() {
            harvest(2);

            Building cabana = sim.markBuilding(SITE_X, SITE_Y, BuildingType.CABANA); // custa 2
            sim.run(0.5f);

            // A unidade que acabou de entregar lenha fica parada em cima do
            // depósito, então ela pode já ter retirado a madeira neste ponto.
            // Reservada na prateleira ou carregada nas costas, o efeito visível
            // é o mesmo: saiu de circulação.
            assertEquals(0, sim.stockpile.getAvailableWood(), "as 2 madeiras saíram de circulação");

            Job job = sim.board.findByTarget(SITE_X, SITE_Y);
            assertNotNull(job, "a obra ainda está no quadro");
            for (var unit : sim.units) {
                if (unit.getCurrentJob() == job) {
                    unit.stop();
                }
            }
            sim.board.cancel(job);

            assertEquals(2, sim.stockpile.getWood(), "a madeira voltou ao depósito");
            assertEquals(0, sim.stockpile.getReservedWood(), "e nada ficou preso na reserva");
            assertEquals(2, sim.stockpile.getAvailableWood(), "está disponível de novo");
            assertTrue(cabana.isCancelled(), "o canteiro sai do mapa junto");
        }

        @Test
        @DisplayName("a obra pronta gasta a madeira e vira obstáculo para o A*")
        void obraProntaBloqueiaOTile() {
            harvest(5);

            Building torre = sim.markBuilding(SITE_X, SITE_Y, BuildingType.TORRE); // custa 5
            float levou = sim.runUntil(90f, torre::isComplete);

            assertTrue(torre.isComplete(), "a torre ficou pronta em " + levou + "s");
            assertEquals(1f, torre.getProgress(), 1e-4f);
            assertEquals(0, sim.stockpile.getWood(), "as 5 madeiras foram gastas");
            assertEquals(0, sim.stockpile.getReservedWood(), "nada ficou reservado");

            assertTrue(sim.map.isBlocked(SITE_X, SITE_Y), "o tile passou a ser ocupado");
            assertFalse(sim.map.isWalkable(SITE_X, SITE_Y), "e deixou de ser caminhável");
            assertFalse(sim.finder.findPath(5, 21, SITE_X, SITE_Y, new Array<>()),
                    "o A* não aceita mais o tile da torre como destino");
            assertEquals(0, sim.board.getTotalCount(), "o quadro esvaziou");
            assertTrue(sim.everyoneIdle());
        }
    }

    // ------------------------------------------------------------------

    @Nested
    @DisplayName("Caminho impossível")
    class CaminhoImpossivel {

        @Test
        @DisplayName("tarefa inalcançável fica no quadro sem prender ninguém")
        void tarefaInalcancavelNaoPrendeUnidade() {
            sim.wallOff(7, 22, 9, 22, 8, 23, 8, 21);
            assertNotNull(sim.board.postMove(8, 22, sim.map), "o tile em si continua livre");

            sim.run(10f);

            assertTrue(sim.everyoneIdle(), "ninguém sai andando para um lugar sem caminho");
            assertEquals(1, sim.board.getOpenCount(), "a tarefa espera alguém em posição melhor");
        }

        @Test
        @DisplayName("obra murada no meio do trajeto é abandonada e devolve o material")
        void obraMuradaDevolveMaterial() {
            harvest(2);

            Building cabana = sim.markBuilding(SITE_X, SITE_Y, BuildingType.CABANA);
            // O canteiro é murado logo depois de publicado: a unidade ainda
            // consegue chegar ao depósito, mas não vai conseguir sair de lá
            // para a obra. É o caso que faria a unidade travar carregando
            // madeira para sempre.
            sim.wallOff(SITE_X - 1, SITE_Y, SITE_X + 1, SITE_Y,
                    SITE_X, SITE_Y - 1, SITE_X, SITE_Y + 1);

            sim.run(20f);

            assertTrue(sim.everyoneIdle(), "a unidade desiste em vez de ficar presa");
            assertFalse(cabana.isComplete(), "a obra não foi erguida");
            assertEquals(2, sim.stockpile.getWood(), "a madeira voltou inteira ao depósito");
            assertEquals(0, sim.stockpile.getReservedWood());
            assertEquals(0, sim.board.getTotalCount(), "a tarefa impossível saiu do quadro");
            assertEquals(0, sim.buildings.size, "e o canteiro saiu do mapa");
        }
    }
}
