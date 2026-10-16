package io.helpium.sdk

import android.graphics.Typeface
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.StyleSpan
import android.text.style.URLSpan

/** The inline markdown chat replies use: **bold**, *italic*, [links](url). Bare URLs are linkified by the view. */
internal object Markdown {
    private val pattern = Regex("""\*\*(.+?)\*\*|(?<![*\w])\*(?!\s)(.+?)(?<!\s)\*(?!\*)|\[([^\]]+)]\((https?://[^)\s]+)\)""")

    fun render(source: String): CharSequence {
        val out = SpannableStringBuilder()
        var last = 0
        for (match in pattern.findAll(source)) {
            out.append(source, last, match.range.first)
            val start = out.length
            val (bold, italic, label, url) = match.destructured
            when {
                bold.isNotEmpty() -> { out.append(bold); out.setSpan(StyleSpan(Typeface.BOLD), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                italic.isNotEmpty() -> { out.append(italic); out.setSpan(StyleSpan(Typeface.ITALIC), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
                else -> { out.append(label); out.setSpan(URLSpan(url), start, out.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE) }
            }
            last = match.range.last + 1
        }
        out.append(source, last, source.length)
        return out
    }
}
