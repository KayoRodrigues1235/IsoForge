package isoforge;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * O contorno da única regra do macOS que impede o jogo de abrir lá.
 *
 * <p>No macOS, toda biblioteca de janela (aqui, a GLFW que o LWJGL3 usa) só
 * pode criar janela a partir da <i>primeira</i> thread do processo. A JVM não
 * faz isso por padrão: é preciso iniciá-la com {@code -XstartOnFirstThread}.
 * Sem a flag o jogo não dá erro compreensível — ele simplesmente congela na
 * criação da janela, que é o pior tipo de falha para quem está só testando.
 *
 * <p>Exigir que o testador saiba da flag não é opção, porque duplo clique num
 * jar nunca a passa. Então o processo se relança: detecta que está no macOS sem
 * a flag, dispara uma segunda JVM com ela, repassa a saída e devolve o mesmo
 * código de encerramento. Em Linux e Windows este arquivo não faz nada — a
 * primeira verificação já sai fora.
 */
public final class MacStartup {

    /** Marca o processo-filho, para que um relance não gere outro relance. */
    private static final String MARCA = "isoforge.relancado";

    private MacStartup() {
    }

    /**
     * Relança a JVM com {@code -XstartOnFirstThread} quando for necessário.
     *
     * @return {@code true} se o jogo foi executado num processo-filho e este
     *         processo deve apenas terminar; {@code false} se cabe a quem
     *         chamou seguir e abrir a janela normalmente.
     */
    public static boolean relancarSeNecessario(String[] args) {
        if (!System.getProperty("os.name", "").toLowerCase().contains("mac")) {
            return false;
        }
        if (Boolean.getBoolean(MARCA)) {
            return false;
        }
        // A JVM define esta variável quando foi iniciada com a flag. É a única
        // forma confiável de saber: a flag não aparece nos argumentos da JVM.
        long pid = ProcessHandle.current().pid();
        if ("1".equals(System.getenv("JAVA_STARTED_ON_FIRST_THREAD_" + pid))) {
            return false;
        }

        String java = System.getProperty("java.home") + File.separator + "bin" + File.separator + "java";
        String classpath = System.getProperty("java.class.path");
        if (classpath == null || classpath.isEmpty()) {
            return false; // Sem classpath para repassar, tentar de novo só piora.
        }

        List<String> comando = new ArrayList<>();
        comando.add(java);
        comando.add("-XstartOnFirstThread");
        comando.add("-D" + MARCA + "=true");
        comando.add("-cp");
        comando.add(classpath);
        comando.add(DesktopLauncher.class.getName());
        comando.addAll(List.of(args));

        try {
            Process filho = new ProcessBuilder(comando).inheritIO().start();
            System.exit(filho.waitFor());
            return true;
        } catch (Exception e) {
            // Se o relance falha, tentar abrir a janela aqui mesmo dá ao menos
            // uma mensagem de erro do LWJGL, que é melhor que morrer em silêncio.
            System.err.println("Não foi possível relançar com -XstartOnFirstThread: " + e);
            return false;
        }
    }
}
