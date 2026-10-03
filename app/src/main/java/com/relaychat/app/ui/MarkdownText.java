package com.relaychat.app.ui;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Splits a reply into block and inline runs so the UI can style it without pulling in a Markdown
 * library. Only Android-free types are used here, which keeps the parser testable on a desktop JVM.
 */
public final class MarkdownText {
    public static final int BOLD = 1;
    public static final int ITALIC = 2;
    public static final int STRIKE = 4;
    public static final int CODE = 8;
    public static final int MATH = 16;
    public static final int MUTED = 32;

    public static final int PARAGRAPH = 0;
    public static final int HEADING = 1;
    public static final int CODE_BLOCK = 2;
    public static final int MATH_BLOCK = 3;
    public static final int QUOTE = 4;
    public static final int LIST_ITEM = 5;
    public static final int RULE = 6;
    public static final int TABLE = 7;

    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_CENTER = 1;
    public static final int ALIGN_RIGHT = 2;

    public static final class Piece {
        public final String text;
        public final int style;

        Piece(String text, int style) {
            this.text = text;
            this.style = style;
        }
    }

    public static final class Block {
        public final int kind;
        public final int level;
        public final List<Piece> pieces;
        public final Table table;

        Block(int kind, int level, List<Piece> pieces) {
            this(kind, level, pieces, null);
        }

        Block(int kind, int level, List<Piece> pieces, Table table) {
            this.kind = kind;
            this.level = level;
            this.pieces = pieces;
            this.table = table;
        }
    }

    /**
     * A parsed pipe table. Cells keep their inline styling and every column keeps the alignment its
     * separator row asked for, so the UI can draw a real grid instead of monospaced ASCII art.
     */
    public static final class Table {
        private final List<List<List<Piece>>> rows;
        private final int[] align;

        Table(List<List<List<Piece>>> rows, int[] align) {
            this.rows = rows;
            this.align = align;
        }

        public int rowCount() {
            return rows.size();
        }

        public int columns() {
            int columns = 0;
            for (List<List<Piece>> row : rows) {
                columns = Math.max(columns, row.size());
            }
            return columns;
        }

        public List<Piece> cell(int row, int column) {
            List<List<Piece>> cells = rows.get(row);
            return column < cells.size() ? cells.get(column) : Collections.emptyList();
        }

        public String plainCell(int row, int column) {
            return plainText(cell(row, column));
        }

        public int alignOf(int column) {
            return column < align.length ? align[column] : ALIGN_LEFT;
        }
    }

    private static final class Item {
        private final String marker;
        private final String body;
        private final int level;

        Item(String marker, String body, int level) {
            this.marker = marker;
            this.body = body;
            this.level = level;
        }
    }

    private MarkdownText() {
    }

    public static List<Block> parse(String source) {
        List<Block> blocks = new ArrayList<>();
        if (source == null || source.isEmpty()) {
            return blocks;
        }
        String[] lines = source.replace("\r\n", "\n").replace('\r', '\n').split("\n", -1);
        int index = 0;
        while (index < lines.length) {
            String trimmed = lines[index].trim();
            if (trimmed.isEmpty()) {
                index++;
                continue;
            }
            if (trimmed.startsWith("```") || trimmed.startsWith("~~~")) {
                String fence = trimmed.substring(0, 3);
                index++;
                List<Piece> pieces = new ArrayList<>();
                while (index < lines.length && !lines[index].trim().startsWith(fence)) {
                    pieces.add(new Piece(rstrip(lines[index]), CODE));
                    index++;
                }
                if (index < lines.length) {
                    index++;
                }
                blocks.add(new Block(CODE_BLOCK, 0, pieces));
                continue;
            }
            String closer = mathFenceCloser(trimmed);
            if (closer != null) {
                index++;
                List<Piece> pieces = new ArrayList<>();
                while (index < lines.length && !lines[index].trim().equals(closer)) {
                    pieces.add(new Piece(MathText.render(lines[index].trim()), MATH));
                    index++;
                }
                if (index < lines.length) {
                    index++;
                }
                blocks.add(new Block(MATH_BLOCK, 0, pieces));
                continue;
            }
            if (isInlineMathLine(trimmed)) {
                blocks.add(new Block(MATH_BLOCK, 0, mathPieces(inlineMathBody(trimmed))));
                index++;
                continue;
            }
            int hashes = headingLevel(trimmed);
            if (hashes > 0) {
                blocks.add(new Block(HEADING, hashes, inlineConverted(trimmed.substring(hashes).trim())));
                index++;
                continue;
            }
            if (isRule(trimmed)) {
                blocks.add(new Block(RULE, 0, singlePiece("\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500\u2500", MUTED)));
                index++;
                continue;
            }
            if (isTableRow(trimmed) && index + 1 < lines.length
                    && isTableSeparator(lines[index + 1].trim())) {
                int[] align = columnAlign(lines[index + 1].trim());
                List<List<List<Piece>>> rows = new ArrayList<>();
                rows.add(tableRow(trimmed));
                index += 2;
                while (index < lines.length && isTableRow(lines[index].trim())) {
                    rows.add(tableRow(lines[index].trim()));
                    index++;
                }
                blocks.add(new Block(TABLE, 0, Collections.emptyList(), new Table(rows, align)));
                continue;
            }
            if (trimmed.startsWith(">")) {
                blocks.add(new Block(QUOTE, 0, inlineConverted(stripQuote(trimmed))));
                index++;
                continue;
            }
            Item item = listItem(lines[index]);
            if (item != null) {
                List<Piece> pieces = new ArrayList<>();
                pieces.add(new Piece(item.marker + " ", 0));
                pieces.addAll(inlineConverted(item.body));
                blocks.add(new Block(LIST_ITEM, item.level, pieces));
                index++;
                continue;
            }
            blocks.add(new Block(PARAGRAPH, 0, inlineConverted(trimmed)));
            index++;
        }
        return blocks;
    }

    public static String toPlainText(List<Block> blocks) {
        StringBuilder out = new StringBuilder();
        for (Block block : blocks) {
            if (out.length() > 0) {
                out.append('\n');
            }
            if (block.kind == TABLE && block.table != null) {
                out.append(toPlainText(block.table));
                continue;
            }
            for (Piece piece : block.pieces) {
                out.append(piece.text);
            }
        }
        return out.toString();
    }

    /** Joins the runs of one cell or paragraph back into the text the model sent. */
    public static String plainText(List<Piece> pieces) {
        StringBuilder out = new StringBuilder();
        for (Piece piece : pieces) {
            out.append(piece.text);
        }
        return out.toString();
    }

    /** Renders one table back to pipe text, for copying or sharing. */
    public static String toPlainText(Table table) {
        StringBuilder out = new StringBuilder();
        for (int row = 0; row < table.rowCount(); row++) {
            if (row > 0) {
                out.append('\n');
            }
            for (int column = 0; column < table.columns(); column++) {
                if (column > 0) {
                    out.append(" | ");
                }
                out.append(table.plainCell(row, column));
            }
        }
        return out.toString();
    }

    private static String mathFenceCloser(String trimmed) {
        if (trimmed.equals("$$")) {
            return "$$";
        }
        if (trimmed.equals("\\[")) {
            return "\\]";
        }
        if (trimmed.equals("[")) {
            return "]";
        }
        return null;
    }

    private static boolean isInlineMathLine(String trimmed) {
        if (trimmed.length() <= 4) {
            return false;
        }
        if (trimmed.startsWith("$$") && trimmed.endsWith("$$")) {
            return true;
        }
        return trimmed.startsWith("\\[") && trimmed.endsWith("\\]");
    }

    private static String inlineMathBody(String trimmed) {
        return trimmed.substring(2, trimmed.length() - 2).trim();
    }

    private static List<Piece> mathPieces(String body) {
        List<Piece> pieces = new ArrayList<>();
        String[] parts = body.split("\n");
        for (String part : parts) {
            pieces.add(new Piece(MathText.render(part.trim()), MATH));
        }
        return pieces;
    }

    private static int headingLevel(String trimmed) {
        int count = 0;
        while (count < trimmed.length() && count < 6 && trimmed.charAt(count) == '#') {
            count++;
        }
        if (count == 0 || count + 1 >= trimmed.length()) {
            return 0;
        }
        return trimmed.charAt(count) == ' ' ? count : 0;
    }

    private static boolean isRule(String trimmed) {
        if (trimmed.length() < 3) {
            return false;
        }
        char marker = trimmed.charAt(0);
        if (marker != '-' && marker != '*' && marker != '_') {
            return false;
        }
        for (int position = 0; position < trimmed.length(); position++) {
            char current = trimmed.charAt(position);
            if (current != marker && current != ' ') {
                return false;
            }
        }
        return true;
    }

    private static boolean isTableRow(String trimmed) {
        return trimmed.indexOf('|') >= 0 && !isRule(trimmed);
    }

    private static boolean isTableSeparator(String trimmed) {
        if (trimmed.indexOf('-') < 0 || trimmed.indexOf('|') < 0) {
            return false;
        }
        for (int position = 0; position < trimmed.length(); position++) {
            char current = trimmed.charAt(position);
            if (current != '|' && current != '-' && current != ':' && current != ' ') {
                return false;
            }
        }
        return true;
    }

    private static List<String> splitTableRow(String trimmed) {
        String value = trimmed;
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        List<String> cells = new ArrayList<>();
        int start = 0;
        for (int position = 0; position <= value.length(); position++) {
            if (position == value.length() || value.charAt(position) == '|') {
                cells.add(value.substring(start, position).trim());
                start = position + 1;
            }
        }
        return cells;
    }

    private static List<List<Piece>> tableRow(String trimmed) {
        List<List<Piece>> cells = new ArrayList<>();
        for (String cell : splitTableRow(trimmed)) {
            cells.add(inlineConverted(cell));
        }
        return cells;
    }

    private static int[] columnAlign(String separator) {
        List<String> cells = splitTableRow(separator);
        int[] align = new int[cells.size()];
        for (int index = 0; index < cells.size(); index++) {
            String cell = cells.get(index);
            boolean left = cell.startsWith(":");
            boolean right = cell.endsWith(":");
            align[index] = left && right ? ALIGN_CENTER : right ? ALIGN_RIGHT : ALIGN_LEFT;
        }
        return align;
    }

    private static List<Piece> singlePiece(String text, int style) {
        List<Piece> pieces = new ArrayList<>();
        pieces.add(new Piece(text, style));
        return pieces;
    }

    private static String stripQuote(String trimmed) {
        String value = trimmed.substring(1);
        return value.startsWith(" ") ? value.substring(1).trim() : value.trim();
    }

    private static Item listItem(String line) {
        int start = 0;
        while (start < line.length() && line.charAt(start) == ' ') {
            start++;
        }
        String rest = line.substring(start);
        if (rest.length() >= 2 && rest.charAt(1) == ' '
                && (rest.charAt(0) == '-' || rest.charAt(0) == '*' || rest.charAt(0) == '+')) {
            String body = rest.substring(2).trim();
            String marker = "\u2022";
            if (body.startsWith("[ ] ")) {
                marker = "[ ]";
                body = body.substring(4).trim();
            } else if (body.length() > 4 && (body.startsWith("[x] ") || body.startsWith("[X] "))) {
                marker = "[x]";
                body = body.substring(4).trim();
            }
            return new Item(marker, body, Math.min(3, start / 2));
        }
        int digits = 0;
        while (digits < rest.length() && Character.isDigit(rest.charAt(digits))) {
            digits++;
        }
        if (digits > 0 && digits <= 3 && digits + 1 < rest.length()
                && (rest.charAt(digits) == '.' || rest.charAt(digits) == ')')
                && rest.charAt(digits + 1) == ' ') {
            return new Item(rest.substring(0, digits + 1), rest.substring(digits + 2).trim(),
                    Math.min(3, start / 2));
        }
        return null;
    }

    private static String rstrip(String value) {
        int end = value.length();
        while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(0, end);
    }

    private static List<Piece> inlineConverted(String text) {
        return convert(inline(text, 0));
    }
    private static List<Piece> inline(String text, int style) {
        List<Piece> pieces = new ArrayList<>();
        scan(text, style, pieces);
        List<Piece> merged = new ArrayList<>();
        for (Piece piece : pieces) {
            if (piece.text.isEmpty()) {
                continue;
            }
            if (!merged.isEmpty() && merged.get(merged.size() - 1).style == piece.style) {
                Piece previous = merged.remove(merged.size() - 1);
                merged.add(new Piece(previous.text + piece.text, piece.style));
                continue;
            }
            merged.add(piece);
        }
        return merged;
    }

    private static void scan(String text, int style, List<Piece> out) {
        StringBuilder literal = new StringBuilder();
        int index = 0;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '`') {
                int ticks = runLength(text, index, '`');
                int close = findRun(text, index + ticks, '`', ticks);
                if (close >= 0) {
                    out.add(new Piece(literal.toString(), style));
                    literal.setLength(0);
                    out.add(new Piece(text.substring(index + ticks, close), style | CODE));
                    index = close + ticks;
                    continue;
                }
            }
            if (current == '$' && index + 1 < text.length() && text.charAt(index + 1) == '$') {
                int close = text.indexOf("$$", index + 2);
                if (close > index + 2) {
                    out.add(new Piece(literal.toString(), style));
                    literal.setLength(0);
                    out.add(new Piece(text.substring(index + 2, close), style | MATH));
                    index = close + 2;
                    continue;
                }
            }
            if (current == '$') {
                int close = text.indexOf('$', index + 1);
                if (close > index + 1 && text.substring(index + 1, close).indexOf('\n') < 0
                        && !Character.isWhitespace(text.charAt(index + 1))
                        && !Character.isWhitespace(text.charAt(close - 1))) {
                    out.add(new Piece(literal.toString(), style));
                    literal.setLength(0);
                    out.add(new Piece(text.substring(index + 1, close), style | MATH));
                    index = close + 1;
                    continue;
                }
            }
            if (current == '\\' && index + 1 < text.length() && text.charAt(index + 1) == '(') {
                int close = text.indexOf("\\)", index + 2);
                if (close > index + 2) {
                    out.add(new Piece(literal.toString(), style));
                    literal.setLength(0);
                    out.add(new Piece(text.substring(index + 2, close), style | MATH));
                    index = close + 2;
                    continue;
                }
            }
            if (current == '*' || current == '_') {
                int run = runLength(text, index, current);
                int marker = run >= 2 ? 2 : 1;
                if (isEmphasisOpen(text, index, marker, current)) {
                    int close = findEmphasis(text, index + marker, current, marker);
                    if (close >= 0) {
                        out.add(new Piece(literal.toString(), style));
                        literal.setLength(0);
                        int inner = style | (marker == 2 ? BOLD : ITALIC);
                        scan(text.substring(index + marker, close), inner, out);
                        index = close + marker;
                        continue;
                    }
                }
            }
            if (current == '~' && index + 1 < text.length() && text.charAt(index + 1) == '~') {
                int close = text.indexOf("~~", index + 2);
                if (close > index + 2) {
                    out.add(new Piece(literal.toString(), style));
                    literal.setLength(0);
                    scan(text.substring(index + 2, close), style | STRIKE, out);
                    index = close + 2;
                    continue;
                }
            }
            if (current == '[') {
                int bracket = text.indexOf(']', index + 1);
                if (bracket > index + 1 && bracket + 1 < text.length()
                        && text.charAt(bracket + 1) == '(') {
                    int paren = text.indexOf(')', bracket + 2);
                    if (paren > bracket + 2) {
                        out.add(new Piece(literal.toString(), style));
                        literal.setLength(0);
                        scan(text.substring(index + 1, bracket), style, out);
                        out.add(new Piece(" (" + text.substring(bracket + 2, paren) + ")",
                                style | MUTED));
                        index = paren + 1;
                        continue;
                    }
                }
            }
            literal.append(current);
            index++;
        }
        out.add(new Piece(literal.toString(), style));
    }

    private static int runLength(String text, int from, char marker) {
        int count = 0;
        while (from + count < text.length() && text.charAt(from + count) == marker) {
            count++;
        }
        return count;
    }

    private static int findRun(String text, int from, char marker, int length) {
        int index = from;
        while (index < text.length()) {
            if (text.charAt(index) == marker && runLength(text, index, marker) >= length) {
                return index;
            }
            index++;
        }
        return -1;
    }

    private static boolean isEmphasisOpen(String text, int index, int marker, char symbol) {
        int after = index + marker;
        if (after >= text.length() || Character.isWhitespace(text.charAt(after))) {
            return false;
        }
        if (symbol != '_') {
            return true;
        }
        if (index == 0) {
            return true;
        }
        char before = text.charAt(index - 1);
        return !Character.isLetterOrDigit(before) && before != '_';
    }

    private static int findEmphasis(String text, int from, char symbol, int marker) {
        int index = from;
        while (index < text.length()) {
            char current = text.charAt(index);
            if (current == '\n') {
                return -1;
            }
            if (current == symbol && runLength(text, index, symbol) >= marker) {
                char before = text.charAt(index - 1);
                char after = index + marker < text.length() ? text.charAt(index + marker) : ' ';
                if (!Character.isWhitespace(before)
                        && (symbol != '_' || !Character.isLetterOrDigit(after))) {
                    return index;
                }
            }
            index++;
        }
        return -1;
    }

    private static List<Piece> convert(List<Piece> pieces) {
        List<Piece> converted = new ArrayList<>();
        for (Piece piece : pieces) {
            String text = (piece.style & CODE) != 0 ? piece.text : MathText.render(piece.text);
            converted.add(new Piece(text, piece.style));
        }
        return converted;
    }
}