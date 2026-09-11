package isoforge.ui;

import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.BitmapFont;
import com.badlogic.gdx.graphics.g2d.NinePatch;
import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.NinePatchDrawable;
import isoforge.assets.Assets;

/**
 * A skin do HUD, desenhada em código em vez de carregada de arquivo.
 *
 * <p>Uma skin do Scene2D normalmente é um atlas mais um JSON mais uma fonte —
 * ou seja, três assets, num projeto que ainda não tem pasta de assets. Aqui os
 * fundos e as bordas saem de {@link Pixmap}s de oito por oito pixels montados
 * em tempo de execução, e as fontes são a padrão do libGDX em três escalas.
 * Nada disso precisa existir em disco.
 *
 * <p>É uma escolha de sequência, não de gosto. O HUD ganha widgets de verdade
 * — botões que se pintam ao passar o mouse, tabelas que se reorganizam sozinhas
 * ao redimensionar a janela — <b>agora</b>, sem esperar o pipeline de assets. E
 * quando o pipeline existir, trocar esta classe por uma skin empacotada não
 * mexe em uma linha de layout, porque o layout fala com nomes de estilo, não
 * com arquivos.
 *
 * <p>As fontes, essas vêm de disco — de {@link Assets}, geradas pelo FreeType
 * no tamanho exato de cada uso. É a divisão que faz sentido: um retângulo com
 * borda de um pixel não ganha nada em virar arquivo, enquanto uma fonte
 * desenhada por alguém ganha tudo. A skin não é dona delas e não as descarta.
 */
public final class ProceduralSkin {

    /** Fundo dos painéis: escuro e translúcido, para o mundo continuar visível atrás. */
    private static final Color PANEL_FILL = new Color(0.055f, 0.065f, 0.090f, 0.88f);
    private static final Color PANEL_BORDER = new Color(1f, 1f, 1f, 0.10f);

    private static final Color BUTTON_FILL = new Color(0.145f, 0.165f, 0.205f, 0.95f);
    private static final Color BUTTON_BORDER = new Color(1f, 1f, 1f, 0.14f);
    private static final Color BUTTON_OVER_FILL = new Color(0.215f, 0.245f, 0.300f, 0.98f);
    private static final Color BUTTON_DOWN_FILL = new Color(0.090f, 0.105f, 0.135f, 1f);

    /**
     * Âmbar. É a cor que o jogo já usa para o depósito e para o rastro das
     * unidades — a interface pega emprestado em vez de inventar um destaque
     * novo, para o olho ligar as duas coisas.
     */
    private static final Color ACCENT = new Color(0.95f, 0.75f, 0.25f, 1f);
    private static final Color ACCENT_FILL = new Color(0.42f, 0.32f, 0.11f, 0.96f);
    private static final Color ACCENT_BORDER = new Color(0.95f, 0.75f, 0.25f, 0.55f);

    public static final Color TEXT = new Color(0.88f, 0.90f, 0.93f, 1f);
    public static final Color TEXT_MUTED = new Color(0.56f, 0.60f, 0.67f, 1f);
    public static final Color TEXT_ACCENT = ACCENT;
    public static final Color TEXT_WARN = new Color(0.93f, 0.55f, 0.35f, 1f);

    private ProceduralSkin() {
    }

    /**
     * Monta a skin. O chamador é dono dela e deve chamar {@code dispose()}, que
     * cuida das texturas geradas aqui — mas não das fontes, que são do
     * {@link Assets}.
     */
    public static Skin build(Assets assets) {
        Skin skin = new Skin();

        // As fontes não são registradas na skin: skin.dispose() descartaria o
        // que lhe foi adicionado, e elas pertencem ao AssetManager. Os estilos
        // guardam a referência, que é tudo de que precisam.
        BitmapFont title = assets.getTitleFont();
        BitmapFont body = assets.getBodyFont();
        BitmapFont caption = assets.getCaptionFont();

        NinePatchDrawable panel = bordered(skin, "panel", PANEL_FILL, PANEL_BORDER);
        NinePatchDrawable buttonUp = bordered(skin, "button-up", BUTTON_FILL, BUTTON_BORDER);
        NinePatchDrawable buttonOver = bordered(skin, "button-over", BUTTON_OVER_FILL, BUTTON_BORDER);
        NinePatchDrawable buttonDown = bordered(skin, "button-down", BUTTON_DOWN_FILL, BUTTON_BORDER);
        NinePatchDrawable buttonChecked = bordered(skin, "button-checked", ACCENT_FILL, ACCENT_BORDER);

        // Registrar sob Drawable.class é obrigatório, não estilo. skin.add(nome,
        // objeto) guarda sob a classe concreta — NinePatchDrawable — e
        // skin.getDrawable() procura por Drawable.class, não acha, e cai num
        // fallback que monta um TextureRegionDrawable a partir de uma textura
        // de mesmo nome. O painel apareceria com a imagem 8x8 esticada inteira,
        // bordas e tudo, em vez do NinePatch. Os botões escapavam disso porque
        // recebem o drawable direto, sem passar pelo getDrawable.
        skin.add("panel", panel, com.badlogic.gdx.scenes.scene2d.utils.Drawable.class);

        skin.add("title", new Label.LabelStyle(title, TEXT));
        skin.add("default", new Label.LabelStyle(body, TEXT));
        skin.add("caption", new Label.LabelStyle(caption, TEXT_MUTED));

        TextButton.TextButtonStyle button = new TextButton.TextButtonStyle();
        button.up = buttonUp;
        button.over = buttonOver;
        button.down = buttonDown;
        button.checked = buttonChecked;
        button.font = body;
        button.fontColor = TEXT;
        button.overFontColor = new Color(1f, 1f, 1f, 1f);
        button.downFontColor = TEXT_MUTED;
        button.checkedFontColor = ACCENT;
        skin.add("default", button);

        return skin;
    }

    /**
     * Um retângulo de preenchimento com borda de um pixel, virado NinePatch
     * para poder esticar em qualquer tamanho sem engordar a borda.
     *
     * <p>A borda e o fundo são pintados no pixmap em vez de saírem de uma
     * tintura: tingir um NinePatch tingiria os dois com a mesma cor, e é
     * justamente o contraste entre eles que faz o painel ter contorno.
     */
    private static NinePatchDrawable bordered(Skin skin, String name, Color fill, Color border) {
        Pixmap pixmap = new Pixmap(8, 8, Pixmap.Format.RGBA8888);
        pixmap.setBlending(Pixmap.Blending.None);
        pixmap.setColor(fill);
        pixmap.fill();
        pixmap.setColor(border);
        pixmap.drawRectangle(0, 0, 8, 8);

        Texture texture = new Texture(pixmap);
        pixmap.dispose();
        // Registrar na skin é o que faz skin.dispose() cuidar desta textura. O
        // sufixo evita colidir com o nome do drawable — nomes iguais em mapas
        // diferentes é exatamente a armadilha descrita em build().
        skin.add(name + ".texture", texture, Texture.class);

        return new NinePatchDrawable(new NinePatch(new TextureRegion(texture), 1, 1, 1, 1));
    }
}
