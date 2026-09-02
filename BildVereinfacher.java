import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.awt.image.IndexColorModel;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Vereinfacht ein Bild, indem aehnliche Pixelfarben zu einer gemeinsamen Farbe
 * zusammengefasst werden (Farbquantisierung). Dadurch sinkt die Anzahl der
 * unterschiedlichen Farben und die PNG-Datei wird deutlich kleiner.
 *
 * Aufruf:
 *   java BildVereinfacher <eingabe> <ausgabe.png> [toleranz]
 *
 * toleranz = erlaubter Farbabstand (0-255, Standard 32).
 *   klein : viele Farben, kaum sichtbarer Unterschied, wenig Ersparnis
 *   gross : wenige Farben, Poster-Effekt, starke Ersparnis
 */
public class BildVereinfacher {

    /** Gewichte fuer den Farbabstand (Auge ist fuer Gruen am empfindlichsten). */
    private static final double GEWICHT_ROT = 0.30;
    private static final double GEWICHT_GRUEN = 0.59;
    private static final double GEWICHT_BLAU = 0.11;

    public static void main(String[] args) throws IOException {
        if (args.length < 2) {
            System.out.println("Aufruf: java BildVereinfacher <eingabe> <ausgabe.png> [toleranz]");
            System.out.println("Beispiel: java BildVereinfacher foto.jpg klein.png 32");
            return;
        }

        File eingabe = new File(args[0]);
        File ausgabe = new File(args[1]);
        int toleranz = args.length > 2 ? Integer.parseInt(args[2]) : 32;

        BufferedImage original = ImageIO.read(eingabe);
        if (original == null) {
            System.out.println("Fehler: '" + eingabe + "' konnte nicht als Bild gelesen werden.");
            return;
        }

        BufferedImage rgb = alsRgb(original);
        int farbenVorher = zaehleFarben(rgb);

        // 1. Palette aus aehnlichen Farben aufbauen
        List<Integer> palette = ersellePalette(rgb, toleranz);

        // 2. Jeden Pixel auf die passende Palettenfarbe umbiegen
        BufferedImage vereinfacht = wendePaletteAn(rgb, palette);

        // 3. Speichern - bei <= 256 Farben als indiziertes PNG (spart am meisten)
        if (palette.size() <= 256) {
            vereinfacht = alsIndiziert(vereinfacht, palette);
        }
        ImageIO.write(vereinfacht, "png", ausgabe);

        // 4. Bericht
        long groesseVorher = eingabe.length();
        long groesseNachher = ausgabe.length();
        System.out.println("Bild:      " + rgb.getWidth() + " x " + rgb.getHeight() + " Pixel");
        System.out.println("Toleranz:  " + toleranz);
        System.out.println("Farben:    " + farbenVorher + "  ->  " + palette.size());
        System.out.printf("Dateigroesse: %,d Byte  ->  %,d Byte  (%.1f %% Ersparnis)%n",
                groesseVorher, groesseNachher,
                100.0 * (groesseVorher - groesseNachher) / groesseVorher);
        System.out.println("Gespeichert: " + ausgabe.getPath());
    }

    /**
     * Baut die Farbpalette auf: haeufigste Farben zuerst. Eine neue Farbe wird
     * nur dann in die Palette aufgenommen, wenn sie zu allen bisherigen
     * Palettenfarben weiter entfernt ist als die Toleranz.
     */
    private static List<Integer> ersellePalette(BufferedImage bild, int toleranz) {
        Map<Integer, Integer> haeufigkeit = new HashMap<>();
        for (int y = 0; y < bild.getHeight(); y++) {
            for (int x = 0; x < bild.getWidth(); x++) {
                int farbe = bild.getRGB(x, y) & 0xFFFFFF;
                haeufigkeit.merge(farbe, 1, Integer::sum);
            }
        }

        List<Integer> sortiert = new ArrayList<>(haeufigkeit.keySet());
        sortiert.sort((a, b) -> haeufigkeit.get(b) - haeufigkeit.get(a));

        double grenze = (double) toleranz * toleranz;
        List<Integer> palette = new ArrayList<>();
        for (int farbe : sortiert) {
            boolean passtZuVorhandener = false;
            for (int p : palette) {
                if (abstandQuadrat(farbe, p) <= grenze) {
                    passtZuVorhandener = true;
                    break;
                }
            }
            if (!passtZuVorhandener) {
                palette.add(farbe);
            }
        }
        return palette;
    }

    /** Ersetzt jeden Pixel durch die naechstgelegene Palettenfarbe. */
    private static BufferedImage wendePaletteAn(BufferedImage bild, List<Integer> palette) {
        BufferedImage ergebnis = new BufferedImage(
                bild.getWidth(), bild.getHeight(), BufferedImage.TYPE_INT_RGB);

        // Cache, damit gleiche Farben nicht mehrfach gesucht werden
        Map<Integer, Integer> cache = new HashMap<>();

        for (int y = 0; y < bild.getHeight(); y++) {
            for (int x = 0; x < bild.getWidth(); x++) {
                int farbe = bild.getRGB(x, y) & 0xFFFFFF;
                Integer neu = cache.get(farbe);
                if (neu == null) {
                    neu = naechsteFarbe(farbe, palette);
                    cache.put(farbe, neu);
                }
                ergebnis.setRGB(x, y, neu);
            }
        }
        return ergebnis;
    }

    /** Sucht die Palettenfarbe mit dem kleinsten Abstand. */
    private static int naechsteFarbe(int farbe, List<Integer> palette) {
        int beste = palette.get(0);
        double besterAbstand = Double.MAX_VALUE;
        for (int p : palette) {
            double abstand = abstandQuadrat(farbe, p);
            if (abstand < besterAbstand) {
                besterAbstand = abstand;
                beste = p;
                if (abstand == 0) {
                    break;
                }
            }
        }
        return beste;
    }

    /** Gewichteter quadratischer Farbabstand zweier RGB-Werte. */
    private static double abstandQuadrat(int farbe1, int farbe2) {
        int dr = ((farbe1 >> 16) & 0xFF) - ((farbe2 >> 16) & 0xFF);
        int dg = ((farbe1 >> 8) & 0xFF) - ((farbe2 >> 8) & 0xFF);
        int db = (farbe1 & 0xFF) - (farbe2 & 0xFF);
        return GEWICHT_ROT * dr * dr + GEWICHT_GRUEN * dg * dg + GEWICHT_BLAU * db * db;
    }

    /** Zaehlt die unterschiedlichen Farben im Bild. */
    private static int zaehleFarben(BufferedImage bild) {
        Map<Integer, Boolean> gesehen = new HashMap<>();
        for (int y = 0; y < bild.getHeight(); y++) {
            for (int x = 0; x < bild.getWidth(); x++) {
                gesehen.put(bild.getRGB(x, y) & 0xFFFFFF, Boolean.TRUE);
            }
        }
        return gesehen.size();
    }

    /** Wandelt beliebige Eingabeformate in ein einheitliches RGB-Bild um. */
    private static BufferedImage alsRgb(BufferedImage bild) {
        if (bild.getType() == BufferedImage.TYPE_INT_RGB) {
            return bild;
        }
        BufferedImage rgb = new BufferedImage(
                bild.getWidth(), bild.getHeight(), BufferedImage.TYPE_INT_RGB);
        rgb.getGraphics().drawImage(bild, 0, 0, java.awt.Color.WHITE, null);
        return rgb;
    }

    /**
     * Speichert das Bild mit 8 Bit pro Pixel statt 24 Bit. Das ist der
     * eigentliche Speicher-Gewinn: nur noch ein Palettenindex pro Pixel.
     */
    private static BufferedImage alsIndiziert(BufferedImage bild, List<Integer> palette) {
        int n = palette.size();
        byte[] r = new byte[n];
        byte[] g = new byte[n];
        byte[] b = new byte[n];
        for (int i = 0; i < n; i++) {
            int farbe = palette.get(i);
            r[i] = (byte) ((farbe >> 16) & 0xFF);
            g[i] = (byte) ((farbe >> 8) & 0xFF);
            b[i] = (byte) (farbe & 0xFF);
        }

        int bits = Math.max(1, 32 - Integer.numberOfLeadingZeros(Math.max(1, n - 1)));
        if (bits > 4) {
            bits = 8;
        } else if (bits > 2) {
            bits = 4;
        }

        IndexColorModel modell = new IndexColorModel(bits, n, r, g, b);
        BufferedImage indiziert = new BufferedImage(
                bild.getWidth(), bild.getHeight(),
                bits <= 4 ? BufferedImage.TYPE_BYTE_BINARY : BufferedImage.TYPE_BYTE_INDEXED,
                modell);
        indiziert.getGraphics().drawImage(bild, 0, 0, null);
        return indiziert;
    }
}
