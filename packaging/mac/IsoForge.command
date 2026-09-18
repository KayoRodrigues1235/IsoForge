#!/bin/bash
# Lançador de duplo clique para macOS.
#
# Existe porque um .jar não abre com duplo clique no macOS moderno: o sistema
# não associa mais a extensão a nada. Este arquivo é um script de shell com
# extensão .command, que o Finder abre no Terminal — e o Terminal sabe rodar
# `java -jar`. A flag -XstartOnFirstThread não aparece aqui de propósito: quem
# cuida dela é a própria classe MacStartup, dentro do jogo.

cd "$(dirname "$0")" || exit 1

if ! command -v java >/dev/null 2>&1; then
  echo
  echo "  O IsoForge precisa do Java 21 (ou mais novo), que não está instalado."
  echo
  echo "  Baixe o instalador da Adoptium (gratuito, assinado pela Apple):"
  echo "    https://adoptium.net/temurin/releases/?os=mac&version=21"
  echo
  echo "  Instale, feche esta janela e clique de novo neste arquivo."
  echo
  read -r -p "  Pressione Enter para fechar. " _
  exit 1
fi

versao=$(java -version 2>&1 | head -1 | sed -E 's/.*version "([0-9]+).*/\1/')
if [ -n "$versao" ] && [ "$versao" -lt 21 ] 2>/dev/null; then
  echo
  echo "  O Java instalado é a versão $versao; o IsoForge precisa da 21 ou mais nova."
  echo "    https://adoptium.net/temurin/releases/?os=mac&version=21"
  echo
  read -r -p "  Pressione Enter para fechar. " _
  exit 1
fi

echo "  Abrindo o IsoForge... (esta janela pode ficar aberta atrás do jogo)"
java -jar IsoForge.jar "$@"
