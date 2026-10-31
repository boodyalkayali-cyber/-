package app.aero.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProviderInfo
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Outline
import android.graphics.drawable.GradientDrawable
import android.hardware.display.DisplayManager
import android.media.ThumbnailUtils
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.provider.Settings
import android.view.Surface
import android.view.View
import android.view.ViewOutlineProvider
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.viewpager2.widget.ViewPager2
import java.io.File
import java.io.FileOutputStream
import kotlin.math.abs

class MainActivity : Activity() {
    lateinit var st: Store
    lateinit var frame: FrameLayout
    lateinit var root: LauncherLayout
    private lateinit var pager: ViewPager2
    private lateinit var dots: Dots
    private lateinit var dock: DockView
    private lateinit var host: AppWidgetHost
    private lateinit var mgr: AppWidgetManager
    var all: List<AppInfo> = emptyList()
    var search: SearchOverlay? = null
    private var pendingKey = ""
    private var pendingWidget = -1
    private var lastRot = -1

    private val REQ_ICON = 11; private val REQ_BIND = 12; private val REQ_CONFIG = 13

    val d: Float get() = resources.displayMetrics.density
    val visible: List<AppInfo> get() = all.filter { it.key !in st.hidden }
    private val land: Boolean get() = resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    @Suppress("DEPRECATION")
    private fun rot(): Int = windowManager.defaultDisplay.rotation

    private val pkgReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) { reload() }
    }
    private val dl = object : DisplayManager.DisplayListener {
        override fun onDisplayAdded(id: Int) {}
        override fun onDisplayRemoved(id: Int) {}
        override fun onDisplayChanged(id: Int) { if (rot() != lastRot) rebuild() }
    }

    override fun onCreate(b: Bundle?) {
        super.onCreate(b)
        st = Store(this)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (Build.VERSION.SDK_INT >= 29) { window.isNavigationBarContrastEnforced = false }
        mgr = AppWidgetManager.getInstance(this)
        host = AppWidgetHost(this, 1024)
        pager = ViewPager2(this); dots = Dots(this); dock = DockView(this)
        pager.offscreenPageLimit = 1
        pager.setPageTransformer { v, pos ->
            val k = 1f - abs(pos).coerceAtMost(1f)
            v.alpha = 0.35f + 0.65f * k
            v.scaleX = 0.93f + 0.07f * k; v.scaleY = 0.93f + 0.07f * k
        }
        pager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageScrolled(p: Int, o: Float, px: Int) { dots.pos = p + o }
        })
        root = LauncherLayout(this, pager, dots, dock)
        root.background = GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, intArrayOf(0x33000000, 0x00000000, 0x66000000))
        root.onSwipeDown = { openSearch() }
        frame = FrameLayout(this)
        frame.addView(root, FrameLayout.LayoutParams(-1, -1))
        setContentView(frame)
        ViewCompat.setOnApplyWindowInsetsListener(frame) { _, ins ->
            val bars = ins.getInsets(WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.displayCutout())
            root.inset.set(bars.left, bars.top, bars.right, bars.bottom)
            root.requestLayout()
            ins
        }
        ViewCompat.requestApplyInsets(frame)
        reload()
    }

    override fun onStart() {
        super.onStart()
        try { host.startListening() } catch (e: Exception) {}
        val f = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED); addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED); addDataScheme("package")
        }
        if (Build.VERSION.SDK_INT >= 33) registerReceiver(pkgReceiver, f, Context.RECEIVER_EXPORTED) else registerReceiver(pkgReceiver, f)
        getSystemService(DisplayManager::class.java).registerDisplayListener(dl, null)
        if (rot() != lastRot) rebuild()
    }

    override fun onStop() {
        super.onStop()
        try { host.stopListening() } catch (e: Exception) {}
        try { unregisterReceiver(pkgReceiver) } catch (e: Exception) {}
        getSystemService(DisplayManager::class.java).unregisterDisplayListener(dl)
    }

    override fun onConfigurationChanged(c: android.content.res.Configuration) {
        super.onConfigurationChanged(c)
        rebuild()
    }

    override fun onNewIntent(i: Intent?) {
        super.onNewIntent(i)
        search?.close(false)
        pager.setCurrentItem(0, true)
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (search != null) search?.close() else if (pager.currentItem != 0) pager.currentItem = 0
    }

    // ---------- البيانات والبناء ----------
    fun reload() {
        all = Repo.load(this, st)
        if (!st.initDone && all.isNotEmpty()) { initDefaults(); st.initDone = true }
        rebuild()
    }

    private fun initDefaults() {
        fun pk(i: Intent) = packageManager.resolveActivity(i, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
        val browser = if (all.any { it.pkg == "com.android.chrome" }) "com.android.chrome"
        else pk(Intent(Intent.ACTION_VIEW, Uri.parse("http://example.com")))
        val pkgs = listOf(pk(Intent(Intent.ACTION_DIAL)), pk(Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:"))),
            pk(Intent(MediaStore.ACTION_IMAGE_CAPTURE)), browser)
        st.dock = pkgs.mapNotNull { p -> all.firstOrNull { it.pkg == p }?.key }.distinct()
    }

    private fun cellOf(a: AppInfo, label: Boolean): AppCell {
        val c = AppCell(this, a, label, st.scale)
        c.setOnClickListener { launch(a, c) }
        c.setOnLongClickListener { appMenu(a); true }
        return c
    }

    fun rebuild() {
        lastRot = rot()
        val l = land
        root.side = if (!l) 0 else if (lastRot == Surface.ROTATION_90) 2 else 1
        dock.vertical = l
        val byKey = all.associateBy { it.key }
        val hid = st.hidden
        dock.removeAllViews()
        st.dock.mapNotNull { byKey[it] }.filter { it.key !in hid }.take(5).forEach { dock.addView(cellOf(it, false)) }

        val cols = if (l) 6 else 5; val rows = if (l) 5 else 6
        val pages = ArrayList<View>()
        val favs = st.home.mapNotNull { byKey[it] }.filter { it.key !in hid }.take(15)
        val home = HomePage(this, l, favs.map { cellOf(it, true) }, st.widgets.mapNotNull { widgetView(it) })
        home.setOnLongClickListener { openSettings(); true }
        pages.add(home)
        visible.chunked(cols * rows).forEach { chunk ->
            val g = GridPage(this, cols, rows, chunk.map { cellOf(it, true) }, (6 * d).toInt())
            g.setOnLongClickListener { openSettings(); true }
            pages.add(g)
        }
        val cur = pager.currentItem
        pager.adapter = PagesAdapter(pages)
        pager.setCurrentItem(minOf(cur, pages.size - 1), false)
        dots.count = pages.size
        dots.pos = pager.currentItem.toFloat()
    }

    fun toast(m: String) = Toast.makeText(this, m, Toast.LENGTH_SHORT).show()

    fun launch(a: AppInfo, v: View? = null) {
        st.bump(a.key)
        search?.close(false)
        try {
            val o = if (v != null && v.width > 0) ActivityOptions.makeScaleUpAnimation(v, 0, 0, v.width, v.height).toBundle() else null
            startActivity(a.launchIntent(), o)
        } catch (e: Exception) { toast("تعذر فتح التطبيق") }
    }

    // ---------- البحث ----------
    private fun openSearch() {
        if (search != null) return
        val s = SearchOverlay(this)
        frame.addView(s, FrameLayout.LayoutParams(-1, -1))
        search = s
        s.open()
    }

    // ---------- قوائم التطبيق ----------
    fun appMenu(a: AppInfo) {
        val inHome = a.key in st.home; val inDock = a.key in st.dock
        Sheet(this).header(a.label, a)
            .action(if (inHome) "إزالة من الشاشة الرئيسية" else "إضافة إلى الشاشة الرئيسية") {
                st.home = if (inHome) st.home - a.key else st.home + a.key; rebuild()
            }
            .action(if (inDock) "إزالة من الدوك" else "إضافة إلى الدوك") {
                if (inDock) { st.dock = st.dock - a.key }
                else if (st.dock.size >= 5) { toast("الدوك ممتلئ (5 تطبيقات)") }
                else { st.dock = st.dock + a.key }
                rebuild()
            }
            .action("إعادة تسمية") { rename(a) }
            .action("تغيير الأيقونة") { pickIcon(a) }
            .action("إخفاء التطبيق") {
                st.hidden = st.hidden + a.key; st.home = st.home - a.key; st.dock = st.dock - a.key; rebuild()
            }
            .action("معلومات التطبيق") {
                startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + a.pkg)))
            }
            .action("إلغاء التثبيت", true) {
                startActivity(Intent(Intent.ACTION_DELETE, Uri.parse("package:" + a.pkg)))
            }
            .show()
    }

    private fun rename(a: AppInfo) {
        val et = EditText(this).apply {
            setText(a.label); setSelection(text.length); setTextColor(Color.WHITE); textSize = 17f
            isSingleLine = true; background = rr(0x26FFFFFF, 16 * d)
            val p = (16 * d).toInt(); setPadding(p, p, p, p)
        }
        Sheet(this).header("إعادة تسمية", a).view(et)
            .action("حفظ") { st.setName(a.key, et.text.toString()); reload() }
            .action("استعادة الاسم الأصلي") { st.setName(a.key, ""); reload() }
            .show()
    }

    private fun pickIcon(a: AppInfo) {
        pendingKey = a.key
        Sheet(this).header("تغيير الأيقونة", a)
            .action("اختيار صورة من المعرض") {
                startActivityForResult(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("image/*"), REQ_ICON)
            }
            .action("استعادة الأيقونة الأصلية") { st.setIconPath(a.key, null); reload() }
            .show()
    }

    private fun saveIcon(u: Uri) {
        try {
            val o = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o) }
            var s = 1
            while (o.outWidth / s > 768) s *= 2
            val o2 = BitmapFactory.Options().apply { inSampleSize = s }
            val bmp = contentResolver.openInputStream(u)?.use { BitmapFactory.decodeStream(it, null, o2) } ?: return
            val th = ThumbnailUtils.extractThumbnail(bmp, 256, 256)
            val f = File(filesDir, "ic_" + pendingKey.hashCode() + ".png")
            FileOutputStream(f).use { th.compress(Bitmap.CompressFormat.PNG, 100, it) }
            st.setIconPath(pendingKey, f.absolutePath)
            reload()
        } catch (e: Exception) { toast("تعذر تحميل الصورة") }
    }

    // ---------- الإعدادات ----------
    fun openSettings() {
        val box = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding((12 * d).toInt(), 0, (12 * d).toInt(), 0) }
        val lbl = TextView(this).apply { setTextColor(Color.WHITE); textSize = 15f; text = "حجم الأيقونات ${(st.scale * 100).toInt()}%" }
        val seek = SeekBar(this).apply {
            max = 40; progress = ((st.scale * 100).toInt() - 60).coerceIn(0, 40)
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(s: SeekBar?, p: Int, u: Boolean) { lbl.text = "حجم الأيقونات ${60 + p}%" }
                override fun onStartTrackingTouch(s: SeekBar?) {}
                override fun onStopTrackingTouch(s: SeekBar?) { st.scale = (60 + progress) / 100f; rebuild() }
            })
        }
        box.addView(lbl); box.addView(seek)
        val sh = Sheet(this).header("إعدادات الواجهة").view(box)
            .action("إضافة ويدجت") { pickWidget() }
        if (st.widgets.isNotEmpty()) sh.action("حذف ويدجت") { removeWidgetMenu() }
        if (st.hidden.isNotEmpty()) sh.action("التطبيقات المخفية (${st.hidden.size})") { hiddenMenu() }
        sh.action("تغيير الخلفية") { startActivity(Intent.createChooser(Intent(Intent.ACTION_SET_WALLPAPER), "الخلفية")) }
        sh.show()
    }

    private fun hiddenMenu() {
        val sh = Sheet(this).header("التطبيقات المخفية — اضغط للإظهار")
        all.filter { it.key in st.hidden }.forEach { a -> sh.action(a.label) { st.hidden = st.hidden - a.key; rebuild() } }
        sh.show()
    }

    // ---------- الويدجت ----------
    private fun widgetView(id: Int): View? {
        val info = mgr.getAppWidgetInfo(id) ?: return null
        val hv = host.createView(this, id, info)
        hv.setOnLongClickListener { removeWidgetSheet(id); true }
        hv.addOnLayoutChangeListener { _, l, t, r, b, _, _, _, _ ->
            val w = ((r - l) / d).toInt(); val h = ((b - t) / d).toInt()
            if (w > 0 && h > 0) hv.updateAppWidgetSize(null, w, h, w, h)
        }
        val w = FrameLayout(this)
        w.addView(hv, FrameLayout.LayoutParams(-1, -1))
        w.clipToOutline = true
        w.outlineProvider = object : ViewOutlineProvider() {
            override fun getOutline(v: View, o: Outline) { o.setRoundRect(0, 0, v.width, v.height, 22 * d) }
        }
        return w
    }

    private fun pickWidget() {
        val pm = packageManager
        val sh = Sheet(this).header("اختر ويدجت")
        mgr.installedProviders.sortedBy { it.loadLabel(pm).lowercase() }.forEach { p -> sh.action(p.loadLabel(pm)) { addWidget(p) } }
        sh.show()
    }

    private fun addWidget(p: AppWidgetProviderInfo) {
        val id = host.allocateAppWidgetId()
        pendingWidget = id
        if (mgr.bindAppWidgetIdIfAllowed(id, p.provider)) configureOrAdd(id)
        else startActivityForResult(Intent(AppWidgetManager.ACTION_APPWIDGET_BIND)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, p.provider), REQ_BIND)
    }

    private fun configureOrAdd(id: Int) {
        val info = mgr.getAppWidgetInfo(id)
        if (info?.configure != null) host.startAppWidgetConfigureActivityForResult(this, id, 0, REQ_CONFIG, null)
        else finishAdd(id)
    }

    private fun finishAdd(id: Int) {
        st.widgets = st.widgets + id
        rebuild()
        pager.setCurrentItem(0, true)
    }

    private fun removeWidget(id: Int) {
        host.deleteAppWidgetId(id)
        st.widgets = st.widgets - id
        rebuild()
    }

    private fun removeWidgetSheet(id: Int) {
        Sheet(this).header("ويدجت").action("حذف الويدجت", true) { removeWidget(id) }.show()
    }

    private fun removeWidgetMenu() {
        val sh = Sheet(this).header("حذف ويدجت")
        st.widgets.forEach { id ->
            val name = mgr.getAppWidgetInfo(id)?.loadLabel(packageManager) ?: "ويدجت $id"
            sh.action(name, true) { removeWidget(id) }
        }
        sh.show()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(rc: Int, res: Int, data: Intent?) {
        super.onActivityResult(rc, res, data)
        when (rc) {
            REQ_ICON -> { val u = data?.data; if (res == RESULT_OK && u != null) saveIcon(u) }
            REQ_BIND -> if (res == RESULT_OK) configureOrAdd(pendingWidget) else host.deleteAppWidgetId(pendingWidget)
            REQ_CONFIG -> if (res == RESULT_OK) finishAdd(pendingWidget) else host.deleteAppWidgetId(pendingWidget)
        }
    }
}
