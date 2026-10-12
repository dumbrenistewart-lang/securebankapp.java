package com.securebank.pdf;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class PdfWriter {

    public static final int PAGE_WIDTH = 595;
    public static final int PAGE_HEIGHT = 842;

    public enum Font {
        REGULAR("F1", "Helvetica"), BOLD("F2", "Helvetica-Bold"), MONO("F3", "Courier");
        final String id;
        final String baseName;
        Font(String id, String baseName) { this.id = id; this.baseName = baseName; }
    }

    private final List<StringBuilder> pages = new ArrayList<>();
    private StringBuilder current;
    private String title = "Document";

    public PdfWriter() { newPage(); }

    public void setTitle(String title) { this.title = title; }

    public void newPage() {
        current = new StringBuilder();
        pages.add(current);
    }

    public int pageCount() { return pages.size(); }

    public void text(Font font, float size, float x, float y, String s) {
        current.append("BT /").append(font.id).append(' ').append(num(size)).append(" Tf ")
               .append(num(x)).append(' ').append(num(y)).append(" Td (")
               .append(escape(s)).append(") Tj ET\n");
    }

    public void line(float x1, float y1, float x2, float y2, float width) {
        current.append(num(width)).append(" w ").append(num(x1)).append(' ').append(num(y1)).append(" m ")
               .append(num(x2)).append(' ').append(num(y2)).append(" l S\n");
    }

    public void rect(float x, float y, float w, float h, float gray) {
        current.append("q ").append(num(gray)).append(" g ").append(num(x)).append(' ').append(num(y)).append(' ')
               .append(num(w)).append(' ').append(num(h)).append(" re f Q\n");
    }

    public void writeTo(Path file) throws IOException { Files.write(file, toBytes()); }

    public byte[] toBytes() {
        int n = pages.size();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        List<Integer> offsets = new ArrayList<>();

        put(out, "%PDF-1.4\n%\u00e2\u00e3\u00cf\u00d3\n");

        obj(out, offsets, "<< /Type /Catalog /Pages 2 0 R >>");
        StringBuilder kids = new StringBuilder();
        for (int i = 0; i < n; i++) kids.append(7 + 2 * i).append(" 0 R ");
        obj(out, offsets, "<< /Type /Pages /Kids [" + kids + "] /Count " + n + " >>");
        for (Font f : Font.values()) {
            obj(out, offsets, "<< /Type /Font /Subtype /Type1 /BaseFont /" + f.baseName
                    + " /Encoding /WinAnsiEncoding >>");
        }
        obj(out, offsets, "<< /Title (" + escape(title) + ") /Producer (SecureBankApp) >>");

        for (int i = 0; i < n; i++) {
            int contentObj = 8 + 2 * i;
            obj(out, offsets, "<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + PAGE_WIDTH + " " + PAGE_HEIGHT
                    + "] /Resources << /Font << /F1 3 0 R /F2 4 0 R /F3 5 0 R >> >> /Contents "
                    + contentObj + " 0 R >>");
            String content = pages.get(i).toString();
            int len = content.getBytes(StandardCharsets.ISO_8859_1).length;
            obj(out, offsets, "<< /Length " + len + " >>\nstream\n" + content + "endstream");
        }

        int xrefPos = out.size();
        int total = offsets.size() + 1;
        put(out, "xref\n0 " + total + "\n0000000000 65535 f \n");
        for (int off : offsets) put(out, String.format(Locale.ROOT, "%010d 00000 n \n", off));
        put(out, "trailer\n<< /Size " + total + " /Root 1 0 R /Info 6 0 R >>\nstartxref\n" + xrefPos + "\n%%EOF\n");
        return out.toByteArray();
    }

    private static void obj(ByteArrayOutputStream out, List<Integer> offsets, String body) {
        offsets.add(out.size());
        put(out, offsets.size() + " 0 obj\n" + body + "\nendobj\n");
    }

    private static void put(ByteArrayOutputStream out, String s) {
        byte[] b = s.getBytes(StandardCharsets.ISO_8859_1);
        out.write(b, 0, b.length);
    }

    private static String num(float f) { return String.format(Locale.ROOT, "%.2f", f); }

    static String escape(String s) {
        StringBuilder sb = new StringBuilder();
        for (char c : s.toCharArray()) {
            if (c == '\\' || c == '(' || c == ')') sb.append('\\').append(c);
            else if (c < 32 || c > 126) sb.append('?');
            else sb.append(c);
        }
        return sb.toString();
    }
}
