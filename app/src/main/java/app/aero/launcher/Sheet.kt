package app.aero.launcher

import android.app.Dialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.view.Window
import android.view.WindowManager
import android.view.animation.DecelerateInterpolator
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** Bottom sheet عصري يُبنى بالكود. */
class Sheet(private val ctx: Context) {
    private val d = ctx.resources.displayMetrics.density
    private val dlg = Dialog(ctx)
    private var focus: View? = null
    private val col = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setPadding((8 * d).toInt(), (12 * d).toInt(), (8 * d).toInt(), (24 * d).toInt())
        background = GradientDrawable().apply {
            setColor(0xF21B1C21.toInt()); val r = 28 * d
            cornerRadii = floatArrayOf(r, r, r, r, 0f, 0f, 0f, 0f)
        }
    }

    fun header(text: String, app: AppInfo? = null): Sheet {
        val row = LinearLayout(ctx).apply {
            gravity = Gravity.CENTER_VERTICAL
            setPadding((16 * d).toInt(), (4 * d).toInt(), (16 * d).toInt(), (12 * d).toInt())
        }
        if (app != null) row.addView(AppCell(ctx, app, false, 1f),
            LinearLayout.LayoutParams((52 * d).toInt(), (52 * d).toInt()).apply { marginEnd = (12 * d).toInt() })
        row.addView(TextView(ctx).apply {
            this.text = text; setTextColor(Color.WHITE); textSize = 19f; maxLines = 2
            typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        })
        col.addView(row)
        return this
    }

    fun action(text: String, danger: Boolean = false, block: () -> Unit): Sheet {
        val tv = TextView(ctx).apply {
            this.text = text; textSize = 16f
            setTextColor(if (danger) 0xFFFF6B6B.toInt() else Color.WHITE)
            gravity = Gravity.CENTER_VERTICAL or Gravity.START
            setPadding((16 * d).toInt(), 0, (16 * d).toInt(), 0)
            background = RippleDrawable(ColorStateList.valueOf(0x33FFFFFF), null, rr(Color.WHITE, 16 * d))
            setOnClickListener { block(); dlg.dismiss() }
        }
        col.addView(tv, LinearLayout.LayoutParams(-1, (50 * d).toInt()))
        return this
    }

    fun view(v: View): Sheet {
        val m = (8 * d).toInt()
        col.addView(v, LinearLayout.LayoutParams(-1, -2).apply { setMargins(m, m / 2, m, m) })
        if (v is EditText) focus = v
        return this
    }

    fun show() {
        dlg.requestWindowFeature(Window.FEATURE_NO_TITLE)
        val sv = ScrollView(ctx).apply { isVerticalScrollBarEnabled = false; addView(col) }
        dlg.setContentView(sv)
        dlg.window?.apply {
            setBackgroundDrawable(ColorDrawable(0))
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND)
            setDimAmount(0.5f)
            setGravity(Gravity.BOTTOM)
            setLayout(minOf(ctx.resources.displayMetrics.widthPixels, (520 * d).toInt()), WindowManager.LayoutParams.WRAP_CONTENT)
            if (focus != null) setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE or WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
        }
        col.translationY = 600f * d
        dlg.setOnShowListener {
            col.animate().translationY(0f).setDuration(280).setInterpolator(DecelerateInterpolator(2f)).start()
            focus?.requestFocus()
        }
        dlg.show()
    }
}
