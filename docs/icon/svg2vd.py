"""Convert the restricted SVG subset used for the icon into an Android VectorDrawable.

Usage: python3 svg2vd.py in.svg out.xml SIZE_DP

Supported: <g> (transform translate/scale/rotate, inherited paint attributes), <path>, <circle>,
<ellipse>, <rect> (incl. rx/ry), <polygon>, <polyline>, <line>. Anything else is an error, so a
design that uses an unsupported feature fails loudly instead of rendering differently.
"""
import re
import sys
import xml.etree.ElementTree as ET

SVG_NS = "{http://www.w3.org/2000/svg}"
PAINT = ["fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin", "stroke-miterlimit",
         "fill-rule", "fill-opacity", "stroke-opacity", "opacity"]
SHAPES = {"path", "circle", "ellipse", "rect", "polygon", "polyline", "line"}
NAMED = {"white": "#FFFFFF", "black": "#000000"}


def num(v):
    return float(re.sub(r"(px)$", "", v.strip()))


def fmt(x):
    s = f"{x:.3f}".rstrip("0").rstrip(".")
    return "0" if s in ("-0", "") else s


def color(v):
    v = v.strip()
    v = NAMED.get(v.lower(), v)
    if v == "none":
        return None
    m = re.fullmatch(r"#([0-9a-fA-F]{3}|[0-9a-fA-F]{6})", v)
    if not m:
        raise SystemExit(f"unsupported colour {v!r}")
    h = m.group(1)
    if len(h) == 3:
        h = "".join(c * 2 for c in h)
    return "#" + h.upper()


def shape_path(tag, a):
    if tag == "path":
        return a["d"].strip()
    if tag == "circle":
        cx, cy, r = num(a.get("cx", "0")), num(a.get("cy", "0")), num(a["r"])
        return ellipse(cx, cy, r, r)
    if tag == "ellipse":
        return ellipse(num(a.get("cx", "0")), num(a.get("cy", "0")), num(a["rx"]), num(a["ry"]))
    if tag == "rect":
        x, y, w, h = num(a.get("x", "0")), num(a.get("y", "0")), num(a["width"]), num(a["height"])
        rx = a.get("rx")
        ry = a.get("ry")
        rx = num(rx) if rx is not None else (num(ry) if ry is not None else 0.0)
        ry = num(ry) if ry is not None else rx
        rx, ry = min(rx, w / 2), min(ry, h / 2)
        if rx <= 0 or ry <= 0:
            return f"M{fmt(x)},{fmt(y)} H{fmt(x + w)} V{fmt(y + h)} H{fmt(x)} Z"
        return (f"M{fmt(x + rx)},{fmt(y)} H{fmt(x + w - rx)} A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x + w)},{fmt(y + ry)} "
                f"V{fmt(y + h - ry)} A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x + w - rx)},{fmt(y + h)} "
                f"H{fmt(x + rx)} A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x)},{fmt(y + h - ry)} "
                f"V{fmt(y + ry)} A{fmt(rx)},{fmt(ry)} 0 0,1 {fmt(x + rx)},{fmt(y)} Z")
    if tag in ("polygon", "polyline"):
        pts = [float(p) for p in re.split(r"[\s,]+", a["points"].strip()) if p]
        pairs = list(zip(pts[0::2], pts[1::2]))
        d = "M" + " L".join(f"{fmt(x)},{fmt(y)}" for x, y in pairs)
        return d + (" Z" if tag == "polygon" else "")
    if tag == "line":
        return (f"M{fmt(num(a.get('x1', '0')))},{fmt(num(a.get('y1', '0')))} "
                f"L{fmt(num(a.get('x2', '0')))},{fmt(num(a.get('y2', '0')))}")
    raise SystemExit(f"unsupported element {tag}")


def ellipse(cx, cy, rx, ry):
    return (f"M{fmt(cx - rx)},{fmt(cy)} A{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(cx + rx)},{fmt(cy)} "
            f"A{fmt(rx)},{fmt(ry)} 0 1,0 {fmt(cx - rx)},{fmt(cy)} Z")


def transforms(t):
    """Yields VectorDrawable group attribute dicts, outermost first."""
    for name, args in re.findall(r"(\w+)\s*\(([^)]*)\)", t or ""):
        v = [float(x) for x in re.split(r"[\s,]+", args.strip()) if x]
        if name == "translate":
            yield {"android:translateX": fmt(v[0]), "android:translateY": fmt(v[1] if len(v) > 1 else 0)}
        elif name == "scale":
            yield {"android:scaleX": fmt(v[0]), "android:scaleY": fmt(v[1] if len(v) > 1 else v[0])}
        elif name == "rotate":
            g = {"android:rotation": fmt(v[0])}
            if len(v) == 3:
                g["android:pivotX"] = fmt(v[1])
                g["android:pivotY"] = fmt(v[2])
            yield g
        else:
            raise SystemExit(f"unsupported transform {name}")


def path_attrs(d, paint):
    out = {"android:pathData": d}
    fill = color(paint.get("fill", "#000000"))
    stroke = color(paint["stroke"]) if "stroke" in paint else None
    opacity = float(paint.get("opacity", "1"))
    if fill:
        out["android:fillColor"] = fill
        alpha = float(paint.get("fill-opacity", "1")) * opacity
        if alpha != 1:
            out["android:fillAlpha"] = fmt(alpha)
        if paint.get("fill-rule") == "evenodd":
            out["android:fillType"] = "evenOdd"
    if stroke:
        out["android:strokeColor"] = stroke
        out["android:strokeWidth"] = fmt(num(paint.get("stroke-width", "1")))
        alpha = float(paint.get("stroke-opacity", "1")) * opacity
        if alpha != 1:
            out["android:strokeAlpha"] = fmt(alpha)
        if "stroke-linecap" in paint:
            out["android:strokeLineCap"] = paint["stroke-linecap"]
        if "stroke-linejoin" in paint:
            out["android:strokeLineJoin"] = paint["stroke-linejoin"]
        if "stroke-miterlimit" in paint:
            out["android:strokeMiterLimit"] = fmt(float(paint["stroke-miterlimit"]))
    return out


def walk(el, paint, indent, lines):
    tag = el.tag.replace(SVG_NS, "")
    a = dict(el.attrib)
    if "style" in a or "class" in a:
        raise SystemExit(f"style/class attributes are not supported (on <{tag}>)")
    own = dict(paint)
    for k in PAINT:
        if k in a:
            if k == "opacity" and tag == "g":
                raise SystemExit("opacity on <g> is not supported")
            own[k] = a[k]
    groups = list(transforms(a.get("transform")))
    if groups and tag != "g":
        raise SystemExit(f"transform on <{tag}> is not supported, wrap it in <g>")
    pad = "    " * indent
    if tag == "g":
        for g in groups:
            lines.append(pad + "<group " + " ".join(f'{k}="{v}"' for k, v in g.items()) + ">")
            indent += 1
            pad = "    " * indent
        for child in el:
            walk(child, own, indent, lines)
        for _ in groups:
            indent -= 1
            lines.append("    " * indent + "</group>")
        return
    if tag not in SHAPES:
        raise SystemExit(f"unsupported element <{tag}>")
    attrs = path_attrs(shape_path(tag, a), own)
    if "android:fillColor" not in attrs and "android:strokeColor" not in attrs:
        return
    lines.append(pad + "<path")
    for k, v in attrs.items():
        lines.append(pad + f'    {k}="{v}"')
    lines[-1] += " />"


def main():
    src, dst, size = sys.argv[1], sys.argv[2], sys.argv[3]
    root = ET.parse(src).getroot()
    vb = [float(x) for x in re.split(r"[\s,]+", root.attrib["viewBox"].strip())]
    if vb[0] != 0 or vb[1] != 0:
        raise SystemExit("viewBox must start at 0 0")
    lines = ['<?xml version="1.0" encoding="utf-8"?>',
             '<vector xmlns:android="http://schemas.android.com/apk/res/android"',
             f'    android:width="{size}dp"',
             f'    android:height="{size}dp"',
             f'    android:viewportWidth="{fmt(vb[2])}"',
             f'    android:viewportHeight="{fmt(vb[3])}">']
    paint = {k: root.attrib[k] for k in PAINT if k in root.attrib}
    for child in root:
        walk(child, paint, 1, lines)
    lines.append("</vector>")
    open(dst, "w", encoding="utf-8").write("\n".join(lines) + "\n")


if __name__ == "__main__":
    main()
