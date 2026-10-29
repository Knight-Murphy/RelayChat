package com.relaychat.app.model;

/** Instructions that let a text-only model hand exact drawing data to the Android renderer. */
public final class DrawingProtocol {
    public static final String INSTRUCTION =
            "Local drawing capability: when the user asks for a diagram, circuit, flow, chart, "
                    + "curve, or waveform, create the visual instead of saying you cannot draw. "
                    + "Return a complete fenced block using exactly one supported format below, "
                    + "followed by only the necessary explanation.\n"
                    + "1. Use ```svg for a precise self-contained SVG. Use viewBox, shapes, paths "
                    + "and text. Never use scripts, external links, remote images, or external CSS.\n"
                    + "2. Use ```mermaid for flowcharts, sequence diagrams, state diagrams, and "
                    + "relationships supported by Mermaid.\n"
                    + "3. Use ```waveform for sampled numeric curves and electrical waveforms. "
                    + "The body must be compact JSON with this shape: "
                    + "{\"title\":\"...\",\"xLabel\":\"...\",\"yLabel\":\"...\","
                    + "\"series\":[{\"name\":\"Ud\",\"color\":\"#246b8a\","
                    + "\"step\":false,\"points\":[[0,0],[1,1.2]]}]}. "
                    + "Points are numeric [x,y] pairs; use step=true for digital waveforms.\n"
                    + "Keep SVG viewBox coordinates compact and labels readable. Do not wrap a "
                    + "drawing block inside another code fence. If none of these formats can express "
                    + "the request accurately, explain the limitation in plain text.";
    public static final String PYTHON_INSTRUCTION =
            "Local Python execution: RelayChat runs Python 3.12 on the user's Android device with "
                    + "NumPy and Matplotlib already installed. When the user asks to run Python, "
                    + "calculate data, or create a computed chart or waveform, return exactly one "
                    + "complete ```python block containing the full program. Do not ask for a "
                    + "server-side tool and do not emit a separate duplicate code block. "
                    + "The program runs from an empty temporary directory. Use relative output "
                    + "filenames, call plt.show() or save a PNG/JPEG/WebP file, use "
                    + "constrained_layout or tight_layout, and keep labels inside the figure. "
                    + "Write Matplotlib math as $...$ using the mathtext subset; do not use "
                    + "\\(...\\) or \\[...\\] delimiters. "
                    + "Do not read unrelated files, access secrets, or start network requests. "
                    + "You cannot see the execution result before replying, so never fabricate "
                    + "printed values; state briefly what the program will compute. The app will "
                    + "show captured logs and generated images after it runs the code.";

    private DrawingProtocol() {
    }

    public static String appendTo(String systemPrompt) {
        String prompt = systemPrompt == null ? "" : systemPrompt.trim();
        String instructions = INSTRUCTION + "\n\n" + PYTHON_INSTRUCTION;
        return prompt.isEmpty() ? instructions : prompt + "\n\n" + instructions;
    }
}
