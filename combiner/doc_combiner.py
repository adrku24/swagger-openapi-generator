#!/usr/bin/env python3
# -*- coding: utf-8 -*-

"""
Combine PlantUML (*.puml) and OpenAPI (*.yml/*.yaml/*.json) into one Markdown doc.

Features:
- Small Tkinter UI to choose inputs + output location
- Generates a Markdown file with:
  - PlantUML diagram embedded as code block (always)
  - OpenAPI spec embedded as code block (always)
  - Optional: render PlantUML to PNG via local plantuml.jar or "plantuml" command,
    and reference the PNG in the Markdown

No external network required.
"""

from __future__ import annotations

import os
import re
import shutil
import subprocess
from dataclasses import dataclass
from datetime import datetime
from pathlib import Path
from tkinter import Tk, Label, Entry, Button, StringVar, filedialog, messagebox, Checkbutton, IntVar


@dataclass
class Inputs:
    puml_path: Path
    openapi_path: Path
    out_md_path: Path
    render_png: bool
    plantuml_jar: Path | None


def read_text(path: Path) -> str:
    return path.read_text(encoding="utf-8", errors="replace")


def guess_lang_for_openapi(path: Path) -> str:
    ext = path.suffix.lower()
    if ext in {".yml", ".yaml"}:
        return "yaml"
    if ext == ".json":
        return "json"
    return ""


def sanitize_title_from_paths(puml_path: Path, openapi_path: Path) -> str:
    base = puml_path.stem
    api = openapi_path.stem
    title = f"Dokumentation: {base} + {api}"
    # Remove control chars etc.
    title = re.sub(r"[\x00-\x1F]+", " ", title).strip()
    return title


def try_render_plantuml_png(puml_path: Path, out_dir: Path, plantuml_jar: Path | None) -> Path | None:
    """
    Tries to render PlantUML to PNG.
    Order:
    1) If jar provided: java -jar plantuml.jar -tpng <file> -o <out_dir>
    2) If 'plantuml' exists on PATH: plantuml -tpng <file> -o <out_dir>
    Returns the generated PNG path if successful, else None.
    """
    out_dir.mkdir(parents=True, exist_ok=True)

    # Expect PlantUML to output PNG with same stem in out_dir
    expected_png = out_dir / f"{puml_path.stem}.png"

    try:
        if plantuml_jar:
            cmd = ["java", "-jar", str(plantuml_jar), "-tpng", str(puml_path), "-o", str(out_dir)]
            subprocess.run(cmd, check=True, capture_output=True)
            if expected_png.exists():
                return expected_png

        if shutil.which("plantuml"):
            cmd = ["plantuml", "-tpng", str(puml_path), "-o", str(out_dir)]
            subprocess.run(cmd, check=True, capture_output=True)
            if expected_png.exists():
                return expected_png

    except subprocess.CalledProcessError:
        return None

    return None


def build_markdown(inputs: Inputs) -> str:
    puml_text = read_text(inputs.puml_path)
    openapi_text = read_text(inputs.openapi_path)
    openapi_lang = guess_lang_for_openapi(inputs.openapi_path)

    title = sanitize_title_from_paths(inputs.puml_path, inputs.openapi_path)
    now = datetime.now().strftime("%Y-%m-%d %H:%M")

    png_rel = None
    if inputs.render_png:
        img_dir = inputs.out_md_path.parent / "assets"
        png_path = try_render_plantuml_png(inputs.puml_path, img_dir, inputs.plantuml_jar)
        if png_path:
            # Relative path for markdown
            png_rel = os.path.relpath(png_path, start=inputs.out_md_path.parent).replace("\\", "/")

    md = []
    md.append(f"# {title}\n")
    md.append(f"*Generiert am {now}*\n")

    md.append("## UML / Struktur (PlantUML)\n")
    if png_rel:
        md.append(f"![PlantUML Diagramm]({png_rel})\n")
    md.append("### PlantUML-Quelle\n")
    md.append("```plantuml\n")
    md.append(puml_text.rstrip() + "\n")
    md.append("```\n")

    md.append("## REST API (OpenAPI / Swagger)\n")
    md.append("### OpenAPI-Spezifikation\n")
    if openapi_lang:
        md.append(f"```{openapi_lang}\n")
    else:
        md.append("```\n")
    md.append(openapi_text.rstrip() + "\n")
    md.append("```\n")

    md.append("## Hinweise\n")
    md.append("- Dieses Dokument bündelt die automatisch generierten Artefakte (PlantUML + OpenAPI) in einer gemeinsamen Ausgabe.\n")
    md.append("- Optional kann das PlantUML-Diagramm als PNG gerendert werden (lokales PlantUML erforderlich).\n")

    return "".join(md)


def write_markdown(out_path: Path, content: str) -> None:
    out_path.parent.mkdir(parents=True, exist_ok=True)
    out_path.write_text(content, encoding="utf-8")


def validate_inputs(puml_path: str, openapi_path: str, out_md_path: str) -> tuple[bool, str]:
    if not puml_path or not Path(puml_path).exists():
        return False, "Bitte eine gültige PlantUML-Datei (*.puml) auswählen."
    if not openapi_path or not Path(openapi_path).exists():
        return False, "Bitte eine gültige OpenAPI-Datei (*.yml/*.yaml/*.json) auswählen."
    if not out_md_path:
        return False, "Bitte einen Speicherort für die Markdown-Ausgabe auswählen."
    outp = Path(out_md_path)
    if outp.suffix.lower() not in {".md", ".markdown"}:
        return False, "Ausgabedatei muss auf .md oder .markdown enden."
    return True, ""


class App:
    def __init__(self, root: Tk):
        self.root = root
        root.title("MDSD Doc Combiner (PlantUML + OpenAPI → Markdown)")
        root.resizable(True, True)
        root.grid_columnconfigure(1, weight=1)
        root.minsize(720, 260)

        self.puml_var = StringVar()
        self.openapi_var = StringVar()
        self.out_var = StringVar()

        self.render_png_var = IntVar(value=0)
        self.jar_var = StringVar()

        Label(root, text="PlantUML (*.puml):").grid(row=0, column=0, sticky="w", padx=8, pady=6)
        Entry(root, textvariable=self.puml_var).grid(row=0, column=1, sticky="ew", padx=8, pady=6)
        Button(root, text="Auswählen…", command=self.pick_puml).grid(row=0, column=2, padx=8, pady=6)

        Label(root, text="OpenAPI (*.yml/*.yaml/*.json):").grid(row=1, column=0, sticky="w", padx=8, pady=6)
        Entry(root, textvariable=self.openapi_var).grid(row=1, column=1, sticky="ew", padx=8, pady=6)
        Button(root, text="Auswählen…", command=self.pick_openapi).grid(row=1, column=2, padx=8, pady=6)

        Label(root, text="Ausgabe (*.md):").grid(row=2, column=0, sticky="w", padx=8, pady=6)
        Entry(root, textvariable=self.out_var).grid(row=2, column=1, sticky="ew", padx=8, pady=6)
        Button(root, text="Speichern unter…", command=self.pick_out).grid(row=2, column=2, padx=8, pady=6)

        Checkbutton(root, text="PlantUML als PNG rendern (optional)", variable=self.render_png_var, command=self.toggle_png).grid(
            row=3, column=0, columnspan=2, sticky="w", padx=8, pady=6
        )

        Label(root, text="plantuml.jar (optional, falls kein 'plantuml' im PATH):").grid(row=4, column=0, sticky="w", padx=8, pady=6)
        Entry(root, textvariable=self.jar_var, state="disabled").grid(row=4, column=1, sticky="ew", padx=8, pady=6)
        self.jar_btn = Button(root, text="Jar wählen…", command=self.pick_jar, state="disabled")
        self.jar_btn.grid(row=4, column=2, padx=8, pady=6)

        Button(root, text="Markdown erzeugen", command=self.generate).grid(row=5, column=1, sticky="e", padx=8, pady=12)
        Button(root, text="Beenden", command=root.quit).grid(row=5, column=2, sticky="w", padx=8, pady=12)

    def pick_puml(self):
        path = filedialog.askopenfilename(filetypes=[("PlantUML", "*.puml"), ("All files", "*.*")])
        if path:
            self.puml_var.set(path)

    def pick_openapi(self):
        path = filedialog.askopenfilename(filetypes=[("OpenAPI", "*.yml *.yaml *.json"), ("All files", "*.*")])
        if path:
            self.openapi_var.set(path)

    def pick_out(self):
        path = filedialog.asksaveasfilename(defaultextension=".md", filetypes=[("Markdown", "*.md *.markdown")])
        if path:
            self.out_var.set(path)

    def toggle_png(self):
        enabled = self.render_png_var.get() == 1
        state = "normal" if enabled else "disabled"
        # enable jar selection only if png rendering is enabled
        for widget in self.root.grid_slaves(row=4, column=1):
            widget.configure(state=state)
        self.jar_btn.configure(state=state)

    def pick_jar(self):
        path = filedialog.askopenfilename(filetypes=[("PlantUML Jar", "*.jar"), ("All files", "*.*")])
        if path:
            self.jar_var.set(path)

    def generate(self):
        ok, msg = validate_inputs(self.puml_var.get(), self.openapi_var.get(), self.out_var.get())
        if not ok:
            messagebox.showerror("Eingabe fehlt", msg)
            return

        puml_path = Path(self.puml_var.get())
        openapi_path = Path(self.openapi_var.get())
        out_md_path = Path(self.out_var.get())

        render_png = self.render_png_var.get() == 1
        jar = Path(self.jar_var.get()) if (render_png and self.jar_var.get().strip()) else None

        inputs = Inputs(
            puml_path=puml_path,
            openapi_path=openapi_path,
            out_md_path=out_md_path,
            render_png=render_png,
            plantuml_jar=jar
        )

        md = build_markdown(inputs)
        write_markdown(out_md_path, md)

        msg = f"Fertig!\nMarkdown gespeichert unter:\n{out_md_path}"
        if render_png:
            msg += "\n\nHinweis: PNG-Rendering ist optional. Falls kein Bild erscheint, installiere PlantUML oder gib plantuml.jar an."
        messagebox.showinfo("OK", msg)


def main():
    root = Tk()
    App(root)
    root.mainloop()


if __name__ == "__main__":
    main()
