// Genera informe-pdf.html (índice estático + paginación CSS) para imprimir a PDF con Edge headless.
const fs = require("fs"), path = require("path");
let s = fs.readFileSync(path.join(__dirname, "informe.html"), "utf8");
const titulos = [...s.matchAll(/<h1>([^<]+)<\/h1>/g)].map(m => m[1]);
let n = 0;
s = s.replace(/<h1>([^<]+)<\/h1>/g, (m, t) => `<h1 id="s${++n}">${t}</h1>`);
const indice = "<ol style=\"list-style:none; padding-left:0; font-size:11pt; line-height:1.9\">" +
  titulos.map((t, i) => `<li><a href="#s${i + 1}" style="color:#0F2A44; text-decoration:none">${t}</a></li>`).join("") + "</ol>";
s = s.replace("<p>[[INDICE]]</p>", indice);
s = s.replace("</style>", `
@page { size: A4; margin: 20mm 18mm 20mm 18mm;
  @top-right { content: "MediConecta · Informe técnico final del TPO · UADE"; font: 8pt Calibri, Arial; color: #4A6178; }
  @bottom-right { content: "integracion-final · eda4b02 · Página " counter(page) " de " counter(pages); font: 8pt Calibri, Arial; color: #4A6178; } }
@page :first { @top-right { content: none; } @bottom-right { content: none; } }
table, tr, img { page-break-inside: avoid; }
h1, h2, h3 { page-break-after: avoid; }
</style>`);
fs.writeFileSync(path.join(__dirname, "informe-pdf.html"), s);
console.log("secciones:", titulos.length);
