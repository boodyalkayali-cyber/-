package app.aero.launcher

import android.content.Context
import android.content.res.Configuration
import android.graphics.*
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import kotlin.math.abs
import kotlin.math.min

fun ex(n: Int) = View.MeasureSpec.makeMeasureSpec(maxOf(n, 0), View.MeasureSpec.EXACTLY)
fun sz(s: Int) = View.MeasureSpec.getSize(s)
fun rr(color: Int, r: Float) = GradientDrawable().apply { setColor(color); cornerRadius = r }

/** أيقونة + اسم. الحجم يُحسب من مساحة الخلية فلا يحدث تداخل ولا قصّ. */
class AppCell(ctx: Context, val app: AppInfo, private val showLabel: Boolean, private val scale: Float) : View(ctx) {
    private val d = resources.displayMetrics.density
    private val tf = Typeface.create("sans-serif-light", Typeface.NORMAL)
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE; textAlign = Paint.Align.CENTER; typeface = tf }
    private val outline = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = Color.BLACK; textAlign = Paint.Align.CENTER; style = Paint.Style.STROKE
        strokeJoin = Paint.Join.ROUND; typeface = tf
    }
    private val clip = Path()
    private val bp = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)

    init { isClickable = true; isLongClickable = true }

    override fun setPressed(pressed: Boolean) {
        super.setPressed(pressed)
        val s = if (pressed) 0.86f else 1f
        animate().scaleX(s).scaleY(s).setDuration(110).start()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat(); val h = height.toFloat()
        val ts = min(min(13f * d, h * 0.17f), w * 0.19f).coerceAtLeast(7f * d)
        val lh = if (showLabel) ts * 1.4f else 0f
        val gap = if (showLabel) 3f * d else 0f
        val fit = min(w * 0.8f, h - lh - gap - 4f * d).coerceAtLeast(8f * d)
        val s = fit * scale
        val top = (h - s - gap - lh) / 2f
        icon(c, (w - s) / 2f, top, s)
        if (showLabel) label(c, w, top + s + gap, ts, lh)
    }

    private fun icon(c: Canvas, l: Float, t: Float, s: Float) {
        val cb = app.custom
        if (cb != null) {
            val rect = RectF(l, t, l + s, t + s)
            clip.reset(); clip.addRoundRect(rect, s * 0.225f, s * 0.225f, Path.Direction.CW)
            c.save(); c.clipPath(clip); c.drawBitmap(cb, null, rect, bp); c.restore()
        } else {
            app.icon.setBounds(l.toInt(), t.toInt(), (l + s).toInt(), (t + s).toInt())
            app.icon.draw(c)
        }
    }

    /** سطر واحد: نضيف كلمات كاملة فقط، والكلمة التي لا تتسع تُحذف (بدون ... أو قصّ). */
    private fun label(c: Canvas, w: Float, top: Float, ts: Float, lh: Float) {
        val avail = w - 6f * d
        val words = app.label.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
        if (words.isEmpty()) return
        var size = ts
        fill.textSize = size
        val fw = fill.measureText(words[0])
        if (fw > avail) { size = size * avail / fw; fill.textSize = size }
        var text = words[0]
        for (i in 1 until words.size) {
            val t = text + " " + words[i]
            if (fill.measureText(t) <= avail) text = t else break
        }
        outline.textSize = size; outline.strokeWidth = size * 0.16f
        val fm = fill.fontMetrics
        val base = top + (lh - (fm.descent - fm.ascent)) / 2f - fm.ascent
        c.drawText(text, w / 2f, base, outline)
        c.drawText(text, w / 2f, base, fill)
    }
}

class GridPage(ctx: Context, private val cols: Int, private val rows: Int,
               private val cells: List<AppCell>, private val padH: Int) : ViewGroup(ctx) {
    var fixedH = 0
    init { cells.forEach { addView(it) } }
    private fun cw(w: Int) = (w - 2 * padH) / cols
    private fun chh(h: Int) = if (fixedH > 0) fixedH else h / rows
    override fun onMeasure(ws: Int, hs: Int) {
        val w = sz(ws); val h = sz(hs); setMeasuredDimension(w, h)
        cells.forEach { it.measure(ex(cw(w)), ex(chh(h))) }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val cellW = cw(r - l); val cellH = chh(b - t)
        cells.forEachIndexed { i, c ->
            val x = padH + (i % cols) * cellW; val y = (i / cols) * cellH
            c.layout(x, y, x + cellW, y + cellH)
        }
    }
}

class HomePage(ctx: Context, private val land: Boolean, favs: List<AppCell>, widgets: List<View>) : ViewGroup(ctx) {
    private val d = resources.displayMetrics.density
    private val cols = if (land) 3 else 5
    private val rows = (favs.size + cols - 1) / cols
    private val box = LinearLayout(ctx)
    private val grid: GridPage
    init {
        box.orientation = LinearLayout.VERTICAL
        val p = (8 * d).toInt(); box.setPadding(p, p / 2, p, p / 2)
        if (widgets.isEmpty()) {
            val tv = TextView(ctx).apply {
                text = "اضغط مطولاً على أي مساحة فارغة\nلإضافة ويدجت"
                setTextColor(0x99FFFFFF.toInt()); gravity = Gravity.CENTER; textSize = 14f
            }
            box.addView(tv, LinearLayout.LayoutParams(-1, -1))
        }
        widgets.forEach {
            box.addView(it, LinearLayout.LayoutParams(-1, 0, 1f).apply { setMargins(p / 2, p / 2, p / 2, p / 2) })
        }
        grid = GridPage(ctx, cols, maxOf(rows, 1), favs, (6 * d).toInt())
        addView(box); addView(grid)
    }
    private fun gridH(h: Int): Int = rows * (if (land) h / 5 else h / 6)
    override fun onMeasure(ws: Int, hs: Int) {
        val w = sz(ws); val h = sz(hs); setMeasuredDimension(w, h)
        grid.fixedH = if (land) h / 5 else h / 6
        if (land) { box.measure(ex(w - w / 2), ex(h)); grid.measure(ex(w / 2), ex(gridH(h))) }
        else { box.measure(ex(w), ex(h - gridH(h))); grid.measure(ex(w), ex(gridH(h))) }
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        if (land) { box.layout(0, 0, w - w / 2, h); grid.layout(w - w / 2, 0, w, gridH(h)) }
        else { box.layout(0, 0, w, h - gridH(h)); grid.layout(0, h - gridH(h), w, h) }
    }
}

/** الدوك: أفقي في الوضع الطولي، وعمودي (بنفس مكانه الفيزيائي) في الوضع الأفقي. بلا أسماء. */
class DockView(ctx: Context) : ViewGroup(ctx) {
    var vertical = false
    private val d = resources.displayMetrics.density
    init {
        background = GradientDrawable().apply {
            cornerRadius = 30f * d; setColor(0x44FFFFFF); setStroke((1 * d).toInt(), 0x33FFFFFF)
        }
    }
    private val pad get() = (8 * d).toInt()
    override fun onMeasure(ws: Int, hs: Int) {
        val w = sz(ws); val h = sz(hs); setMeasuredDimension(w, h)
        val (cw, ch) = slot(w, h)
        for (i in 0 until childCount) getChildAt(i).measure(ex(cw), ex(ch))
    }
    private fun slot(w: Int, h: Int) = if (vertical) Pair(w, (h - 2 * pad) / 5) else Pair((w - 2 * pad) / 5, h)
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val h = b - t
        val (cw, ch) = slot(w, h)
        val n = childCount
        for (i in 0 until n) {
            val v = getChildAt(i)
            if (vertical) { val y = (h - n * ch) / 2 + i * ch; v.layout(0, y, cw, y + ch) }
            else { val x = (w - n * cw) / 2 + i * cw; v.layout(x, 0, x + cw, ch) }
        }
    }
}

class Dots(ctx: Context) : View(ctx) {
    var count = 1
        set(v) { field = v; invalidate() }
    var pos = 0f
        set(v) { field = v; invalidate() }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    override fun onDraw(c: Canvas) {
        val d = resources.displayMetrics.density; val gap = 14 * d
        val x0 = (width - (count - 1) * gap) / 2f
        val rtl = layoutDirection == LAYOUT_DIRECTION_RTL
        for (i in 0 until count) {
            val k = (1 - abs(pos - i)).coerceIn(0f, 1f)
            p.color = Color.argb((90 + 165 * k).toInt(), 255, 255, 255)
            val idx = if (rtl) count - 1 - i else i
            c.drawCircle(x0 + idx * gap, height / 2f, (2.6f + 1.6f * k) * d, p)
        }
    }
}

class LauncherLayout(ctx: Context, val pager: View, val dots: View, val dock: View) : ViewGroup(ctx) {
    val inset = Rect()
    /** 0 = الدوك أسفل، 1 = يسار الجهاز، 2 = يمين الجهاز */
    var side = 0
    var onSwipeDown: (() -> Unit)? = null
    private val d = resources.displayMetrics.density
    private val rp = Rect(); private val rd = Rect(); private val rk = Rect()
    private var sx = 0f; private var sy = 0f; private var fired = false
    init { addView(pager); addView(dots); addView(dock) }

    private fun geo(w: Int, h: Int) {
        val l = inset.left; val t = inset.top; val r = w - inset.right; val b = h - inset.bottom
        val th = (86 * d).toInt(); val m = (10 * d).toInt(); val dh = (20 * d).toInt()
        when (side) {
            0 -> { rk.set(l + m * 2, b - th - m, r - m * 2, b - m); rp.set(l, t, r, rk.top - dh); rd.set(l, rp.bottom, r, rk.top) }
            1 -> { rk.set(l + m, t + m, l + m + th, b - m); val x = rk.right + m; rp.set(x, t, r, b - dh); rd.set(x, b - dh, r, b) }
            else -> { rk.set(r - m - th, t + m, r - m, b - m); rp.set(l, t, rk.left - m, b - dh); rd.set(l, b - dh, rk.left - m, b) }
        }
    }
    override fun onMeasure(ws: Int, hs: Int) {
        val w = sz(ws); val h = sz(hs); setMeasuredDimension(w, h); geo(w, h)
        pager.measure(ex(rp.width()), ex(rp.height()))
        dots.measure(ex(rd.width()), ex(rd.height()))
        dock.measure(ex(rk.width()), ex(rk.height()))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        pager.layout(rp.left, rp.top, rp.right, rp.bottom)
        dots.layout(rd.left, rd.top, rd.right, rd.bottom)
        dock.layout(rk.left, rk.top, rk.right, rk.bottom)
    }
    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> { sx = e.x; sy = e.y; fired = false }
            MotionEvent.ACTION_MOVE -> if (!fired) {
                val dy = e.y - sy; val dx = e.x - sx
                if (dy > 70 * d && dy > 1.6f * abs(dx)) {
                    fired = true
                    val c = MotionEvent.obtain(e); c.action = MotionEvent.ACTION_CANCEL
                    super.dispatchTouchEvent(c); c.recycle()
                    onSwipeDown?.invoke()
                    return true
                }
            }
        }
        return super.dispatchTouchEvent(e)
    }
}

class PagesAdapter(private val pages: List<View>) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    override fun getItemCount() = pages.size
    override fun getItemViewType(position: Int) = position
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val v = pages[viewType]
        (v.parent as? ViewGroup)?.removeView(v)
        v.layoutParams = RecyclerView.LayoutParams(-1, -1)
        return object : RecyclerView.ViewHolder(v) {}
    }
    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {}
}

/** نتائج البحث: سطران × 5 طولياً، وسطر واحد × 10 أفقياً. */
class ResultsGrid(ctx: Context) : ViewGroup(ctx) {
    private val d = resources.displayMetrics.density
    private val land get() = resources.configuration.orientation == Configuration.ORIENTATION_LANDSCAPE
    private val cols get() = if (land) 10 else 5
    private fun cellH(w: Int): Int {
        val cw = w / cols
        return if (land) min((cw * 1.1f).toInt(), (76 * d).toInt()) else min((cw * 1.3f).toInt(), (96 * d).toInt())
    }
    override fun onMeasure(ws: Int, hs: Int) {
        val w = sz(ws); val rows = (childCount + cols - 1) / cols; val ch = cellH(w)
        setMeasuredDimension(w, rows * ch)
        for (i in 0 until childCount) getChildAt(i).measure(ex(w / cols), ex(ch))
    }
    override fun onLayout(changed: Boolean, l: Int, t: Int, r: Int, b: Int) {
        val w = r - l; val cw = w / cols; val ch = cellH(w)
        for (i in 0 until childCount) {
            val x = (i % cols) * cw; val y = (i / cols) * ch
            getChildAt(i).layout(x, y, x + cw, y + ch)
        }
    }
}
