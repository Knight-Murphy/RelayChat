package com.relaychat.app.ui;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Color;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import com.relaychat.app.R;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Renders model-authored drawing data entirely on the device. The web view has no network, file or
 * content access. SVG markup is reduced to a safe drawing subset and Mermaid source is only handed
 * to the bundled renderer as text.
 */
public final class LocalDiagramView extends WebView {
    public enum Kind {
        SVG,
        MERMAID,
        WAVEFORM
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1);
    private static final String LOCAL_BASE_URL = "https://relaychat.local/";
    private static final String MERMAID_URL = LOCAL_BASE_URL + "mermaid.min.js";
    private static volatile byte[] mermaidScript;

    private final Kind kind;
    private final String source;
    private final MarkdownRenderer.Palette palette;

    public static Kind kindFor(String language) {
        String value = language == null ? "" : language.trim().toLowerCase(Locale.ROOT);
        if ("svg".equals(value)) {
            return Kind.SVG;
        }
        if ("mermaid".equals(value)) {
            return Kind.MERMAID;
        }
        if ("waveform".equals(value) || "chart".equals(value) || "plot".equals(value)) {
            return Kind.WAVEFORM;
        }
        return null;
    }

    public static String sourceOf(MarkdownText.Block block) {
        if (block == null || block.kind != MarkdownText.CODE_BLOCK) {
            return "";
        }
        StringBuilder source = new StringBuilder();
        for (MarkdownText.Piece piece : block.pieces) {
            if (source.length() > 0) {
                source.append('\n');
            }
            source.append(piece.text);
        }
        return source.toString();
    }

    @SuppressLint("SetJavaScriptEnabled")
    public LocalDiagramView(Context context, Kind kind, String source,
                            MarkdownRenderer.Palette palette, boolean interactive) {
        super(context);
        this.kind = kind;
        this.source = source == null ? "" : source;
        this.palette = palette == null
                ? MarkdownRenderer.Palette.assistant(context.getResources().getDisplayMetrics().density)
                : palette;

        setBackgroundColor(Color.WHITE);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setVerticalScrollBarEnabled(interactive);
        setHorizontalScrollBarEnabled(false);
        setNestedScrollingEnabled(interactive);
        WebSettings settings = getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setBlockNetworkLoads(true);
        settings.setDomStorageEnabled(false);
        settings.setDatabaseEnabled(false);
        settings.setMediaPlaybackRequiresUserGesture(true);
        settings.setSupportZoom(interactive);
        settings.setBuiltInZoomControls(interactive);
        settings.setDisplayZoomControls(false);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return true;
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return true;
            }

            @Override
            public WebResourceResponse shouldInterceptRequest(
                    WebView view, WebResourceRequest request) {
                if (MERMAID_URL.equals(request.getUrl().toString())) {
                    return new WebResourceResponse("application/javascript", "UTF-8",
                            new ByteArrayInputStream(readMermaidBytes()));
                }
                return null;
            }
        });
        loadDataWithBaseURL(LOCAL_BASE_URL, buildHtml(interactive),
                "text/html", "UTF-8", null);
    }

    public Kind getKind() {
        return kind;
    }

    public String getSource() {
        return source;
    }

    public void release() {
        stopLoading();
        loadUrl("about:blank");
        removeAllViews();
        destroy();
    }

    private String buildHtml(boolean interactive) {
        String common = commonHtml(interactive);
        switch (kind) {
            case SVG:
                return common + svgHtml();
            case MERMAID:
                return common + mermaidHtml();
            case WAVEFORM:
            default:
                return common + waveformHtml(interactive);
        }
    }

    private String commonHtml(boolean interactive) {
        String text = cssColor(palette.text);
        String muted = cssColor(palette.muted);
        String border = cssColor(palette.tableBorder);
        String minHeight = interactive ? "100vh" : "250px";
        return "<!doctype html><html><head><meta charset=\"utf-8\">"
                + "<meta name=\"viewport\" content=\"width=device-width,initial-scale=1,"
                + "maximum-scale=5,user-scalable=yes\">"
                + "<meta http-equiv=\"Content-Security-Policy\" content=\"default-src 'none'; "
                + "img-src data:; style-src 'unsafe-inline'; script-src 'unsafe-inline' "
                + "'unsafe-eval' https://relaychat.local; font-src data:; connect-src 'none'; "
                + "media-src 'none'; "
                + "object-src 'none'; frame-src 'none';\">"
                + "<style>"
                + "*{box-sizing:border-box}"
                + "html,body{height:100%;margin:0;padding:0;background:#fff;color:" + text + ";"
                + "font-family:system-ui,-apple-system,'Segoe UI',sans-serif;overflow:auto}"
                + "body{min-height:" + minHeight + ";padding:10px}"
                + "#chart{display:block;width:100%;height:100%}"
                + "#diagram{width:100%;min-height:220px;display:flex;align-items:center;"
                + "justify-content:center;overflow:auto}"
                + "#diagram svg{display:block;max-width:100%;height:auto;margin:auto}"
                + "#fallback{display:none;margin:0;padding:12px;border:1px solid " + border + ";"
                + "border-radius:7px;background:#f7f9fb;color:" + muted + ";font:12px/1.45 monospace;"
                + "white-space:pre-wrap;overflow-wrap:anywhere}"
                + "</style></head><body>";
    }

    private String svgHtml() {
        return "<div id=\"diagram\"></div><pre id=\"fallback\"></pre>"
                + "<pre id=\"svg-source\" hidden>" + escapeHtml(source) + "</pre>"
                + "<script>" + svgScript() + "</script></body></html>";
    }

    private String mermaidHtml() {
        return "<div id=\"diagram\"></div><pre id=\"fallback\"></pre>"
                + "<pre id=\"mermaid-source\" hidden>" + escapeHtml(source) + "</pre>"
                + "<script src=\"" + MERMAID_URL + "\"></script>"
                + "<script>" + mermaidScript(NEXT_ID.getAndIncrement()) + "</script>"
                + "</body></html>";
    }

    private String waveformHtml(boolean interactive) {
        return "<canvas id=\"chart\"></canvas><pre id=\"fallback\"></pre>"
                + "<pre id=\"chart-source\" hidden>" + escapeHtml(source) + "</pre>"
                + "<script>" + waveformScript(interactive) + "</script></body></html>";
    }

    private static String svgScript() {
        return """
                (() => {
                  const source = document.getElementById('svg-source').textContent.trim();
                  const fallback = document.getElementById('fallback');
                  const fail = message => {
                    fallback.style.display = 'block';
                    fallback.textContent = source + (message ? '\\n\\n' + message : '');
                  };
                  try {
                    const doc = new DOMParser().parseFromString(source, 'image/svg+xml');
                    if (doc.querySelector('parsererror')) throw new Error('SVG parse error');
                    const root = doc.documentElement;
                    if (!root || root.localName.toLowerCase() !== 'svg') {
                      throw new Error('Expected an svg root element');
                    }
                    const allowed = new Set([
                      'svg','g','path','rect','circle','ellipse','line','polyline','polygon',
                      'text','tspan','defs','lineargradient','radialgradient','stop','clippath',
                      'mask','marker','symbol','use','title','desc'
                    ]);
                    for (const node of [root, ...Array.from(root.querySelectorAll('*'))]) {
                      if (!allowed.has(node.localName.toLowerCase())) {
                        node.remove();
                        continue;
                      }
                      for (const attribute of Array.from(node.attributes)) {
                        const name = attribute.name.toLowerCase();
                        const value = attribute.value || '';
                        if (name.startsWith('on') || name === 'src' ||
                            ((name === 'href' || name === 'xlink:href') && !value.startsWith('#')) ||
                            /(?:url|expression|import)\\s*\\(/i.test(value) &&
                              !/^\\s*url\\(#[^)]+\\)\\s*$/i.test(value)) {
                          node.removeAttribute(attribute.name);
                        }
                      }
                    }
                    for (const style of Array.from(root.querySelectorAll('style'))) {
                      style.remove();
                    }
                    root.removeAttribute('width');
                    root.removeAttribute('height');
                    root.setAttribute('preserveAspectRatio', 'xMidYMid meet');
                    root.style.maxWidth = '100%';
                    root.style.height = 'auto';
                    document.getElementById('diagram').appendChild(root);
                  } catch (error) {
                    fail(String(error && error.message ? error.message : error));
                  }
                })();
                """;
    }

    private static String mermaidScript(int id) {
        return "(() => {\n"
                + "  const source=document.getElementById('mermaid-source').textContent.trim();\n"
                + "  const fallback=document.getElementById('fallback');\n"
                + "  const fail=message=>{fallback.style.display='block';"
                + "fallback.textContent=source+'\\n\\n'+message;};\n"
                + "  if(!window.mermaid){fail('Mermaid runtime failed to load');return;}\n"
                + "  mermaid.initialize({startOnLoad:false,securityLevel:'strict',theme:'base',"
                + "fontFamily:'sans-serif',flowchart:{htmlLabels:false,useMaxWidth:true},"
                + "sequence:{useMaxWidth:true},themeVariables:{primaryColor:'#eaf1fb',"
                + "primaryTextColor:'#182330',primaryBorderColor:'#4f89e2',"
                + "lineColor:'#526473',secondaryColor:'#f3f6f8',tertiaryColor:'#fff'}});\n"
                + "  mermaid.render('relay-mermaid-" + id + "',source).then(result=>{\n"
                + "    document.getElementById('diagram').innerHTML=result.svg;\n"
                + "  }).catch(error=>fail(String(error&&error.message?error.message:error)));\n"
                + "})();";
    }

    private static String waveformScript(boolean interactive) {
        return """
                (() => {
                  const source = document.getElementById('chart-source').textContent.trim();
                  const fallback = document.getElementById('fallback');
                  const interactive = __INTERACTIVE__;
                  const canvas = document.getElementById('chart');
                  const context = canvas.getContext('2d');
                  const fail = message => {
                    fallback.style.display = 'block';
                    fallback.textContent = source + (message ? '\\n\\n' + message : '');
                  };
                  const draw = () => {
                    try {
                      fallback.style.display = 'none';
                      const spec = JSON.parse(source);
                      const rawSeries = Array.isArray(spec.series)
                        ? spec.series
                        : (Array.isArray(spec.points)
                          ? [{name: spec.title || '', points: spec.points}] : []);
                      if (!rawSeries.length) throw new Error('No series data');
                      if (rawSeries.length > 12) throw new Error('Too many series');
                      let total = 0;
                      const series = rawSeries.map((item, seriesIndex) => {
                        if (!item || !Array.isArray(item.points)) {
                          throw new Error('Invalid series');
                        }
                        total += item.points.length;
                        if (total > 6000) throw new Error('Too many points');
                        const points = item.points.map(point => {
                          const x = Array.isArray(point)
                            ? Number(point[0]) : Number(point && point.x);
                          const y = Array.isArray(point)
                            ? Number(point[1]) : Number(point && point.y);
                          if (!Number.isFinite(x) || !Number.isFinite(y)) {
                            throw new Error('Every point needs numeric x and y');
                          }
                          return {x, y};
                        });
                        return {
                          name: String(item.name || item.label || ('S' + (seriesIndex + 1))),
                          color: String(item.color || ''),
                          step: item.step === true,
                          points
                        };
                      });
                      const all = series.flatMap(item => item.points);
                      if (!all.length) throw new Error('No points to draw');
                      const finiteRange = (value, fallbackMin, fallbackMax) =>
                        Array.isArray(value) && value.length === 2 &&
                        Number.isFinite(Number(value[0])) &&
                        Number.isFinite(Number(value[1])) &&
                        Number(value[0]) !== Number(value[1])
                          ? [Number(value[0]), Number(value[1])]
                          : [fallbackMin, fallbackMax];
                      const padRange = (min, max) => {
                        if (min === max) return [min - 1, max + 1];
                        const pad = (max - min) * 0.08;
                        return [min - pad, max + pad];
                      };
                      let [xMin, xMax] = finiteRange(spec.xRange,
                        Math.min(...all.map(p => p.x)), Math.max(...all.map(p => p.x)));
                      let [yMin, yMax] = finiteRange(spec.yRange,
                        Math.min(...all.map(p => p.y)), Math.max(...all.map(p => p.y)));
                      [xMin, xMax] = padRange(xMin, xMax);
                      [yMin, yMax] = padRange(yMin, yMax);

                      const bounds = canvas.getBoundingClientRect();
                      const width = Math.max(280, Math.round(
                        bounds.width || canvas.parentElement.clientWidth));
                      const fallbackHeight = (window.visualViewport
                        ? window.visualViewport.height : window.innerHeight)
                        || (interactive ? 800 : 250);
                      const height = Math.max(interactive ? 240 : 180, Math.round(
                        bounds.height || fallbackHeight - 20));
                      const ratio = Math.max(1, window.devicePixelRatio || 1);
                      canvas.width = Math.round(width * ratio);
                      canvas.height = Math.round(height * ratio);
                      context.setTransform(ratio, 0, 0, ratio, 0, 0);
                      context.fillStyle = '#ffffff';
                      context.fillRect(0, 0, width, height);

                      const yTicks = width < 420 ? 4 : 5;
                      const tickLabels = Array.from({length: yTicks + 1}, (_, index) =>
                        formatTick(yMax - (yMax - yMin) * index / yTicks));
                      context.font = '11px sans-serif';
                      context.textBaseline = 'middle';
                      const maxYLabelWidth = Math.max(
                        ...tickLabels.map(label => context.measureText(label).width));
                      const left = Math.max(54, Math.ceil(maxYLabelWidth) + 18);
                      const right = 20;
                      const top = spec.title ? 44 : 22;

                      const showLegend = series.length > 1 ||
                        (!spec.title && series.length === 1 && series[0].name);
                      const legendEntries = showLegend
                        ? series.map((item, index) => ({
                            label: item.name,
                            color: safeColor(item.color) ||
                              ['#246b8a','#c2410c','#5b6e3a','#7b4f9d','#b7791f','#2c7a7b']
                                [index % 6]
                          })).filter(item => item.label) : [];
                      let legendRows = 0;
                      let rowWidth = 0;
                      const available = width - left - right;
                      legendEntries.forEach(item => {
                        const itemWidth = 28 + context.measureText(item.label).width;
                        if (rowWidth > 0 && rowWidth + itemWidth > available) {
                          legendRows++;
                          rowWidth = 0;
                        }
                        rowWidth += itemWidth;
                      });
                      if (legendEntries.length) legendRows++;
                      const bottom = 44 + legendRows * 18 + (spec.xLabel ? 18 : 0);
                      const plotWidth = Math.max(1, width - left - right);
                      const plotHeight = Math.max(1, height - top - bottom);
                      const plotBottom = top + plotHeight;
                      const xLabelY = plotBottom + 39 + legendRows * 18;
                      let xTicks = width < 420 ? 4 : 5;
                      const xLabelValues = () => Array.from({length: xTicks + 1},
                        (_, index) => formatTick(
                          xMin + (xMax - xMin) * index / xTicks));
                      let xTickLabels = xLabelValues();
                      let maxXLabelWidth = Math.max(
                        ...xTickLabels.map(label => context.measureText(label).width));
                      while (xTicks > 2 &&
                          plotWidth / xTicks < maxXLabelWidth + 12) {
                        xTicks--;
                        xTickLabels = xLabelValues();
                        maxXLabelWidth = Math.max(
                          ...xTickLabels.map(label => context.measureText(label).width));
                      }
                      const sx = value =>
                        left + (value - xMin) / (xMax - xMin) * plotWidth;
                      const sy = value =>
                        top + (yMax - value) / (yMax - yMin) * plotHeight;

                      for (let index = 0; index <= yTicks; index++) {
                        const fraction = index / yTicks;
                        const y = top + plotHeight * fraction;
                        context.strokeStyle = '#e7ece8';
                        context.lineWidth = 1;
                        context.beginPath();
                        context.moveTo(left, y);
                        context.lineTo(left + plotWidth, y);
                        context.stroke();
                        context.fillStyle = '#707f8f';
                        context.textAlign = 'right';
                        context.fillText(tickLabels[index], left - 8, y);
                      }
                      for (let index = 0; index <= xTicks; index++) {
                        const fraction = index / xTicks;
                        const x = left + plotWidth * fraction;
                        context.strokeStyle = '#e7ece8';
                        context.lineWidth = 1;
                        context.beginPath();
                        context.moveTo(x, top);
                        context.lineTo(x, plotBottom);
                        context.stroke();
                        context.fillStyle = '#707f8f';
                        context.textAlign = 'center';
                        context.fillText(xTickLabels[index], x, plotBottom + 17);
                      }
                      context.strokeStyle = '#526473';
                      context.lineWidth = 1.4;
                      context.beginPath();
                      context.moveTo(left, top);
                      context.lineTo(left, plotBottom);
                      context.lineTo(left + plotWidth, plotBottom);
                      context.stroke();

                      const colors =
                        ['#246b8a','#c2410c','#5b6e3a','#7b4f9d','#b7791f','#2c7a7b'];
                      context.save();
                      context.beginPath();
                      context.rect(left, top, plotWidth, plotHeight);
                      context.clip();
                      series.forEach((item, index) => {
                        if (!item.points.length) return;
                        context.strokeStyle =
                          safeColor(item.color) || colors[index % colors.length];
                        context.lineWidth = 2;
                        context.lineJoin = 'round';
                        context.lineCap = 'round';
                        context.beginPath();
                        item.points.forEach((point, pointIndex) => {
                          const x = sx(point.x), y = sy(point.y);
                          if (pointIndex === 0) {
                            context.moveTo(x, y);
                          } else if (item.step) {
                            context.lineTo(x, sy(item.points[pointIndex - 1].y));
                            context.lineTo(x, y);
                          } else {
                            context.lineTo(x, y);
                          }
                        });
                        context.stroke();
                      });
                      context.restore();

                      context.textAlign = 'left';
                      context.textBaseline = 'alphabetic';
                      context.font = 'bold 13px sans-serif';
                      context.fillStyle = '#182330';
                      if (spec.title) context.fillText(String(spec.title), 8, 20);
                      if (spec.xLabel) {
                        context.textAlign = 'right';
                        context.font = '11px sans-serif';
                        context.fillStyle = '#526473';
                        context.fillText(String(spec.xLabel), width - right, xLabelY);
                      }
                      if (spec.yLabel) {
                        context.save();
                        context.translate(13, top + plotHeight / 2);
                        context.rotate(-Math.PI / 2);
                        context.textAlign = 'center';
                        context.fillStyle = '#526473';
                        context.fillText(String(spec.yLabel), 0, 0);
                        context.restore();
                      }

                      context.font = '11px sans-serif';
                      context.textBaseline = 'middle';
                      let legendX = left;
                      let legendRow = 0;
                      legendEntries.forEach(item => {
                        const itemWidth = 28 + context.measureText(item.label).width;
                        if (legendX > left && legendX + itemWidth > width - right) {
                          legendRow++;
                          legendX = left;
                        }
                        const legendY = plotBottom + 39 + legendRow * 18;
                        context.fillStyle = item.color;
                        context.fillRect(legendX, legendY - 1.5, 12, 3);
                        context.fillStyle = '#526473';
                        context.textAlign = 'left';
                        context.fillText(item.label, legendX + 16, legendY);
                        legendX += itemWidth;
                      });
                      window.__relaychatChartMetrics = {
                        canvasWidth: width,
                        canvasHeight: height,
                        plotLeft: left,
                        plotTop: top,
                        plotRight: left + plotWidth,
                        plotBottom,
                        plotHeight,
                        xTickLabelY: plotBottom + 17,
                        xLabelY: spec.xLabel ? xLabelY : null,
                        xTickLabels: xTickLabels.map((label, index) => ({
                          label,
                          center: left + plotWidth * index / xTicks,
                          width: context.measureText(label).width
                        })),
                        legendTop: legendEntries.length
                          ? plotBottom + 37.5 : null,
                        legendBottom: legendEntries.length
                          ? plotBottom + 39 + (legendRows - 1) * 18 + 8 : null
                      };

                      function safeColor(value) {
                        return /^#[0-9a-f]{3,8}$/i.test(value) ? value : '';
                      }
                      function formatTick(value) {
                        if (value === 0) return '0';
                        const abs = Math.abs(value);
                        if (abs >= 1000 || abs < 0.01) return value.toExponential(1);
                        return Number(value.toFixed(3)).toString();
                      }
                    } catch (error) {
                      fail(String(error && error.message ? error.message : error));
                    }
                  };
                  requestAnimationFrame(() => requestAnimationFrame(draw));
                  let redrawScheduled = false;
                  const scheduleDraw = () => {
                    if (redrawScheduled) return;
                    redrawScheduled = true;
                    requestAnimationFrame(() => {
                      redrawScheduled = false;
                      draw();
                    });
                  };
                  if (window.ResizeObserver) {
                    new ResizeObserver(scheduleDraw).observe(canvas);
                  }
                  window.addEventListener('resize', scheduleDraw);
                })();
                """.replace("__INTERACTIVE__", Boolean.toString(interactive));
    }

    private byte[] readMermaidBytes() {
        byte[] cached = mermaidScript;
        if (cached != null) {
            return cached;
        }
        try (InputStream input = getContext().getResources()
                .openRawResource(R.raw.mermaid_min);
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) {
                output.write(buffer, 0, count);
            }
            cached = output.toByteArray();
            mermaidScript = cached;
            return cached;
        } catch (Exception error) {
            return "window.mermaid=null;".getBytes(StandardCharsets.UTF_8);
        }
    }

    private static String escapeHtml(String value) {
        String source = value == null ? "" : value;
        return source.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String cssColor(int color) {
        return String.format(Locale.US, "#%06X", color & 0xFFFFFF);
    }
}
