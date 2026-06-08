import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Empacota a pasta resourcepack/ em dist/deadzone-resourcepack.zip
 * com entradas usando barras "/" (compatível com Minecraft).
 * Rode com:  java tools/ZipPack.java
 */
public class ZipPack {

    public static void main(String[] args) throws IOException {
        Path src = Paths.get("resourcepack");
        Path zip = Paths.get("dist", "deadzone-resourcepack.zip");
        Files.createDirectories(zip.getParent());
        Files.deleteIfExists(zip);

        try (ZipOutputStream zos = new ZipOutputStream(new BufferedOutputStream(Files.newOutputStream(zip)));
             Stream<Path> walk = Files.walk(src)) {
            walk.filter(Files::isRegularFile).forEach(p -> {
                String name = src.relativize(p).toString().replace('\\', '/');
                try {
                    zos.putNextEntry(new ZipEntry(name));
                    Files.copy(p, zos);
                    zos.closeEntry();
                } catch (IOException e) {
                    throw new UncheckedIOException(e);
                }
            });
        }
        System.out.println("OK: " + zip.toAbsolutePath());
    }
}
