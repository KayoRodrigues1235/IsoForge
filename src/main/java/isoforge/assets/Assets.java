package isoforge.assets;

import com.badlogic.gdx.assets.AssetManager;
import com.badlogic.gdx.assets.loaders.FileHandleResolver;
import com.badlogic.gdx.assets.loaders.resolvers.InternalFileHandleResolver;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGenerator;
import com.badlogic.gdx.graphics.g2d.freetype.FreeTypeFontGeneratorLoader;
import com.badlogic.gdx.graphics.g2d.freetype.FreetypeFontLoader;
import com.badlogic.gdx.utils.Disposable;

/**
 * Tudo que o jogo carrega de disco, num lugar só.
 *
 * <p>O projeto passou três marcos sem um único asset: terreno, unidades e
 * interface eram geometria e cores escritas em código. Isso acabou aqui, e a
 * primeira coisa a entrar foi uma fonte — não por capricho, mas porque a fonte
 * embutida no libGDX existe num tamanho só, e o título do HUD, escalado a
 * 1,35×, ficava macio.
 *
 * <p><b>O FreeType resolve isso de um jeito que um .fnt pronto não resolveria:</b>
 * ele gera o bitmap no tamanho exato de cada uso. Título, corpo e legenda saem
 * do mesmo arquivo e nenhum é uma versão esticada do outro. O custo é um
 * punhado de milissegundos no início, que é justamente o que a tela de
 * carregamento cobre.
 *
 * <p>O carregamento é assíncrono de propósito, mesmo sendo rápido hoje. Não é
 * pelo que se carrega agora — é pelo que vai se carregar: as texturas de
 * terreno e os <i>decals</i> da fase 3 entram por aqui, e a diferença entre
 * "some um frame" e "a janela trava por dois segundos" é ter ou não ter esta
 * estrutura antes de precisar dela.
 */
public final class Assets implements Disposable {

    private static final String FONT_FILE = "fonts/NotoSans-Regular.ttf";

    /**
     * Nomes dos assets dentro do gerenciador. O sufixo {@code .ttf} não é
     * decorativo: é por ele que o {@link AssetManager} escolhe o carregador de
     * fonte, então três tamanhos da mesma fonte precisam de três nomes que
     * terminem assim.
     */
    private static final String TITLE = "hud-title.ttf";
    private static final String BODY = "hud-body.ttf";
    private static final String CAPTION = "hud-caption.ttf";

    private static final int TITLE_SIZE = 22;
    private static final int BODY_SIZE = 15;
    private static final int CAPTION_SIZE = 12;

    private final AssetManager manager = new AssetManager();

    public Assets() {
        FileHandleResolver resolver = new InternalFileHandleResolver();
        manager.setLoader(FreeTypeFontGenerator.class, new FreeTypeFontGeneratorLoader(resolver));
        manager.setLoader(BitmapFont.class, ".ttf", new FreetypeFontLoader(resolver));
    }

    /** Enfileira tudo. Não bloqueia: quem chama depois é {@link #update()}. */
    public void queue() {
        queueFont(TITLE, TITLE_SIZE);
        queueFont(BODY, BODY_SIZE);
        queueFont(CAPTION, CAPTION_SIZE);
    }

    private void queueFont(String name, int size) {
        FreetypeFontLoader.FreeTypeFontLoaderParameter parameter =
                new FreetypeFontLoader.FreeTypeFontLoaderParameter();
        parameter.fontFileName = FONT_FILE;
        parameter.fontParameters.size = size;
        // DEFAULT_CHARS cobre o Latin-1 acentuado, mas não o ponto médio nem o
        // travessão — e os dois aparecem no HUD. Caractere fora deste conjunto
        // não dá erro: some, o que é bem pior de notar.
        parameter.fontParameters.characters = FreeTypeFontGenerator.DEFAULT_CHARS + "·—…°%";
        // Filtro linear porque a janela pode ter escala de sistema diferente de
        // 1; sem isso a fonte fica serrilhada em telas HiDPI.
        parameter.fontParameters.minFilter = Texture.TextureFilter.Linear;
        parameter.fontParameters.magFilter = Texture.TextureFilter.Linear;
        manager.load(name, BitmapFont.class, parameter);
    }

    /** Avança o carregamento. Devolve true quando não há mais nada na fila. */
    public boolean update() {
        return manager.update();
    }

    /** 0 a 1, para a barra da tela de carregamento. */
    public float getProgress() {
        return manager.getProgress();
    }

    public BitmapFont getTitleFont() {
        return manager.get(TITLE, BitmapFont.class);
    }

    public BitmapFont getBodyFont() {
        return manager.get(BODY, BitmapFont.class);
    }

    public BitmapFont getCaptionFont() {
        return manager.get(CAPTION, BitmapFont.class);
    }

    /**
     * Descarta tudo. As fontes pertencem a esta classe, não à skin que as usa —
     * uma {@code Skin} descarta o que lhe foi adicionado, e deixar as duas
     * donas do mesmo objeto é descartá-lo duas vezes.
     */
    @Override
    public void dispose() {
        manager.dispose();
    }
}
