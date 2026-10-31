package app.aero.launcher

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.Drawable
import org.json.JSONObject

class AppInfo(val key: String, val pkg: String, val cls: String, val orig: String, val icon: Drawable) {
    var label: String = orig
    var custom: Bitmap? = null
    fun launchIntent(): Intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        .setClassName(pkg, cls)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
}

class Store(ctx: Context) {
    private val sp = ctx.getSharedPreferences("aero", Context.MODE_PRIVATE)
    private fun lst(k: String): List<String> = (sp.getString(k, "") ?: "").split("\n").filter { it.isNotEmpty() }
    private fun put(k: String, l: List<String>) { sp.edit().putString(k, l.joinToString("\n")).apply() }
    private fun obj(k: String) = JSONObject(sp.getString(k, "{}") ?: "{}")
    private fun putObj(k: String, o: JSONObject) { sp.edit().putString(k, o.toString()).apply() }

    var hidden: List<String>
        get() = lst("hidden")
        set(v) = put("hidden", v)
    var home: List<String>
        get() = lst("home")
        set(v) = put("home", v)
    var dock: List<String>
        get() = lst("dock")
        set(v) = put("dock", v)
    var widgets: List<Int>
        get() = lst("widgets").map { it.toInt() }
        set(v) = put("widgets", v.map { it.toString() })
    var scale: Float
        get() = sp.getFloat("scale", 1f)
        set(v) { sp.edit().putFloat("scale", v).apply() }
    var initDone: Boolean
        get() = sp.getBoolean("init", false)
        set(v) { sp.edit().putBoolean("init", v).apply() }

    fun name(k: String): String = obj("names").optString(k, "")
    fun setName(k: String, v: String) {
        val o = obj("names")
        if (v.isBlank()) o.remove(k) else o.put(k, v.trim())
        putObj("names", o)
    }
    fun iconPath(k: String): String = obj("icons").optString(k, "")
    fun setIconPath(k: String, p: String?) {
        val o = obj("icons")
        if (p == null) o.remove(k) else o.put(k, p)
        putObj("icons", o)
    }
    fun usage(k: String): Int = obj("usage").optInt(k, 0)
    fun bump(k: String) {
        val o = obj("usage"); o.put(k, o.optInt(k, 0) + 1); putObj("usage", o)
    }
}

object Repo {
    fun load(ctx: Context, st: Store): List<AppInfo> {
        val pm = ctx.packageManager
        val i = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(i, 0)
            .filter { it.activityInfo.packageName != ctx.packageName }
            .map { r ->
                val a = r.activityInfo
                val app = AppInfo(a.packageName + "/" + a.name, a.packageName, a.name, r.loadLabel(pm).toString(), r.loadIcon(pm))
                val n = st.name(app.key)
                if (n.isNotEmpty()) app.label = n
                val p = st.iconPath(app.key)
                if (p.isNotEmpty()) app.custom = BitmapFactory.decodeFile(p)
                app
            }
            .sortedBy { it.label.lowercase() }
    }
}
