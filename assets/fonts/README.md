# Fontes

`NotoSans-Regular.ttf` — Google Noto Sans, distribuída sob Apache License 2.0
(ver `NotoSans-LICENSE.txt`). Está aqui como **placeholder declarado**: serve
para o HUD ter uma fonte de verdade em vez da bitmap embutida no libGDX, que só
existe num tamanho e fica macia quando escalada.

A escolha é neutra de propósito. Direção de arte é assunto da fase 4 do plano
de migração, e trocar esta fonte por outra é mexer em uma linha de `Assets`.

O arquivo é carregado por `gdx-freetype`, que gera bitmaps em tempo de execução
no tamanho exato de cada uso — por isso um único `.ttf` atende título, corpo e
legenda sem nenhum ser uma versão esticada do outro.
