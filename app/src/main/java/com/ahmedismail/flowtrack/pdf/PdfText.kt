package com.ahmedismail.flowtrack.pdf

import android.graphics.Paint
import android.text.TextPaint
import android.text.TextUtils

/**
 * Canvas.drawText neither wraps nor clips, so any user-supplied string
 * (project, area, task or member name, inspection reference) drawn at a fixed
 * x would otherwise run off the sheet. These helpers trim it to the width that
 * is actually available.
 */
internal fun fitText(text: String, paint: Paint, maxWidth: Float): String =
    if (text.isEmpty() || maxWidth <= 0f || paint.measureText(text) <= maxWidth) text
    else TextUtils.ellipsize(text, TextPaint(paint), maxWidth, TextUtils.TruncateAt.END).toString()

/** Turns a user-supplied name into a file-name fragment that is safe on every filesystem. */
internal fun fileNameSafe(name: String, fallback: String = "Report"): String =
    name.trim()
        .replace(Regex("[\\\\/:*?\"<>|\\s]+"), "_")
        .trim('_')
        .take(60)
        .ifBlank { fallback }
