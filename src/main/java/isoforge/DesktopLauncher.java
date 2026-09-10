package isoforge;

import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;

/**
 * Ponto de entrada do desktop.
 *
 * <p>O libGDX separa o jogo (que é só uma {@code ApplicationListener}) do
 * backend que abre a janela e cria o contexto OpenGL. Essa separação é o que
 * permitiria rodar o mesmo {@link IsoForgeGame} no Android ou no navegador sem
 * tocar na lógica — não é o plano aqui, mas explica por que o main é tão magro.
 */
public final class DesktopLauncher {

    private DesktopLauncher() {
    }

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("IsoForge");
        config.setWindowedMode(1280, 720);
        config.useVsync(true);
        config.setForegroundFPS(60);
        config.setBackBufferConfig(8, 8, 8, 8, 16, 0, 4); // 4x MSAA: suaviza a borda dos losangos

        new Lwjgl3Application(new IsoForgeGame(), config);
    }
}
