package com.relaychat.app.ui;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * Rewrites LaTeX fragments into readable Unicode text so a reply that arrives as raw LaTeX stays
 * legible inside a plain TextView.
 *
 * <p>The converter is deliberately forgiving: unknown commands are preserved verbatim, which keeps
 * ordinary text such as {@code C:\Users\name} or a lone {@code $} intact.
 */
public final class MathText {
    private static final Map<String, String> SYMBOLS = new HashMap<>();
    private static final Map<String, String> BLACKBOARD = new HashMap<>();
    private static final Map<Character, String> SUPERSCRIPTS = new HashMap<>();
    private static final Map<Character, String> SUBSCRIPTS = new HashMap<>();
    private static final Set<String> LIMIT_OPERATORS = new HashSet<>();
    private static final Set<String> NAMED_OPERATORS = new HashSet<>();
    private static final Set<String> PLAIN_ARGUMENTS = new HashSet<>();
    private static final Set<String> DROPPED_ARGUMENTS = new HashSet<>();

    static {
        put(SYMBOLS, "alpha", "\u03b1", "beta", "\u03b2", "gamma", "\u03b3", "delta", "\u03b4");
        put(SYMBOLS, "epsilon", "\u03b5", "varepsilon", "\u03b5", "zeta", "\u03b6", "eta", "\u03b7");
        put(SYMBOLS, "theta", "\u03b8", "vartheta", "\u03d1", "iota", "\u03b9", "kappa", "\u03ba");
        put(SYMBOLS, "lambda", "\u03bb", "mu", "\u03bc", "nu", "\u03bd", "xi", "\u03be");
        put(SYMBOLS, "omicron", "\u03bf", "pi", "\u03c0", "varpi", "\u03d6", "rho", "\u03c1");
        put(SYMBOLS, "varrho", "\u03f1", "sigma", "\u03c3", "varsigma", "\u03c2", "tau", "\u03c4");
        put(SYMBOLS, "upsilon", "\u03c5", "phi", "\u03c6", "varphi", "\u03d5", "chi", "\u03c7");
        put(SYMBOLS, "psi", "\u03c8", "omega", "\u03c9", "Gamma", "\u0393", "Delta", "\u0394");
        put(SYMBOLS, "Theta", "\u0398", "Lambda", "\u039b", "Xi", "\u039e", "Pi", "\u03a0");
        put(SYMBOLS, "Sigma", "\u03a3", "Upsilon", "\u03a5", "Phi", "\u03a6", "Psi", "\u03a8");
        put(SYMBOLS, "Omega", "\u03a9");
        put(SYMBOLS, "to", "\u2192", "rightarrow", "\u2192", "longrightarrow", "\u27f6");
        put(SYMBOLS, "leftarrow", "\u2190", "longleftarrow", "\u27f5", "gets", "\u2190");
        put(SYMBOLS, "Rightarrow", "\u21d2", "implies", "\u27f9", "Leftrightarrow", "\u21d4");
        put(SYMBOLS, "iff", "\u27fa", "leftrightarrow", "\u2194", "mapsto", "\u21a6");
        put(SYMBOLS, "uparrow", "\u2191", "downarrow", "\u2193", "updownarrow", "\u2195");
        put(SYMBOLS, "infty", "\u221e", "partial", "\u2202", "nabla", "\u2207", "int", "\u222b");
        put(SYMBOLS, "iint", "\u222c", "iiint", "\u222d", "oint", "\u222e", "sum", "\u2211");
        put(SYMBOLS, "prod", "\u220f", "coprod", "\u2210", "pm", "\u00b1", "mp", "\u2213");
        put(SYMBOLS, "times", "\u00d7", "cdot", "\u00b7", "cdotp", "\u00b7", "div", "\u00f7");
        put(SYMBOLS, "ast", "\u2217", "star", "\u22c6", "circ", "\u2218", "bullet", "\u2022");
        put(SYMBOLS, "neq", "\u2260", "ne", "\u2260", "approx", "\u2248", "equiv", "\u2261");
        put(SYMBOLS, "sim", "\u223c", "simeq", "\u2243", "cong", "\u2245", "propto", "\u221d");
        put(SYMBOLS, "le", "\u2264", "leq", "\u2264", "leqslant", "\u2264", "ge", "\u2265");
        put(SYMBOLS, "geq", "\u2265", "geqslant", "\u2265", "ll", "\u226a", "gg", "\u226b");
        put(SYMBOLS, "in", "\u2208", "notin", "\u2209", "ni", "\u220b", "subset", "\u2282");
        put(SYMBOLS, "subseteq", "\u2286", "supset", "\u2283", "supseteq", "\u2287");
        put(SYMBOLS, "cup", "\u222a", "cap", "\u2229", "setminus", "\u2216");
        put(SYMBOLS, "emptyset", "\u2205", "varnothing", "\u2205", "forall", "\u2200");
        put(SYMBOLS, "exists", "\u2203", "nexists", "\u2204", "neg", "\u00ac", "lnot", "\u00ac");
        put(SYMBOLS, "land", "\u2227", "wedge", "\u2227", "lor", "\u2228", "vee", "\u2228");
        put(SYMBOLS, "oplus", "\u2295", "otimes", "\u2297", "odot", "\u2299", "perp", "\u22a5");
        put(SYMBOLS, "parallel", "\u2225", "angle", "\u2220", "triangle", "\u25b3");
        put(SYMBOLS, "square", "\u25a1", "blacksquare", "\u25a0", "degree", "\u00b0");
        put(SYMBOLS, "prime", "\u2032", "hbar", "\u210f", "ell", "\u2113", "aleph", "\u2135");
        put(SYMBOLS, "lfloor", "\u230a", "rfloor", "\u230b", "lceil", "\u2308", "rceil", "\u2309");
        put(SYMBOLS, "langle", "\u27e8", "rangle", "\u27e9", "lbrace", "{", "rbrace", "}");
        put(SYMBOLS, "vert", "|", "lvert", "|", "rvert", "|", "Vert", "\u2016");
        put(SYMBOLS, "lVert", "\u2016", "rVert", "\u2016", "mid", "|", "colon", ":");
        put(SYMBOLS, "dots", "\u2026", "ldots", "\u2026", "cdots", "\u22ef", "vdots", "\u22ee");
        put(SYMBOLS, "ddots", "\u22f1", "therefore", "\u2234", "because", "\u2235");
        put(SYMBOLS, "checkmark", "\u2713", "dag", "\u2020", "dagger", "\u2020", "S", "\u00a7");

        put(BLACKBOARD, "R", "\u211d", "N", "\u2115", "Z", "\u2124", "Q", "\u211a");
        put(BLACKBOARD, "C", "\u2102", "H", "\u210d", "P", "\u2119", "E", "\ud835\udd3c");

        put(SUPERSCRIPTS, '0', "\u2070", '1', "\u00b9", '2', "\u00b2", '3', "\u00b3", '4', "\u2074");
        put(SUPERSCRIPTS, '5', "\u2075", '6', "\u2076", '7', "\u2077", '8', "\u2078", '9', "\u2079");
        put(SUPERSCRIPTS, '+', "\u207a", '-', "\u207b", '=', "\u207c", '(', "\u207d", ')', "\u207e");
        put(SUBSCRIPTS, '0', "\u2080", '1', "\u2081", '2', "\u2082", '3', "\u2083", '4', "\u2084");
        put(SUBSCRIPTS, '5', "\u2085", '6', "\u2086", '7', "\u2087", '8', "\u2088", '9', "\u2089");
        put(SUBSCRIPTS, '+', "\u208a", '-', "\u208b", '=', "\u208c", '(', "\u208d", ')', "\u208e");

        addAll(LIMIT_OPERATORS, "lim", "limsup", "liminf", "max", "min", "sup", "inf",
                "argmax", "argmin", "det", "gcd", "lcm");
        addAll(NAMED_OPERATORS, "sin", "cos", "tan", "cot", "sec", "csc", "arcsin", "arccos",
                "arctan", "sinh", "cosh", "tanh", "coth", "log", "ln", "lg", "exp", "bmod",
                "pmod", "ker", "hom", "dim", "deg", "tr", "rank", "diag", "mod");
        addAll(PLAIN_ARGUMENTS, "text", "textrm", "textnormal", "textup", "textit", "textbf",
                "textsf", "texttt", "mbox", "mathrm", "mathit", "mathsf", "mathtt", "mathbf",
                "mathcal", "mathfrak", "boldsymbol", "operatorname", "ensuremath");
        addAll(PLAIN_ARGUMENTS, "vec", "hat", "bar", "tilde", "dot", "ddot", "breve", "check",
                "acute", "grave", "mathring", "overline", "underline", "widehat", "widetilde",
                "overrightarrow", "overleftarrow", "overbrace", "underbrace", "boxed", "pmb",
                "operatornamewithlimits", "substack");
        addAll(DROPPED_ARGUMENTS, "phantom", "vphantom", "hphantom", "label", "tag", "notag");
    }

    private static void put(Map<String, String> map, String... pairs) {
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put(pairs[index], pairs[index + 1]);
        }
    }

    private static void put(Map<Character, String> map, Object... pairs) {
        for (int index = 0; index + 1 < pairs.length; index += 2) {
            map.put((Character) pairs[index], (String) pairs[index + 1]);
        }
    }

    private static void addAll(Set<String> target, String... names) {
        for (String name : names) {
            target.add(name);
        }
    }
    private final String source;
    private int index;
    private boolean limitOperator;

    private MathText(String source) {
        this.source = source;
    }

    /** Converts every LaTeX fragment found in {@code source} and leaves other text untouched. */
    public static String render(String source) {
        if (source == null || source.isEmpty()) {
            return "";
        }
        return tidy(new MathText(source).parse());
    }

    private String parse() {
        StringBuilder out = new StringBuilder();
        while (index < source.length()) {
            char current = source.charAt(index);
            if (current == '}') {
                break;
            }
            if (current == '{') {
                index++;
                out.append(parse());
                if (index < source.length() && source.charAt(index) == '}') {
                    index++;
                }
                limitOperator = false;
                continue;
            }
            if (current == '\\') {
                out.append(parseCommand());
                continue;
            }
            if (current == '^' || current == '_') {
                out.append(parseScript(current));
                continue;
            }
            if (current == '&') {
                out.append(' ');
                index++;
                limitOperator = false;
                continue;
            }
            out.append(current);
            if (!Character.isWhitespace(current)) {
                limitOperator = false;
            }
            index++;
        }
        return out.toString();
    }

    private String parseCommand() {
        index++;
        if (index >= source.length()) {
            return "\\";
        }
        char current = source.charAt(index);
        if (!Character.isLetter(current)) {
            index++;
            if (current == '\\' && index < source.length()
                    && Character.isLetter(source.charAt(index))) {
                return "\\\\";
            }
            if (current == '_' && index < source.length() && source.charAt(index) == '{') {
                return parseScript('_');
            }
            return symbolEscape(current);
        }
        int start = index;
        while (index < source.length() && Character.isLetter(source.charAt(index))) {
            index++;
        }
        String name = source.substring(start, index);
        if (index < source.length() && source.charAt(index) == '*') {
            index++;
        }
        return command(name);
    }

    private String symbolEscape(char value) {
        switch (value) {
            case '\\':
                return "\n";
            case ',':
            case ';':
            case ':':
            case ' ':
                return " ";
            case '!':
            case '\'':
            case '(':
            case ')':
            case '[':
            case ']':
                return "";
            case '{':
                return "{";
            case '}':
                return "}";
            case '%':
                return "%";
            case '&':
                return "&";
            case '#':
                return "#";
            case '_':
                return "_";
            case '$':
                return "$";
            case '|':
                return "\u2016";
            default:
                return "\\" + value;
        }
    }

    private String command(String name) {
        limitOperator = false;
        if ("frac".equals(name) || "dfrac".equals(name) || "tfrac".equals(name)
                || "cfrac".equals(name)) {
            return fraction();
        }
        if ("sqrt".equals(name)) {
            return root();
        }
        if (isDelimiterCommand(name)) {
            return parseDelimiter();
        }
        if ("begin".equals(name) || "end".equals(name)) {
            readGroup();
            return "";
        }
        if ("mathbb".equals(name)) {
            String inner = readGroup();
            String mapped = BLACKBOARD.get(inner);
            return mapped == null ? inner : mapped;
        }
        if (PLAIN_ARGUMENTS.contains(name)) {
            return readGroup();
        }
        if (DROPPED_ARGUMENTS.contains(name)) {
            readGroup();
            return "";
        }
        if ("quad".equals(name)) {
            return "   ";
        }
        if ("qquad".equals(name)) {
            return "     ";
        }
        if ("limits".equals(name) || "nolimits".equals(name) || "displaystyle".equals(name)
                || "textstyle".equals(name) || "scriptstyle".equals(name)
                || "scriptscriptstyle".equals(name) || "nonumber".equals(name)
                || "mathstrut".equals(name) || "strut".equals(name) || "hline".equals(name)) {
            return "";
        }
        String symbol = SYMBOLS.get(name);
        if (symbol != null) {
            limitOperator = LIMIT_OPERATORS.contains(name);
            return symbol;
        }
        if (NAMED_OPERATORS.contains(name) || LIMIT_OPERATORS.contains(name)) {
            limitOperator = LIMIT_OPERATORS.contains(name);
            return name;
        }
        return "\\" + name;
    }

    private boolean isDelimiterCommand(String name) {
        if ("left".equals(name) || "right".equals(name) || "middle".equals(name)) {
            return true;
        }
        String size = name.startsWith("big") ? name.substring(3)
                : name.startsWith("Big") ? name.substring(3) : null;
        if (size == null) {
            return false;
        }
        return size.isEmpty() || "l".equals(size) || "r".equals(size) || "m".equals(size)
                || "g".equals(size) || "gl".equals(size) || "gr".equals(size)
                || "gm".equals(size);
    }

    private String parseDelimiter() {
        while (index < source.length() && source.charAt(index) == ' ') {
            index++;
        }
        if (index >= source.length()) {
            return "";
        }
        char current = source.charAt(index);
        if (current == '\\') {
            return parseCommand();
        }
        index++;
        return current == '.' ? "" : String.valueOf(current);
    }

    private String fraction() {
        String numerator = tidy(readGroup());
        String denominator = tidy(readGroup());
        if (numerator.isEmpty() || denominator.isEmpty()) {
            return numerator + denominator;
        }
        return group(numerator) + "/" + group(denominator);
    }

    private String root() {
        String degree = "";
        if (index < source.length() && source.charAt(index) == '[') {
            int close = source.indexOf(']', index + 1);
            if (close < 0) {
                return "\u221a";
            }
            degree = source.substring(index + 1, close).trim();
            index = close + 1;
        }
        String inner = tidy(readGroup());
        if ("3".equals(degree)) {
            return "\u221b" + group(inner);
        }
        if (!degree.isEmpty()) {
            return "\u221a[" + degree + "]" + group(inner);
        }
        return "\u221a" + group(inner);
    }

    private String readGroup() {
        if (index < source.length() && source.charAt(index) == '{') {
            index++;
            String inner = parse();
            if (index < source.length() && source.charAt(index) == '}') {
                index++;
            }
            return inner;
        }
        int start = index;
        if (index < source.length() && source.charAt(index) == '\\') {
            parseCommand();
            return source.substring(start, index);
        }
        if (index < source.length()) {
            index++;
        }
        return source.substring(start, index);
    }

    private String parseScript(char marker) {
        index++;
        boolean braced = index < source.length() && source.charAt(index) == '{';
        boolean describeLimit = limitOperator && marker == '_' && braced;
        limitOperator = false;
        String content = tidy(readGroup());
        if (content.isEmpty()) {
            return "";
        }
        if (describeLimit) {
            return " (" + content + ")";
        }
        String mapped = mapScript(content, marker == '^' ? SUPERSCRIPTS : SUBSCRIPTS);
        if (mapped != null) {
            return mapped;
        }
        if (!braced && isToken(content)) {
            return marker + content;
        }
        return marker + "(" + content + ")";
    }

    private static boolean isToken(String value) {
        if (value.isEmpty() || value.length() > 3) {
            return false;
        }
        for (int position = 0; position < value.length(); position++) {
            if (!Character.isLetterOrDigit(value.charAt(position))) {
                return false;
            }
        }
        return true;
    }

    private static String mapScript(String content, Map<Character, String> table) {
        StringBuilder out = new StringBuilder();
        for (int position = 0; position < content.length(); position++) {
            char current = content.charAt(position);
            if (current == ' ') {
                continue;
            }
            String mapped = table.get(current);
            if (mapped == null) {
                return null;
            }
            out.append(mapped);
        }
        return out.length() == 0 ? null : out.toString();
    }

    private static String group(String value) {
        if (value.isEmpty() || isAtomic(value)) {
            return value;
        }
        return "(" + value + ")";
    }

    private static boolean isAtomic(String value) {
        if (value.length() == 1) {
            return true;
        }
        int depth = 0;
        for (int position = 0; position < value.length(); position++) {
            char current = value.charAt(position);
            if (current == '(' || current == '[' || current == '{') {
                depth++;
            } else if (current == ')' || current == ']' || current == '}') {
                depth--;
                if (depth < 0) {
                    return false;
                }
            } else if (depth == 0) {
                if (current == '+' || current == '-' || current == '=' || current == '/'
                        || current == '\n' || current == ',' || current == ' ') {
                    return false;
                }
            }
        }
        return depth == 0;
    }

    private static String tidy(String value) {
        StringBuilder out = new StringBuilder(value.length());
        boolean pendingSpace = false;
        for (int position = 0; position < value.length(); position++) {
            char current = value.charAt(position);
            if (current == '\n') {
                trimTrailingSpaces(out);
                out.append('\n');
                pendingSpace = false;
                continue;
            }
            if (current == ' ' || current == '\t') {
                pendingSpace = true;
                continue;
            }
            if (pendingSpace) {
                pendingSpace = false;
                char previous = out.length() == 0 ? '\n' : out.charAt(out.length() - 1);
                boolean tight = previous == '\n'
                        || "([{".indexOf(previous) >= 0
                        || ")]},.;:!?".indexOf(current) >= 0;
                if (!tight) {
                    out.append(' ');
                }
            }
            out.append(current);
        }
        trimTrailingSpaces(out);
        return out.toString();
    }

    private static void trimTrailingSpaces(StringBuilder out) {
        while (out.length() > 0) {
            char last = out.charAt(out.length() - 1);
            if (last == ' ' || last == '\t') {
                out.delete(out.length() - 1, out.length());
            } else {
                break;
            }
        }
    }
}