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
 * 小红书 9.50 keeps the search hint pill ("搜索笔记，用户和商品") in the same thin
 * layer as the search icon: `ConstraintLayout(icon + hint TextView)`. The top bar
 * hides the icon so the native click target stays alive, which leaves the pill
 * and its background visible next to our glass search key.
 *
 * Binding stays structural and never reads a version:
 * - preferred: the icon's parent is a shallow layer that holds no tabs, menu or
 *   pager, is wider than the icon and is not taller than one row; hide the layer;
 * - fallback: layouts that put the icon directly in the page root (RedViewStub
 *   inflated pill) hide the right-side hint text view in the header band.
 *
 * Only alpha is touched: the icon stays attached and enabled so `performClick`
 * from the glass key keeps using the native route. A rejected/ambiguous layout
 * keeps the native chrome and reports once instead of guessing.
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

    fun suppress(hide: (View, Boolean) -> Unit) {
        if (!icon.isAttachedToWindow || icon.width <= 0 || icon.height <= 0) return
        val d = icon.resources.displayMetrics.density
        icon.getLocationInWindow(location)
        val iconX = location[0]
        val iconY = location[1]
        val iconBottom = iconY + icon.height
        val iconCenterY = iconY + icon.height / 2
        val iconRight = iconX + icon.width
        iconRect.set(iconX, iconY, iconRight, iconBottom)

        // Preferred: the icon and the hint text share one dedicated layer.
        var layer = icon.parent as? ViewGroup
        var depth = 0
        while (layer != null && layer !== root && depth < 3) {
            if (isDedicatedLayer(layer, iconRight)) {
                hide(layer)
                report("layer", layer)
                return
            }
            layer = layer.parent as? ViewGroup
            depth++
        }

        // Fallback: the pill lives next to the icon in the header root.
        val header = headerScope() ?: return
        val limit = maxOf((16f * d).toInt(), (icon.height * .8f).toInt())
        val candidates = ArrayList<TextView>(4)
        collect(header, candidates, 0, 300)
        var hidden = 0
        for (text in candidates) {
            if (!text.isShown || text.text.isNullOrBlank() || text.background == null) continue
            if (text.width <= 0 || text.width > root.width * .8f) continue
            text.getLocationInWindow(location)
            val top = location[1]
            val bottom = top + text.height
            val right = location[0] + text.width
            // Right half of the header only: the login chip and tab labels sit
            // left of this band and must keep their native behaviour.
            if (right > iconRight + (4f * d).toInt()) continue
            if (right < leftOfTabs() - (8f * d).toInt()) continue
            if (bottom < iconY - limit || top > iconBottom + limit) continue
            if (abs(top + text.height / 2 - iconCenterY) > limit) continue
            hide(text)
            hidden++
        }
        if (hidden > 0) report("hint", candidates.firstOrNull { text ->
            text.isShown && !text.text.isNullOrBlank() && text.background != null
        })
    }

    private fun isDedicatedLayer(layer: ViewGroup, iconRight: Int): Boolean {
        if (contains(layer, tabs)) return false
        if (menu != null && contains(layer, menu)) return false
        if (containsPager(layer)) return false
        if (containsOwned(layer)) return false
        if (layer.width < icon.width * 2) return false
        if (layer.height !in 1..(icon.height * 2.6f).toInt()) return false
        layer.getLocationInWindow(location)
        bounds.set(location[0], location[1], location[0] + layer.width, location[1] + layer.height)
        // Stay in the header band: a page-sized layer is never a search row.
        if (bounds.width() > root.width * .98f && bounds.height() > icon.height * 1.8f) return false
        if (!Rect.intersects(bounds, iconRect)) return false
        // The layer must actually carry the search entry left of the icon.
        val text = findHintText(layer, iconRight) ?: return false
        return text.width > icon.width && text.background != null
    }

    private fun findHintText(scope: ViewGroup, iconRight: Int): TextView? {
        val pending = ArrayList<View>(8)
        pending.add(scope)
        var budget = 40
        while (pending.isNotEmpty() && budget-- > 0) {
            val view = pending.removeAt(pending.size - 1)
            if (view === icon || view is AdapterOwnedOverlay || view is GlassHostLayout) continue
            if (view is TextView) {
                if (!view.text.isNullOrBlank() && view.isShown && view.width > 0 &&
                    view.background != null) {
                    view.getLocationInWindow(location)
                    if (location[0] + view.width <= iconRight + (4f * view.resources.displayMetrics.density).toInt())
                        return view
                }
                continue
            }
            if (view is ViewGroup) for (i in 0 until view.childCount) pending.add(view.getChildAt(i))
        }
        return null
    }

    private fun headerScope(): ViewGroup? {
        var parent = icon.parent as? ViewGroup
        while (parent != null) {
            if (parent === root) return parent
            if (contains(parent, tabs)) return parent
            parent = parent.parent as? ViewGroup
        }
        return null
    }

    private fun leftOfTabs(): Int {
        tabs.getLocationInWindow(location)
        return location[0]
    }

    private fun collect(scope: ViewGroup, out: MutableList<TextView>, depth: Int, budget: Int): Int {
        if (budget <= 0 || depth > 8) return budget
        for (i in 0 until scope.childCount) {
            if (budget <= 0) return budget
            val child = scope.getChildAt(i)
            if (child === tabs || child === menu || child === icon) continue
            if (child is AdapterOwnedOverlay || child is GlassHostLayout) continue
            if (contains(child, tabs) || (menu != null && contains(child, menu))) continue
            if (child is TextView) out.add(child)
            else if (child is ViewGroup) budget = collect(child, out, depth + 1, budget - 1)
        }
        return budget
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

    private fun report(kind: String, hidden: View?) {
        if (reported) return
        reported = true
        val layer = icon.parent as? ViewGroup
        val children = layer?.let { parent ->
            (0 until parent.childCount).joinToString(",") { index ->
                parent.getChildAt(index).javaClass.simpleName
            }
        }
        android.util.Log.i("LiquidTabXhsTop",
            "search chrome hidden by $kind; icon-parent=${layer?.javaClass?.name} children=[$children] " +
                "hidden=${hidden?.javaClass?.name}")
    }
}
