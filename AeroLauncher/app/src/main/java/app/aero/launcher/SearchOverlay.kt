package app.aero.launcher

import android.content.Context
import android.graphics.Color
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.MotionEvent
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout

/** البحث: يفتح الكيبورد فوراً، وتحت الشريط أكثر 10 تطبيقات استخداماً. */
class SearchOverlay(private val a: MainActivity) : FrameLayout(a) {
    private val d = resources.displayMetrics.density
    private val edit = EditText(a)
    private val grid = ResultsGrid(a)
    private val col = LinearLayout(a)
    private var shown: List<AppInfo> = emptyList()
    private var sy = 0f

    init {
        setBackgroundColor(0xEB090B12.toInt())
        isClickable = true
        val ins = a.root.inset
        col.orientation = LinearLayout.VERTICAL
        col.setPadding(ins.left + (14 * d).toInt(), ins.top + (12 * d).toInt(), ins.right + (14 * d).toInt(), 0)
        edit.apply {
            hint = "بحث في التطبيقات"
            setHintTextColor(0x99FFFFFF.toInt()); setTextColor(Color.WHITE); textSize = 17f
            isSingleLine = true
            inputType = InputType.TYPE_CLASS_TEXT
            background = rr(0x2EFFFFFF, 26 * d)
            setPadding((20 * d).toInt(), 0, (20 * d).toInt(), 0)
            imeOptions = EditorInfo.IME_ACTION_GO or EditorInfo.IME_FLAG_NO_EXTRACT_UI or EditorInfo.IME_FLAG_NO_FULLSCREEN
        }
        col.addView(edit, LinearLayout.LayoutParams(-1, (52 * d).toInt()))
        col.addView(grid, LinearLayout.LayoutParams(-1, -2).apply { topMargin = (14 * d).toInt() })
        addView(col, FrameLayout.LayoutParams(-1, -2))
        edit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, af: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
            override fun afterTextChanged(s: Editable?) { refresh(s.toString()) }
        })
        edit.setOnEditorActionListener { _, _, _ -> shown.firstOrNull()?.let { a.launch(it) }; true }
        setOnClickListener { close() }
        refresh("")
    }

    private fun refresh(q: String) {
        val key = q.trim()
        val base = a.visible.sortedByDescending { a.st.usage(it.key) }
        val l = if (key.isEmpty()) base else base.filter { it.label.contains(key, true) || it.orig.contains(key, true) }
        shown = l.take(10)
        grid.removeAllViews()
        shown.forEach { app ->
            val c = AppCell(a, app, true, a.st.scale)
            c.setOnClickListener { a.launch(app, c) }
            c.setOnLongClickListener { a.appMenu(app); true }
            grid.addView(c)
        }
    }

    fun open() {
        alpha = 0f; col.translationY = -24 * d
        animate().alpha(1f).setDuration(200).start()
        col.animate().translationY(0f).setDuration(260).setInterpolator(DecelerateInterpolator()).start()
        edit.requestFocus()
        val im = a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        edit.postDelayed({ im.showSoftInput(edit, 0) }, 100)
        edit.postDelayed({ if (edit.hasFocus()) im.showSoftInput(edit, 0) }, 450)
    }

    fun close(animate: Boolean = true) {
        val im = a.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        im.hideSoftInputFromWindow(windowToken, 0)
        a.search = null
        if (animate) animate().alpha(0f).setDuration(160).withEndAction { (parent as? ViewGroup)?.removeView(this) }.start()
        else (parent as? ViewGroup)?.removeView(this)
    }

    override fun dispatchTouchEvent(e: MotionEvent): Boolean {
        if (e.actionMasked == MotionEvent.ACTION_DOWN) sy = e.y
        if (e.actionMasked == MotionEvent.ACTION_MOVE && e.y - sy < -80 * d) { close(); return true }
        return super.dispatchTouchEvent(e)
    }
}
