package com.relaychat.app.diagrams;

final class DiagramSample {
    static final String ARCTAN_WAVEFORM =
            "{\"title\":\"y = arctan(x)\",\"xLabel\":\"x\",\"yLabel\":\"y\","
                    + "\"xRange\":[-10,10],\"yRange\":[-1.706,1.706],\"series\":["
                    + "{\"name\":\"arctan\",\"color\":\"#246b8a\",\"points\":["
                    + "[-10,-1.471],[-8,-1.446],[-6,-1.406],[-5,-1.373],[-4,-1.326],"
                    + "[-3,-1.249],[-2,-1.107],[-1,-0.785],[0,0],[1,0.785],[2,1.107],"
                    + "[3,1.249],[4,1.326],[5,1.373],[6,1.406],[8,1.446],[10,1.471]]}]}";

    static final String MARKDOWN =
            "# Local drawing\n\n"
                    + "The circuit is shown below.\n\n"
                    + "```svg\n"
                    + "<svg xmlns=\"http://www.w3.org/2000/svg\" viewBox=\"0 0 360 170\">\n"
                    + "  <rect x=\"16\" y=\"42\" width=\"70\" height=\"54\" rx=\"4\" "
                    + "fill=\"#eef5ff\" stroke=\"#246b8a\" stroke-width=\"2\"/>\n"
                    + "  <text x=\"51\" y=\"73\" text-anchor=\"middle\" font-size=\"13\">AC</text>\n"
                    + "  <path d=\"M86 69 H135\" fill=\"none\" stroke=\"#246b8a\" "
                    + "stroke-width=\"2\"/>\n"
                    + "  <polygon points=\"135,58 160,69 135,80\" fill=\"none\" "
                    + "stroke=\"#c2410c\" stroke-width=\"2\"/>\n"
                    + "  <path d=\"M160 69 H225\" fill=\"none\" stroke=\"#246b8a\" "
                    + "stroke-width=\"2\"/>\n"
                    + "  <rect x=\"225\" y=\"43\" width=\"58\" height=\"52\" rx=\"3\" "
                    + "fill=\"#fff7ed\" stroke=\"#c2410c\" stroke-width=\"2\"/>\n"
                    + "  <text x=\"254\" y=\"74\" text-anchor=\"middle\" font-size=\"12\">LOAD</text>\n"
                    + "  <path d=\"M283 69 H334 V125 H51 V96\" fill=\"none\" "
                    + "stroke=\"#526473\" stroke-width=\"2\"/>\n"
                    + "  <text x=\"180\" y=\"151\" text-anchor=\"middle\" font-size=\"12\">"
                    + "Single-phase controlled rectifier</text>\n"
                    + "</svg>\n"
                    + "```\n\n"
                    + "```mermaid\n"
                    + "flowchart LR\n"
                    + "  A[AC input] --> B[Thyristor]\n"
                    + "  B --> C[Load]\n"
                    + "  C --> D[Return path]\n"
                    + "  D --> A\n"
                    + "```\n\n"
                    + "```waveform\n"
                    + "{\"title\":\"Ud at alpha = 90 deg\",\"xLabel\":\"omega t\","
                    + "\"yLabel\":\"Ud\",\"series\":["
                    + "{\"name\":\"Ud\",\"color\":\"#246b8a\",\"points\":"
                    + "[[0,0],[0.5,0.8],[1,1.4],[1.5,1.4],[1.6,0],[2,0],"
                    + "[2.5,0.8],[3,1.4],[3.5,1.4],[3.6,0],[4,0]]},"
                    + "{\"name\":\"Gate\",\"color\":\"#c2410c\",\"step\":true,\"points\":"
                    + "[[0,0],[0.8,0],[0.8,0.5],[1.1,0.5],[1.1,0],[3,0],[3,0.5],"
                    + "[3.3,0.5],[3.3,0],[4,0]]}]}\n"
                    + "```\n\n"
                    + "The waveform data is rendered locally.";

    private DiagramSample() {
    }
}
