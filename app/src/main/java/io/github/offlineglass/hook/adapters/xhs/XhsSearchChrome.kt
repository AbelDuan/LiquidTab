package io.github.offlineglass.hook.adapters.xhs

import android.graphics.Rect
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import io.github.offlineglass.hook.GlassHostLayout
import io.github.offlineglass.hook.adapters.AdapterOwnedOverlay
import kotlin.math.abs

/**
 * Native search entry chrome that survives hiding the magnifier alone.
 *
 * 小红书 keeps the search hint pill next to the header's search key. In 9.50 it
 * was a `TextView` ("搜索笔记，用户和商品") sharing a thin layer with the icon; in
 * 9.55 the pill is a separate right-side capsule whose magnifier is a compound
 * drawable, so hiding the icon view never removes it. The glass top bar then has
 * two search entries: our key and the native pill.
 *
 * Binding is structural, never version based:
 * - preferred: the icon's parent is a shallow layer without tabs/menu/pager;
 *   hide that layer;
 * - fallback: scan the decor tree for live right-side text capsules in the tab
 *   band (background on the text or its immediate wrapper) and hide them.
 *
 * Only alpha is touched, so the native click target stays attached and enabled
 * for the glass key. Ambiguous layouts keep the native chrome and log once.
 */
internal class XhsSearchChrome(
    private val root: View,
    private val tabs: ViewGroup,
    private val menu: View?,
    private val icon: View,
) {
    private var reported = false
    private val location = IntArray(2)
    private val iconRect = Rect()
    private val bounds = Rect()
    private val hints = ArrayList<TextView>(2)

    fun suppress(hide: (View, Boolean) -> Unit) {
        if (!icon.isAttachedToWindow || icon.width <= 0 || icon.height <= 0) return
        reportPathOnce()
        val d = icon.resources.displayMetrics.density

        var layer = icon.parent as? ViewGroup
        var depth = 0
        while (layer != null && layer !== root && depth < 3) {
            if (isDedicatedLayer(layer)) {
                hide(layer, false)
                report("layer", layer)
                return
            }
            layer = layer.parent as? ViewGroup
            depth++
        }

        if (hints.any { !it.isAttachedToWindow } || hints.isEmpty()) {
            hints.clear()
            discoverHints(hints)
        }
        var hidden = 0
        for (text in hints) {
            if (!text.isAttachedToWindow) continue
            hide(text, false)
            val box = (text.parent as? ViewGroup)?.takeIf { parent ->
                parent !== root && !parent.isClickable && parent.width <= root.width * .8f &&
                    parent.height <= text.height * 2 &&
                    (parent.background != null || text.background == null)
            }
            if (box != null) hide(box, false)
            hidden++
        }
        if (hidden > 0) report("hint", hints.firstOrNull())
    }

    /**
     * A live right-side capsule inside the tab band. Feed cards are below the
     * band and the recycler subtree is skipped, so ordinary note text never
     * qualifies. Our own glass hosts are AdapterOwnedOverlay and are skipped.
     */
    private fun discoverHints(out: MutableList<TextView>) {
        if (tabs.width <= 0) return
        val d = icon.resources.displayMetrics.density
        tabs.getLocationInWindow(location)
        val tabLeft = location[0]
        val tabTop = location[1]
        val tabRight = tabLeft + tabs.width
        val tabBottom = tabTop + tabs.height
        val band = maxOf(tabs.height * 2, (40f * d).toInt())
        val pending = ArrayDeque<View>()
        pending.add(root)
        var budget = 600
        while (pending.isNotEmpty() && budget-- > 0) {
            val view = pending.removeFirst()
            if (view === tabs || view === menu || view === icon) continue
            if (view is AdapterOwnedOverlay || view is GlassHostLayout) continue
            if (view is TextView) {
                if (!view.isShown || view.text.isNullOrBlank() || view.width <= 0) continue
                if (view.width > root.width * .75f) continue
                if (contains(view, tabs)) continue
                if (view.background == null && (view.parent as? View)?.background == null) continue
                view.getLocationInWindow(location)
                val left = location[0]
                val top = location[1]
                val bottom = top + view.height
                if (left < tabRight - (8f * d).toInt()) continue
                if (bottom < tabTop - band || top > tabBottom + band) continue
                if (view.text.toString().trim().length < 1) continue
                out.add(view)
                continue
            }
            if (view is ViewGroup) {
                if (isRecycler(view)) continue
                for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
            }
        }
    }

    private fun isDedicatedLayer(layer: ViewGroup): Boolean {
        if (layer === root || contains(layer, tabs)) return false
        if (menu != null && contains(layer, menu)) return false
        if (containsPager(layer)) return false
        if (containsOwned(layer)) return false
        if (layer.width < icon.width * 2) return false
        if (layer.height !in 1..(icon.height * 2.6f).toInt()) return false
        layer.getLocationInWindow(location)
        bounds.set(location[0], location[1], location[0] + layer.width, location[1] + layer.height)
        icon.getLocationInWindow(location)
        iconRect.set(location[0], location[1], location[0] + icon.width, location[1] + icon.height)
        if (!Rect.intersects(bounds, iconRect)) return false
        // A row-wide layer that also covers the pager is the page, not search.
        if (bounds.height() > icon.height * 2 && bounds.height() > root.height / 4) return false
        return layer.anyText()
    }

    private fun ViewGroup.anyText(): Boolean {
        for (i in 0 until childCount) {
            val child = getChildAt(i)
            if (child is TextView && !child.text.isNullOrBlank() && child.isShown) return true
            if (child is ViewGroup && child !== this && child.anyText()) return true
        }
        return false
    }

    private fun contains(ancestor: View, target: View): Boolean {
        var view: View? = target
        while (view != null) {
            if (view === ancestor) return true
            view = view.parent as? View
        }
        return false
    }

    private fun containsOwned(scope: View): Boolean {
        if (scope is AdapterOwnedOverlay || scope is GlassHostLayout) return true
        if (scope is ViewGroup) for (i in 0 until scope.childCount) {
            if (containsOwned(scope.getChildAt(i))) return true
        }
        return false
    }

    private fun containsPager(scope: View): Boolean {
        if (scope.javaClass.name.contains("ViewPager")) return true
        if (scope is ViewGroup) for (i in 0 until scope.childCount) {
            if (containsPager(scope.getChildAt(i))) return true
        }
        return false
    }

    private fun isRecycler(view: View): Boolean =
        generateSequence(view.javaClass as Class<*>?) { it.superclass }.any {
            it.name == "androidx.recyclerview.widget.RecyclerView" ||
                it.name == "android.support.v7.widget.RecyclerView"
        }

    private var pathReported = false
    private fun reportPathOnce() {
        if (pathReported) return
        pathReported = true
        val chain = ArrayList<String>(6)
        var view: View? = icon
        var depth = 0
        while (view != null && view !== root && depth < 6) {
            chain.add("${view.javaClass.simpleName}[${view.width}x${view.height}]" +
                if (view.background != null) "*" else "")
            view = view.parent as? View
            depth++
        }
        val siblings = (icon.parent as? ViewGroup)?.let { parent ->
            (0 until parent.childCount).joinToString(",") { index ->
                val child = parent.getChildAt(index)
                child.javaClass.simpleName + if (child is TextView) ":" + child.text else ""
            }
        }
        android.util.Log.i("LiquidTabXhsTop",
            "search icon path=${chain.joinToString(">")} siblings=[$siblings]")
    }

    private fun report(kind: String, hidden: View?) {
        if (reported) return
        reported = true
        val text = hidden as? TextView
        android.util.Log.i("LiquidTabXhsTop",
            "search chrome hidden by $kind class=${hidden?.javaClass?.name} " +
                "text=${text?.text?.take(20)} w=${hidden?.width} h=${hidden?.height}")
    }
}
