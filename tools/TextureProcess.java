import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Processa uma textura "crua" (alta resolução, fundo branco) para uso no Minecraft:
 *  - torna o fundo branco transparente
 *  - redimensiona para um quadrado potência de 2
 *
 * Uso:  java tools/TextureProcess.java <input.png> <output.png> [size] [whiteThreshold]
 *   size           padrão 64
 *   whiteThreshold padrão 244 (pixels com R,G,B >= threshold viram transparentes)
 */
public class TextureProcess {

    public static void main(String[] args) throws Exception {
        if (args.length < 2) {
            System.out.println("Uso: java tools/TextureProcess.java <input.png> <output.png> [size] [whiteThreshold]");
            return;
        }
        String input = args[0];
        String output = args[1];
        int size = args.length >= 3 ? Integer.parseInt(args[2]) : 64;
        int threshold = args.length >= 4 ? Integer.parseInt(args[3]) : 244;

        BufferedImage src = ImageIO.read(new File(input));
        if (src == null) {
            System.out.println("Não consegui ler a imagem: " + input);
            return;
        }

        // 1) remove fundo branco -> alpha
        BufferedImage cleaned = new BufferedImage(src.getWidth(), src.getHeight(), BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < src.getHeight(); y++) {
            for (int x = 0; x < src.getWidth(); x++) {
                int argb = src.getRGB(x, y);
                int a = (argb >>> 24) & 0xFF;
                int r = (argb >> 16) & 0xFF;
                int g = (argb >> 8) & 0xFF;
                int b = argb & 0xFF;
                if (a > 0 && r >= threshold && g >= threshold && b >= threshold) {
                    cleaned.setRGB(x, y, 0x00000000); // transparente
                } else {
                    cleaned.setRGB(x, y, argb);
                }
            }
        }

        // 2) redimensiona para size x size
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g2 = out.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g2.drawImage(cleaned, 0, 0, size, size, null);
        g2.dispose();

        File outFile = new File(output);
        if (outFile.getParentFile() != null) {
            outFile.getParentFile().mkdirs();
        }
        ImageIO.write(out, "PNG", outFile);
        System.out.println("OK -> " + outFile.getAbsolutePath() + " (" + size + "x" + size + ")");
    }
}
