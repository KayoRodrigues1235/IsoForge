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
├── IsoForgeGame.java      # input, relógio e HUD — o meio de campo, não o jogo
├── sim/                   # a simulação: nada aqui conhece um pixel
│   ├── World.java         #   a colônia inteira, com update(delta)
│   └── DayCycle.java      #   que horas são, cor da luz e direção do sol
├── render/                # a apresentação: nada aqui decide regra
│   ├── WorldRenderer.java #   o contrato — desenho, câmera e picking
│   ├── IsoShapeRenderer.java # a implementação 2.5D de geometria chapada
│   └── Cursor.java        #   o que o jogador está apontando neste frame
├── world/                 # o mapa e sua matemática
│   ├── IsoProjector.java  #   conversão grid ↔ tela, sem alocar nada por frame
│   ├── GridMap.java       #   tiles, elevação, ocupação, o mapa de teste
│   ├── TileType.java      #   grama, terra, pedra, água — e quem é andável
│   └── PathFinder.java    #   A* ciente de elevação, penhascos e construções
├── entity/                # quem habita o mapa
│   ├── Unit.java          #   posição contínua, caminho, fase da tarefa atual
│   ├── Job.java           #   uma tarefa e sua sequência de fases
│   ├── JobBoard.java      #   o quadro — tarefas escolhem unidades, não o contrário
│   ├── Tree.java          #   árvore: reservada, cortada, caindo
│   ├── Building.java      #   canteiro → obra em progresso → obstáculo
│   ├── BuildingType.java  #   catálogo: custo, tempo e aparência
│   └── Stockpile.java     #   estoque, com reserva separada do gasto
├── assets/                # o que o jogo carrega de disco
│   └── Assets.java        #   AssetManager: fila, progresso, fontes do FreeType
├── ui/                    # a interface, por fora de qualquer renderizador
│   ├── GameHud.java       #   recursos, tempo, paleta de construção, unidades
│   ├── HudActions.java    #   o contrato entre a interface e o jogo
│   └── ProceduralSkin.java #  skin do Scene2D montada em código, sem assets
└── fx/
    └── Particles.java     #   lascas e poeira, em coordenadas de simulação
```

A linha que divide o projeto passa entre `sim` e `render`. De um lado, a colônia: mapa, unidades, tarefas, estoque, o sol. Nada ali conhece câmera, contexto gráfico ou projeção isométrica — a simulação inteira roda num processo sem tela, e os testes provam isso a cada build. Do outro, o desenho: uma implementação de `WorldRenderer` que decide como aquele estado vira pixels.

**Câmera e *picking* ficam do lado do desenho**, e não é arrumação. Descobrir o tile sob o cursor é, no 2.5D, uma varredura das faces de topo na ordem inversa da pintura; num renderizador 3D seria um raio de câmera contra a malha do terreno. As duas respondem à mesma pergunta e não têm uma linha em comum — se o jogo soubesse fazer essa conta, saberia fazer de um jeito só, e seria o errado para metade dos casos.

O HUD é a exceção deliberada: desenhado por fora de qualquer renderizador, para sobreviver à troca do desenho do mundo sem ser tocado.

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

## Assets

A pasta `assets/` entra no build como **recurso**, não como diretório de trabalho da task `run` — assim `Gdx.files.internal` acha os arquivos tanto no `./gradlew run` quanto num jar empacotado, sem o jogo precisar saber de qual dos dois foi iniciado.

O carregamento passa por um `AssetManager` assíncrono, coberto por uma tela com uma barra e nenhuma palavra — o que está carregando *é a fonte*, então não há com que escrever "Carregando" até terminar. A estrutura é assumidamente maior do que a carga de hoje exige: ela existe para as texturas de terreno e os *decals* que vêm depois, e a diferença entre "some um frame" e "a janela trava dois segundos" é tê-la antes de precisar.

A fonte é gerada pelo `gdx-freetype` no tamanho exato de cada uso, e não escalada a partir de um tamanho só. Título, corpo e legenda saem do mesmo `.ttf` sem nenhum ser uma versão macia do outro. Ver `assets/fonts/README.md` para a licença e para por que a escolha é placeholder.

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
| `G` | liga/desliga a grade · `F3` abre o painel de depuração · `ESC` sai |

Todo comando tem botão no HUD — o teclado é atalho, não a única porta. O relógio no canto é de verdade: um dia leva 150 segundos de tempo simulado, e a luz do sol multiplica as cores do mundo — mas nunca as da interface, que precisa ser legível às três da manhã.

## Stack

Java 21 · libGDX 1.13.1 (backend LWJGL3) · Gradle
