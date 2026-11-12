package ru.r3xed.qsolog.data

import java.time.Instant
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * QSL labels to print: an A4 page of 3 × 8 labels (70 × 37 mm, the common sticker sheet), one contact each —
 * "To Radio CALL", date, time UTC, band, mode, RST, "PSE / TNX QSL". Printed by the browser (computer) or Android's
 * print service; nothing is sent anywhere.
 */
object QslLabels {
    private val DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy").withZone(ZoneOffset.UTC)
    private val TIME = DateTimeFormatter.ofPattern("HH:mm").withZone(ZoneOffset.UTC)

    fun html(list: List<Qso>, myCall: String): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")
        val labels = list.sortedWith(compareBy({ it.call }, { it.timeUtc })).joinToString("\n") { q ->
            val t = Instant.ofEpochMilli(q.timeUtc)
            val tnx = if (q.qslRcvd) "TNX QSL" else "PSE QSL"
            val via = q.adif["QSL_VIA"]?.takeIf { it.isNotBlank() }?.let { "<div class=via>via ${esc(it.uppercase())}</div>" }.orEmpty() +
                // A listener's report: whom the station was heard working.
                if (Swl.isSwl(q)) "<div class=via>SWL report" + Swl.with(q.adif).ifBlank { null }?.let { " · heard working <b>${esc(it.uppercase())}</b>" }.orEmpty() + "</div>" else ""
            """<div class=label><div class=to>To Radio <b>${esc(q.call.uppercase())}</b></div>$via
<table><tr><th>Date</th><th>UTC</th><th>Band</th><th>Mode</th><th>RST</th></tr>
<tr><td>${DATE.format(t)}</td><td>${TIME.format(t)}</td><td>${esc(q.band)}</td><td>${esc(q.mode)}</td><td>${esc(q.rstSent)}</td></tr></table>
<div class=foot><span>${esc(myCall.uppercase())}</span><span>$tnx · 73!</span></div></div>"""
        }
        return """<!doctype html><html><head><meta charset="utf-8"><title>QSL ${esc(myCall.uppercase())}</title>
<style>
@page { size: A4; margin: 10mm 0 0 0 }
body { margin: 0; font-family: Arial, Helvetica, sans-serif; }
.sheet { display: grid; grid-template-columns: repeat(3, 70mm); grid-auto-rows: 37mm; }
.label { box-sizing: border-box; padding: 3mm 4mm; overflow: hidden; break-inside: avoid; font-size: 9pt; }
.to { font-size: 11pt; } .to b { font-size: 14pt; letter-spacing: .5pt; }
.via { font-size: 8pt; }
table { border-collapse: collapse; width: 100%; margin-top: 1.5mm; }
th, td { border: .3mm solid #000; text-align: center; padding: .5mm; font-size: 8pt; } th { font-weight: normal; }
.foot { display: flex; justify-content: space-between; margin-top: 1.5mm; font-weight: bold; }
@media screen { .label { outline: 1px dashed #bbb; } }
</style></head><body><div class=sheet>
$labels
</div></body></html>"""
    }
}
