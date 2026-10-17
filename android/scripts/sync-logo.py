"""Convert the repository mascot's SVG primitives to an Android vector drawable."""
from pathlib import Path
import xml.etree.ElementTree as ET

ROOT = Path(__file__).resolve().parents[2]
ANDROID = "http://schemas.android.com/apk/res/android"
ET.register_namespace("android", ANDROID)


def attrs(**values):
    return {f"{{{ANDROID}}}{key}": str(value) for key, value in values.items()}


def path(node):
    tag = node.tag.split("}")[-1]
    a = node.attrib
    if tag == "path":
        return a["d"]
    if tag == "line":
        return f'M{a["x1"]},{a["y1"]} L{a["x2"]},{a["y2"]}'
    if tag in ("circle", "ellipse"):
        x, y = float(a["cx"]), float(a["cy"])
        rx, ry = (float(a["r"]), float(a["r"])) if tag == "circle" else (float(a["rx"]), float(a["ry"]))
        return f"M{x-rx},{y} A{rx},{ry} 0,1,0 {x+rx},{y} A{rx},{ry} 0,1,0 {x-rx},{y} Z"
    if tag == "rect":
        x, y, w, h = (float(a[key]) for key in ("x", "y", "width", "height"))
        r = min(float(a.get("rx", 0)), w / 2, h / 2)
        return f"M{x+r},{y} H{x+w-r} Q{x+w},{y} {x+w},{y+r} V{y+h-r} Q{x+w},{y+h} {x+w-r},{y+h} H{x+r} Q{x},{y+h} {x},{y+h-r} V{y+r} Q{x},{y} {x+r},{y} Z"
    raise ValueError(f"Unsupported mascot primitive: {tag}")


source = ET.parse(ROOT / ".github/images/castor.svg").getroot()
clips = {node.attrib["id"]: path(node[0]) for node in source.iter() if node.tag.endswith("}clipPath")}
vector = ET.Element("vector", attrs(width="108dp", height="108dp", viewportWidth=108, viewportHeight=108))
canvas = ET.SubElement(vector, "group", attrs(scaleX=0.65, scaleY=0.65, translateX=21.5, translateY=21.5))


def convert(node, parent, inherited=None, alpha=1.0):
    tag = node.tag.split("}")[-1]
    if tag == "defs":
        return
    style = dict(inherited or {})
    style.update({key: value for key, value in node.attrib.items() if key in ("fill", "stroke", "stroke-width", "stroke-linecap")})
    alpha *= float(node.attrib.get("opacity", 1))
    if tag == "g":
        group = ET.SubElement(parent, "group")
        clip = node.attrib.get("clip-path")
        if clip:
            ET.SubElement(group, "clip-path", attrs(pathData=clips[clip.removeprefix("url(#").removesuffix(")")]))
        for child in node:
            convert(child, group, style, alpha)
        return
    fill = style.get("fill", "none" if tag == "line" else "#000000")
    ET.SubElement(parent, "path", attrs(pathData=path(node), fillColor="#00000000" if fill == "none" else fill,
        fillAlpha=alpha, strokeColor=style.get("stroke", "#00000000"), strokeWidth=style.get("stroke-width", 0),
        strokeAlpha=alpha, strokeLineCap=style.get("stroke-linecap", "butt")))


for child in source:
    convert(child, canvas)
ET.indent(vector, space="    ")
ET.ElementTree(vector).write(ROOT / "android/app/src/main/res/drawable/ic_launcher_foreground.xml", encoding="utf-8", xml_declaration=True)
