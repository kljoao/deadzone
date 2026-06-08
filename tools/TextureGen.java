import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Gera a textura 16x16 da crowbar (Pé de Cabra) do zero.
 * Layout: metade esquerda = metal (claw/chisel), metade direita = vermelho (corpo).
 * Rode com:  java tools/TextureGen.java
 */
public class TextureGen {

    public static void main(String[] args) throws Exception {
        int s = 16;
        BufferedImage img = new BufferedImage(s, s, BufferedImage.TYPE_INT_ARGB);

        for (int y = 0; y < s; y++) {
            for (int x = 0; x < s; x++) {
                int noise = ((x * 7 + y * 13) % 5) - 2; // -2..+2, leve granulado
                int argb;
                if (x < 8) {
                    // metal escuro (claw/chisel)
                    int base = 74 + noise * 4;
                    int r = clamp(base - 4);
                    int g = clamp(base);
                    int b = clamp(base + 6);
                    if (x == 0 || x == 7 || y == 0 || y == 15) {
                        r = clamp(r - 18); g = clamp(g - 18); b = clamp(b - 18); // contorno
                    }
                    argb = argb(r, g, b);
                } else {
                    // vermelho (corpo da crowbar)
                    int r = clamp(168 + noise * 6);
                    int g = clamp(26 + noise * 3);
                    int b = clamp(26 + noise * 3);
                    if (x == 8 || x == 15 || y == 0 || y == 15) {
                        r = clamp(r - 40); g = clamp(g - 10); b = clamp(b - 10); // contorno
                    }
                    argb = argb(r, g, b);
                }
                img.setRGB(x, y, argb);
            }
        }

        File out = new File("resourcepack/assets/deadzone/textures/item/pe_de_cabra.png");
        out.getParentFile().mkdirs();
        ImageIO.write(img, "PNG", out);
        System.out.println("Textura gerada em: " + out.getAbsolutePath());
    }

    private static int clamp(int v) {
        return Math.max(0, Math.min(255, v));
    }

    private static int argb(int r, int g, int b) {
        return (0xFF << 24) | (r << 16) | (g << 8) | b;
    }
}
