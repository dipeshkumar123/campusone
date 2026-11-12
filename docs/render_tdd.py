from pathlib import Path

import markdown

ROOT = Path(__file__).resolve().parent
SOURCE = ROOT / "TDD.md"
STYLE = ROOT / "tdd-print.css"
OUTPUT = ROOT / "TDD.html"

body = markdown.Markdown(extensions=["extra", "sane_lists"]).convert(
    SOURCE.read_text(encoding="utf-8")
)
style = STYLE.read_text(encoding="utf-8")

html = f"""<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>CampusOne - Technical Design Document</title>
<style>
{style}
</style>
</head>
<body>
{body}
</body>
</html>
"""

OUTPUT.write_text(html, encoding="utf-8")
print(OUTPUT)
