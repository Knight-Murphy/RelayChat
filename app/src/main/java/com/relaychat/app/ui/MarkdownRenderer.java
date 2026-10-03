package com.relaychat.app.ui;

import android.graphics.Color;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.LeadingMarginSpan;
import android.text.style.QuoteSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.StrikethroughSpan;
import android.text.style.StyleSpan;
import android.text.style.TypefaceSpan;

import java.util.List;

/** Styles the runs produced by {@link MarkdownText} for a single message bubble. */
public final class MarkdownRenderer {
    private static final int SPAN_FLAGS = android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
    private static final String MONOSPACE = "monospace";
    private static final String SERIF = "serif";

    /** Colors and scaling used while styling one bubble. */
    public static final class Palette {
        public final float density;
        public final int text;
        public final int muted;
        public final int codeBackground;
        public final int codeText;
        public final int math;
        public final int accent;
        public final int tableBase;
        public final int tableHeader;
        public final int tableStripe;
        public final int tableBorder;
        public final int tableDivider;

        Palette(float density, int text, int muted, int codeBackground, int codeText, int math,
                int accent, int tableBase, int tableHeader, int tableStripe, int tableBorder,
                int tableDivider) {
            this.density = density;
            this.text = text;
            this.muted = muted;
            this.codeBackground = codeBackground;
            this.codeText = codeText;
            this.math = math;
            this.accent = accent;
            this.tableBase = tableBase;
            this.tableHeader = tableHeader;
            this.tableStripe = tableStripe;
            this.tableBorder = tableBorder;
            this.tableDivider = tableDivider;
        }

        public static Palette assistant(float density) {
            return new Palette(density, UiKit.INK, UiKit.MUTED, Color.rgb(236, 241, 237),
                    Color.rgb(23, 58, 52), Color.rgb(34, 62, 57), UiKit.ACCENT,
                    Color.rgb(252, 253, 252), Color.rgb(240, 245, 242), Color.rgb(248, 251, 249),
                    UiKit.BORDER, Color.rgb(231, 236, 232));
        }

        public static Palette user(float density) {
            return new Palette(density, Color.WHITE, 0xE6FFFFFF, 0x38FFFFFF, Color.WHITE,
                    Color.WHITE, Color.WHITE, 0x1FFFFFFF, 0x33FFFFFF, 0x14FFFFFF, 0x59FFFFFF,
                    0x3DFFFFFF);
        }

        int dp(float value) {
            return Math.round(value * density);
        }
    }

    private MarkdownRenderer() {
    }

    public static CharSequence render(String source, Palette palette) {
        return render(MarkdownText.parse(source), palette);
    }

    /** Styles the text blocks of one reply; tables are drawn by {@link MarkdownView}. */
    public static CharSequence render(List<MarkdownText.Block> blocks, Palette palette) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        int previousKind = -1;
        for (MarkdownText.Block block : blocks) {
            if (out.length() > 0) {
                trimTrailingNewlines(out);
                out.append(blankLineBetween(previousKind, block.kind) ? "\n\n" : "\n");
            }
            appendBlock(out, block, palette);
            previousKind = block.kind;
        }
        trimTrailingNewlines(out);
        return out;
    }

    private static void appendBlock(SpannableStringBuilder out, MarkdownText.Block block,
                                    Palette palette) {
        if (block.kind == MarkdownText.TABLE && block.table != null) {
            appendTableFallback(out, block.table, palette);
            return;
        }
        int start = out.length();
        for (int index = 0; index < block.pieces.size(); index++) {
            if (index > 0 && block.kind == MarkdownText.MATH_BLOCK) {
                out.append('\n');
            }
            MarkdownText.Piece piece = block.pieces.get(index);
            int pieceStart = out.length();
            out.append(piece.text);
            int pieceEnd = out.length();
            if (block.kind == MarkdownText.CODE_BLOCK) {
                out.setSpan(new TypefaceSpan(MONOSPACE), pieceStart, pieceEnd, SPAN_FLAGS);
                out.setSpan(new BackgroundColorSpan(palette.codeBackground), pieceStart, pieceEnd,
                        SPAN_FLAGS);
                out.setSpan(new ForegroundColorSpan(palette.codeText), pieceStart, pieceEnd,
                        SPAN_FLAGS);
            } else {
                applyPieceStyle(out, pieceStart, pieceEnd, piece.style, palette);
            }
            if (block.kind == MarkdownText.CODE_BLOCK) {
                out.append('\n');
            }
        }
        int end = out.length();
        if (end <= start) {
            return;
        }
        switch (block.kind) {
            case MarkdownText.HEADING:
                out.setSpan(new StyleSpan(Typeface.BOLD), start, end, SPAN_FLAGS);
                out.setSpan(new RelativeSizeSpan(headingScale(block.level)), start, end, SPAN_FLAGS);
                break;
            case MarkdownText.QUOTE:
                out.setSpan(new QuoteSpan(palette.muted), start, end, SPAN_FLAGS);
                out.setSpan(new ForegroundColorSpan(palette.muted), start, end, SPAN_FLAGS);
                break;
            case MarkdownText.LIST_ITEM:
                out.setSpan(new LeadingMarginSpan.Standard(0, palette.dp(15) * (block.level + 1)),
                        start, end, SPAN_FLAGS);
                break;
            case MarkdownText.MATH_BLOCK:
                out.setSpan(new LeadingMarginSpan.Standard(palette.dp(12), palette.dp(12)),
                        start, end, SPAN_FLAGS);
                out.setSpan(new TypefaceSpan(SERIF), start, end, SPAN_FLAGS);
                out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SPAN_FLAGS);
                out.setSpan(new RelativeSizeSpan(1.04f), start, end, SPAN_FLAGS);
                break;
            default:
                break;
        }
    }

    /** Styles the inline runs of one block or table cell without block level decoration. */
    public static CharSequence renderPieces(List<MarkdownText.Piece> pieces, Palette palette) {
        SpannableStringBuilder out = new SpannableStringBuilder();
        appendPieces(out, pieces, palette);
        return out;
    }

    private static void appendPieces(SpannableStringBuilder out, List<MarkdownText.Piece> pieces,
                                     Palette palette) {
        for (MarkdownText.Piece piece : pieces) {
            int start = out.length();
            out.append(piece.text);
            applyPieceStyle(out, start, out.length(), piece.style, palette);
        }
    }

    /** Text only table rendering, used when a caller styles whole blocks without the table widget. */
    private static void appendTableFallback(SpannableStringBuilder out, MarkdownText.Table table,
                                            Palette palette) {
        for (int row = 0; row < table.rowCount(); row++) {
            if (row > 0) {
                out.append('\n');
            }
            int rowStart = out.length();
            for (int column = 0; column < table.columns(); column++) {
                if (column > 0) {
                    out.append(" | ");
                }
                appendPieces(out, table.cell(row, column), palette);
            }
            if (row == 0 && out.length() > rowStart) {
                out.setSpan(new StyleSpan(Typeface.BOLD), rowStart, out.length(), SPAN_FLAGS);
            }
        }
    }

    private static void applyPieceStyle(SpannableStringBuilder out, int start, int end, int style,
                                        Palette palette) {
        if (end <= start) {
            return;
        }
        if ((style & MarkdownText.CODE) != 0) {
            out.setSpan(new TypefaceSpan(MONOSPACE), start, end, SPAN_FLAGS);
            out.setSpan(new BackgroundColorSpan(palette.codeBackground), start, end, SPAN_FLAGS);
            out.setSpan(new ForegroundColorSpan(palette.codeText), start, end, SPAN_FLAGS);
        }
        if ((style & MarkdownText.MATH) != 0) {
            out.setSpan(new TypefaceSpan(SERIF), start, end, SPAN_FLAGS);
            out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SPAN_FLAGS);
            out.setSpan(new ForegroundColorSpan(palette.math), start, end, SPAN_FLAGS);
        }
        if ((style & MarkdownText.BOLD) != 0) {
            out.setSpan(new StyleSpan(Typeface.BOLD), start, end, SPAN_FLAGS);
        }
        if ((style & MarkdownText.ITALIC) != 0) {
            out.setSpan(new StyleSpan(Typeface.ITALIC), start, end, SPAN_FLAGS);
        }
        if ((style & MarkdownText.STRIKE) != 0) {
            out.setSpan(new StrikethroughSpan(), start, end, SPAN_FLAGS);
        }
        if ((style & MarkdownText.MUTED) != 0) {
            out.setSpan(new ForegroundColorSpan(palette.muted), start, end, SPAN_FLAGS);
        }
    }

    private static float headingScale(int level) {
        switch (level) {
            case 1:
                return 1.34f;
            case 2:
                return 1.20f;
            case 3:
                return 1.10f;
            default:
                return 1.03f;
        }
    }

    private static boolean blankLineBetween(int previous, int current) {
        if (previous < 0) {
            return false;
        }
        return isSpacious(previous) || isSpacious(current);
    }

    private static boolean isSpacious(int kind) {
        return kind == MarkdownText.HEADING || kind == MarkdownText.CODE_BLOCK
                || kind == MarkdownText.MATH_BLOCK || kind == MarkdownText.TABLE
                || kind == MarkdownText.RULE;
    }

    private static void trimTrailingNewlines(SpannableStringBuilder out) {
        while (out.length() > 0 && out.charAt(out.length() - 1) == '\n') {
            out.delete(out.length() - 1, out.length());
        }
    }
}