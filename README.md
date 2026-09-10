# IsoForge

Um jogo isométrico de gestão e geopolítica, feito para aprender Java e libGDX (e para me divertir no processo). Você cai numa área do mapa, comanda suas unidades ao estilo *Castle Story* e tenta erguer algo antes que a IA rival tome conta do resto do território.

Sem prazo, sem pressa, sem ambição de virar produto — é projeto de portfólio e curiosidade.

## A proposta

Nada de RTS clássico com clique-e-arraste em cada unidade. Aqui você publica **tarefas** num quadro e as unidades ociosas se viram para pegar o trabalho mais adequado — quem está mais perto *pelo caminho real*, não em linha reta (o mapa tem penhascos, e linha reta mente). Elevação é mecânica, não cenário: platôs isolam, rampas são gargalo, e subir custa mais que andar no plano.

O ciclo já fecha: corte árvores, a lenha vai para o depósito, e o depósito paga as construções. Um prédio pronto ocupa o tile de verdade — o A* passa a contorná-lo, e sim, dá para se murar sozinho. Isso é mecânica, não bug: é o mesmo relevo do mapa, só que construído por você.

O adversário é uma IA disputando o mesmo mapa por território e recursos — ainda não implementada, mas é o horizonte que guia as decisões de arquitetura desde o início.

## Estrutura do código

```
src/main/java/isoforge/
├── DesktopLauncher.java   # abre a janela, entrega o jogo pro backend LWJGL3
├── IsoForgeGame.java      # o loop: câmera, input, picking, desenho, luz do dia
├── world/                 # o mapa e sua matemática
│   ├── IsoProjector.java  #   conversão grid ↔ tela, sem alocar nada por frame
│   ├── GridMap.java       #   tiles, elevação, ocupação, o mapa de teste
│   ├── TileType.java      #   grama, terra, pedra, água — e quem é andável
│   └── PathFinder.java    #   A* ciente de elevação, penhascos e construções
├── entity/                # quem habita o mapa — nada aqui conhece um pixel
│   ├── Unit.java          #   posição contínua, caminho, fase da tarefa atual
│   ├── Job.java           #   uma tarefa e sua sequência de fases
│   ├── JobBoard.java      #   o quadro — tarefas escolhem unidades, não o contrário
│   ├── Tree.java          #   árvore: reservada, cortada, caindo
│   ├── Building.java      #   canteiro → obra em progresso → obstáculo
│   ├── BuildingType.java  #   catálogo: custo, tempo e aparência
│   └── Stockpile.java     #   estoque, com reserva separada do gasto
└── fx/
    └── Particles.java     #   lascas e poeira, em pool sem alocação por frame
```

A separação entre `world` (matemática pura, testável sem abrir janela nenhuma) e o resto é proposital: a projeção isométrica não sabe que existe um `ShapeRenderer`, então trocar a forma de desenhar no futuro não deveria doer. Pelo mesmo motivo `entity` não conhece pixel nenhum — a simulação inteira roda sem contexto OpenGL.

### O que uma tarefa é

Os tipos de tarefa não se distinguem pelo que fazem, e sim pela **sequência de fases** que percorrem:

```
MOVE    ir ao alvo                                        → fim
CHOP    ir à árvore → cortar → levar a lenha ao depósito  → fim
BUILD   buscar madeira no depósito → levar ao canteiro → erguer → fim
```

Chegar quase nunca termina a tarefa: cada chegada fecha uma fase e abre a próxima, que pode exigir um caminho novo. A unidade se rota sozinha entre as fases. Acrescentar um tipo de tarefa é acrescentar uma sequência, não um caso especial no loop principal.

## Testes

A simulação inteira roda sem abrir janela — `entity` e `world` não importam OpenGL, então dá para plantar uma árvore, mandar cortá-la e ver a lenha chegar ao depósito num processo sem tela:

```bash
./gradlew test
```

Os testes cobrem o que dá errado devagar: reserva de material que não volta, tarefa que fica sem dono para sempre, unidade que trava esperando um caminho que não existe. São bugs que não aparecem na tela no dia em que são escritos — aparecem numa partida longa, semanas depois, como "sumiu madeira". Se um dia um teste daqui precisar de contexto gráfico para passar, a separação entre simulação e desenho vazou.

## Rodando

Bazzite/Fedora Atômico não vem com JDK — instale via [SDKMAN](https://sdkman.io/):

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew run
```

## Controles

| | |
|---|---|
| **Clique esquerdo** | publica uma tarefa no tile (árvore = cortar) |
| **Arrastar com o esquerdo** | publica em tudo o que o cursor passar |
| **Clique direito** | cancela a tarefa *daquele* tile |
| `X` | cancela tudo |
| `B` | liga/desliga o modo construção |
| `TAB` | troca o prédio selecionado (cabana / torre) |
| `SPACE` | pausa |
| `,` `.` | velocidade da simulação (1x / 2x / 4x) |
| `WASD` / setas | move a câmera · **scroll** dá zoom |
| `G` | liga/desliga a grade · `ESC` sai |

O relógio no canto é de verdade: um dia leva 150 segundos de tempo simulado, e a luz do sol multiplica as cores do mundo — mas nunca as da interface, que precisa ser legível às três da manhã.

## Stack

Java 21 · libGDX 1.13.1 (backend LWJGL3) · Gradle
