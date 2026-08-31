# IsoForge

Um jogo isométrico de gestão e geopolítica, feito para aprender Java e libGDX (e para me divertir no processo). Você cai numa área do mapa, comanda suas unidades ao estilo *Castle Story* e tenta erguer algo antes que a IA rival tome conta do resto do território.

Sem prazo, sem pressa, sem ambição de virar produto — é projeto de portfólio e curiosidade.

## A proposta

Nada de RTS clássico com clique-e-arraste em cada unidade. Aqui você publica **tarefas** num quadro e as unidades ociosas se viram para pegar o trabalho mais adequado — quem está mais perto *pelo caminho real*, não em linha reta (o mapa tem penhascos, e linha reta mente). Elevação é mecânica, não cenário: platôs isolam, rampas são gargalo, e subir custa mais que andar no plano.

O adversário é uma IA disputando o mesmo mapa por território e recursos — ainda não implementada, mas é o horizonte que guia as decisões de arquitetura desde o início.

## Estrutura do código

```
src/main/java/com/rapaduraatomica/isoforge/
├── DesktopLauncher.java   # abre a janela, entrega o jogo pro backend LWJGL3
├── IsoForgeGame.java      # o loop: câmera, input, picking, desenho
├── world/                 # o mapa e sua matemática
│   ├── IsoProjector.java  #   conversão grid ↔ tela, sem alocar nada por frame
│   ├── GridMap.java       #   tiles, elevação, o mapa de teste
│   ├── TileType.java      #   grama, terra, pedra, água — e quem é andável
│   └── PathFinder.java    #   A* ciente de elevação e penhascos
└── entity/                # quem habita o mapa
    ├── Unit.java           #   posição contínua, caminho, tarefa atual
    ├── Job.java             #   uma tarefa: tipo, alvo, dono
    └── JobBoard.java        #   o quadro — tarefas escolhem unidades, não o contrário
```

A separação entre `world` (matemática pura, testável sem abrir janela nenhuma) e o resto é proposital: a projeção isométrica não sabe que existe um `ShapeRenderer`, então trocar a forma de desenhar no futuro não deveria doer.

## Rodando

Bazzite/Fedora Atômico não vem com JDK — instale via [SDKMAN](https://sdkman.io/):

```bash
source "$HOME/.sdkman/bin/sdkman-init.sh"
./gradlew run
```

**Controles:** clique esquerdo publica uma tarefa de deslocamento · clique direito cancela tudo · `WASD`/setas fazem pan · scroll dá zoom · `G` liga/desliga a grade · `ESC` sai.

## Stack

Java 21 · libGDX 1.13.1 (backend LWJGL3) · Gradle
