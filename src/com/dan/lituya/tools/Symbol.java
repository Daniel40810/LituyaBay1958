package com.dan.lituya.tools;

import com.dan.lituya.ui.AppIcon;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 20 (F43 A): das gemalte Programmsymbol ({@link AppIcon#paint}) als PNGs ins Jar
 * ({@code src/com/dan/lituya/ui/icon/lituya_NN.png}) und als {@code icon/LituyaBay1958.ico} für die Verknüpfung
 * (je Größe ein PNG im ICO, wie es Windows seit Vista liest). Aufruf: {@code Symbol [src-Ordner] [ico-Datei]}.
 */
public final class Symbol {
    private Symbol() { }

    public static void main(String[] a) throws IOException {
        File src = new File(a.length > 0 ? a[0] : "src/com/dan/lituya/ui/icon");
        File ico = new File(a.length > 1 ? a[1] : "icon/LituyaBay1958.ico");
        if (!src.isDirectory() && !src.mkdirs()) throw new IOException("Ordner " + src + " lässt sich nicht anlegen");
        List<byte[]> pngs = new ArrayList<>();
        for (int s : AppIcon.SIZES) {
            byte[] p = png(AppIcon.paint(s));
            Files.write(new File(src, "lituya_" + s + ".png").toPath(), p);
            pngs.add(p);
        }
        if (ico.getAbsoluteFile().getParentFile() != null) ico.getAbsoluteFile().getParentFile().mkdirs();
        Files.write(ico.toPath(), ico(AppIcon.SIZES, pngs));
        System.out.println("Symbol: " + AppIcon.SIZES.length + " Größen nach " + src.getPath() + ", " + ico.getPath() + " (" + ico.length() + " Byte)");
    }

    static byte[] png(BufferedImage im) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ImageIO.write(im, "png", o);
        return o.toByteArray();
    }

    /** Eine ICO-Datei aus PNGs (Größen ≤ 256). */
    public static byte[] ico(int[] sizes, List<byte[]> pngs) {
        int n = sizes.length, off = 6 + 16 * n, len = off;
        for (byte[] p : pngs) len += p.length;
        ByteBuffer b = ByteBuffer.allocate(len).order(ByteOrder.LITTLE_ENDIAN);
        b.putShort((short) 0).putShort((short) 1).putShort((short) n);
        for (int i = 0; i < n; i++) {
            int s = sizes[i];
            b.put((byte) (s >= 256 ? 0 : s)).put((byte) (s >= 256 ? 0 : s)).put((byte) 0).put((byte) 0);
            b.putShort((short) 1).putShort((short) 32).putInt(pngs.get(i).length).putInt(off);
            off += pngs.get(i).length;
        }
        for (byte[] p : pngs) b.put(p);
        return b.array();
    }
}
