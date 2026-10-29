"""Local Python execution for RelayChat."""

import contextlib
import io
import json
import os
from pathlib import Path
import re
import traceback

os.environ.setdefault("MPLBACKEND", "Agg")

import matplotlib

matplotlib.use("Agg")
import matplotlib.pyplot as plt
from matplotlib import font_manager
from matplotlib.text import Text


_IMAGE_SUFFIXES = {".png", ".jpg", ".jpeg", ".webp"}
_MAX_LOGS = 200_000
_MAX_IMAGES = 20
_FONT_SUFFIXES = {".otf", ".ttf", ".ttc"}
_SYSTEM_FONT_DIRS = (
    "/system/fonts",
    "/product/fonts",
    "/system_ext/fonts",
    "/vendor/fonts",
    "/odm/fonts",
)
_CJK_FONT_HINTS = (
    "cjk", "han", "hei", "song", "kai", "ming", "fallback",
    "wenquanyi", "source han", "misans", "harmony", "oppo sans", "vivo sans",
)
_DEFAULT_FONT_FAMILIES = ("sans-serif", "DejaVu Sans", "Arial")
_DISPLAY_MATH = re.compile(r"\\\[(.+?)\\\]", re.DOTALL)
_INLINE_MATH = re.compile(r"\\\((.+?)\\\)", re.DOTALL)


def _find_cjk_font():
    paths = []
    for directory in _SYSTEM_FONT_DIRS:
        try:
            paths.extend(
                item for item in Path(directory).iterdir()
                if item.is_file() and item.suffix.lower() in _FONT_SUFFIXES
            )
        except OSError:
            continue

    # Android normally ships Noto Sans CJK, but vendors use different names.
    # Check likely filenames first, then scan every remaining system font.
    paths.sort(key=lambda path: (
        not any(hint in path.name.casefold() for hint in _CJK_FONT_HINTS),
        "noto" not in path.name.casefold(),
        path.name.casefold(),
    ))
    for path in paths:
        try:
            font = font_manager.get_font(str(path))
            glyphs = (font.get_char_index(ord(char)) for char in "中文")
        except Exception:
            continue
        if not all(glyphs):
            continue
        try:
            family = font.family_name
            font_manager.fontManager.addfont(str(path))
        except Exception:
            continue
        if family:
            return family
    return None


_CJK_FONT = _find_cjk_font()
if _CJK_FONT:
    _font_families = [_CJK_FONT, *_DEFAULT_FONT_FAMILIES]
    matplotlib.rcParams["font.family"] = ["sans-serif"]
    matplotlib.rcParams["font.sans-serif"] = _font_families
    matplotlib.rcParams["axes.unicode_minus"] = False


def _restore_font_defaults():
    if not _CJK_FONT:
        return
    matplotlib.rcParams["font.family"] = ["sans-serif"]
    matplotlib.rcParams["font.sans-serif"] = [
        _CJK_FONT, *_DEFAULT_FONT_FAMILIES]
    matplotlib.rcParams["axes.unicode_minus"] = False


def _apply_cjk_font():
    if not _CJK_FONT:
        return
    for figure in (plt.figure(number) for number in plt.get_fignums()):
        _set_figure_font(figure)


def _set_figure_font(figure):
    if _CJK_FONT:
        for text in figure.findobj(match=Text):
            text.set_fontfamily(_CJK_FONT)


def _mathtext(value):
    """Convert common LaTeX delimiters to Matplotlib's inline math syntax."""
    if not isinstance(value, str) or ("\\(" not in value and "\\[" not in value):
        return value
    value = _DISPLAY_MATH.sub(r"$\1$", value)
    return _INLINE_MATH.sub(r"$\1$", value)


def _set_figure_mathtext(figure):
    for text in figure.findobj(match=Text):
        text.set_text(_mathtext(text.get_text()))


def _save_figure(figure, workdir, number):
    _set_figure_font(figure)
    _set_figure_mathtext(figure)
    original_set_text = Text.set_text

    def mathtext_set_text(self, value):
        original_set_text(self, _mathtext(value))

    Text.set_text = mathtext_set_text
    target = workdir / f"relaychat_figure_{number}.png"
    try:
        figure.savefig(target, dpi=160, bbox_inches="tight", facecolor="white")
    finally:
        Text.set_text = original_set_text
    return target


def _collect_images(workdir):
    images = []
    for path in sorted(
        (item for item in workdir.iterdir()
         if item.is_file() and item.suffix.lower() in _IMAGE_SUFFIXES),
        key=lambda item: (item.stat().st_mtime_ns, item.name),
    ):
        if len(images) >= _MAX_IMAGES:
            break
        suffix = path.suffix.lower()
        mime = "image/png" if suffix == ".png" else "image/jpeg"
        if suffix == ".webp":
            mime = "image/webp"
        images.append({"name": path.name, "mime": mime})
    return images


def _combined_logs(stdout, stderr):
    parts = []
    output = stdout.getvalue()
    errors = stderr.getvalue()
    if output:
        parts.append(output.rstrip())
    if errors:
        parts.append(errors.rstrip())
    text = "\n".join(part for part in parts if part)
    if len(text) > _MAX_LOGS:
        text = text[-_MAX_LOGS:]
        text = "[输出过长，仅保留末尾内容]\n" + text
    return text


def run(code, workdir):
    """Execute source in workdir and return a JSON string for the Java caller."""
    root = Path(workdir).resolve()
    root.mkdir(parents=True, exist_ok=True)
    _restore_font_defaults()
    stdout = io.StringIO()
    stderr = io.StringIO()
    status = "completed"
    previous_cwd = os.getcwd()
    original_show = plt.show
    figure_number = 0

    def captured_show(*_args, **_kwargs):
        nonlocal figure_number
        for number in plt.get_fignums():
            figure = plt.figure(number)
            if not figure.axes:
                continue
            figure_number += 1
            _save_figure(figure, root, figure_number)
        plt.close("all")

    try:
        os.chdir(root)
        plt.show = captured_show
        with contextlib.redirect_stdout(stdout), contextlib.redirect_stderr(stderr):
            exec(compile(code, "<relaychat>", "exec"), {
                "__name__": "__main__",
                "__file__": str(root / "main.py"),
            })
    except BaseException:
        status = "failed"
        traceback.print_exc(file=stderr)
    finally:
        try:
            if not _collect_images(root):
                for number in plt.get_fignums():
                    figure = plt.figure(number)
                    if figure.axes:
                        figure_number += 1
                        _save_figure(figure, root, figure_number)
        except BaseException:
            status = "failed"
            traceback.print_exc(file=stderr)
        plt.close("all")
        plt.show = original_show
        os.chdir(previous_cwd)

    result = {
        "status": status,
        "logs": _combined_logs(stdout, stderr),
        "images": _collect_images(root),
    }
    return json.dumps(result, ensure_ascii=False)
