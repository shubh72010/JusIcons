package com.jusdots.jusicons.engine

import android.content.Context
import android.content.res.Resources
import android.graphics.drawable.AdaptiveIconDrawable
import android.graphics.drawable.Drawable
import android.graphics.drawable.InsetDrawable
import android.util.Log

/**
 * Hybrid provider — forensic port of d7/l.java: h() + getThemeDataForPackage()
 * Priority mirrors Nothing: curated ThemeData > app monochrome > generic d7/f.
 *
 * Map is declarative: res/xml/jus_grayscale_icon_map.xml
 * <icons><icon package="com.google.android.calendar" drawable="@drawable/jus_calendar_mono"/></icons>
 * Do not hardcode when(packageName) — add entries to XML.
 */
class ThemedIconProvider(private val context: Context) {

    data class ThemeData(val resources: Resources, val resId: Int) {
        fun loadPaddedDrawable(): Drawable? {
            return try {
                val type = resources.getResourceTypeName(resId)
                if (type == "drawable") {
                    val d = resources.getDrawable(resId, null).mutate()
                    wrapWithInset(d)
                } else if (type == "array") {
                    // Calendar per-day array: pick today's drawable (like IconProvider.loadCalendarDrawable)
                    val day = java.util.Calendar.getInstance().get(java.util.Calendar.DAY_OF_MONTH) - 1
                    val ta = resources.obtainTypedArray(resId)
                    val id = ta.getResourceId(day.coerceIn(0, ta.length() - 1), 0)
                    ta.recycle()
                    if (id != 0) {
                        val d = resources.getDrawable(id, null).mutate()
                        wrapWithInset(d)
                    } else null
                } else null
            } catch (e: Exception) {
                Log.e("ThemedIconProvider", "load $resId failed: ${e.message}")
                null
            }
        }

        private fun wrapWithInset(d: Drawable): Drawable {
            // IconProvider.ThemeData.loadPaddedDrawable: InsetDrawable(InsetDrawable(d,0.2f), extraFraction/(2*extra+1))
            val inner = InsetDrawable(d, 0.2f)
            val frac = AdaptiveIconDrawable.getExtraInsetFraction()
            val outerInset = frac / (frac * 2 + 1)
            return InsetDrawable(inner, outerInset)
        }
    }

    private var themedMap: Map<String, ThemeData>? = null
    private val disabledMap: Map<String, ThemeData> = emptyMap()

    private fun parseMap(): Map<String, ThemeData> {
        themedMap?.let { return it }
        val out = mutableMapOf<String, ThemeData>()
        try {
            val res = context.resources
            val parser = res.getXml(com.jusdots.jusicons.R.xml.jus_grayscale_icon_map)
            var depth = parser.depth
            // advance to <icons>
            var type = parser.next()
            while (type != 1 && !(type == 2 && parser.name == "icons")) type = parser.next()
            depth = parser.depth
            while (true) {
                val t = parser.next()
                if (t == 3 && parser.depth <= depth) break
                if (t == 1) break
                if (t == 2 && parser.name == "icon") {
                    val pkg = parser.getAttributeValue(null, "package")
                    val drawable = parser.getAttributeResourceValue(null, "drawable", 0)
                    if (!pkg.isNullOrEmpty() && drawable != 0) {
                        out[pkg] = ThemeData(res, drawable)
                    }
                }
            }
            parser.close()
        } catch (e: Exception) {
            Log.e("ThemedIconProvider", "Unable to parse jus_grayscale_icon_map", e)
        }
        themedMap = out
        return out
    }

    fun getThemeDataForPackage(packageName: String): ThemeData? = parseMap()[packageName]

    fun getAllMappedPackages(): Set<String> = parseMap().keys
}
