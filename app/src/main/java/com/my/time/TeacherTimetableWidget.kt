package com.my.time

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.View
import android.widget.RemoteViews
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Calendar

class TeacherTimetableWidget : AppWidgetProvider() {

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray
    ) {
        for (id in appWidgetIds) updateWidget(context, appWidgetManager, id)
    }

    override fun onAppWidgetOptionsChanged(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetId: Int,
        newOptions: Bundle
    ) {
        updateWidget(context, appWidgetManager, appWidgetId)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove(keyFor(it)) }
        prefs.apply()
    }

    companion object {
        const val PREFS = "widget_prefs"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun keyFor(id: Int) = "teacher_$id"

        private enum class Mode { SMALL, MEDIUM, LARGE }

        private data class WidgetUi(
            val mode: Mode,
            val rootPaddingH: Int,
            val rootPaddingV: Int,
            val teacherSizeSp: Float,
            val headerSizeSp: Float,
            val periodSizeSp: Float,
            val subjectSizeSp: Float,
            val classSizeSp: Float,
            val periodWidthDp: Int,
            val showClass: Boolean,
            val maxRows: Int
        )

        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val teacher = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(keyFor(id), null)
            val ui = computeUi(mgr.getAppWidgetOptions(id))

            if (teacher.isNullOrBlank()) {
                mgr.updateAppWidget(id, errorViews(ctx, id, ui, "선생님 미설정", "눌러서 설정"))
                return
            }

            mgr.updateAppWidget(id, errorViews(ctx, id, ui, teacher, "불러오는 중…"))

            scope.launch {
                try {
                    val day = todayLabel()
                    val entries = DataFetcher.teacherToday(teacher, day)
                    val views = buildViews(ctx, teacher, day, entries, id, ui)
                    withContext(Dispatchers.Main) { mgr.updateAppWidget(id, views) }
                } catch (e: Exception) {
                    Log.e("TeacherWidget", "update failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        mgr.updateAppWidget(id, errorViews(ctx, id, ui, teacher, "로드 실패"))
                    }
                }
            }
        }

        private fun computeUi(options: Bundle): WidgetUi {
            val width = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 110)
            val height = options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 90)

            return when {
                height <= 120 || width <= 150 -> WidgetUi(
                    mode = Mode.SMALL,
                    rootPaddingH = 6,
                    rootPaddingV = 4,
                    teacherSizeSp = 15f,
                    headerSizeSp = 10f,
                    periodSizeSp = 10f,
                    subjectSizeSp = 12f,
                    classSizeSp = 0f,
                    periodWidthDp = 30,
                    showClass = false,
                    maxRows = 2
                )
                height <= 190 || width <= 240 -> WidgetUi(
                    mode = Mode.MEDIUM,
                    rootPaddingH = 8,
                    rootPaddingV = 6,
                    teacherSizeSp = 15.5f,
                    headerSizeSp = 11f,
                    periodSizeSp = 10f,
                    subjectSizeSp = 12f,
                    classSizeSp = 10f,
                    periodWidthDp = 30,
                    showClass = true,
                    maxRows = 4
                )
                else -> WidgetUi(
                    mode = Mode.LARGE,
                    rootPaddingH = 8,
                    rootPaddingV = 6,
                    teacherSizeSp = 15.5f,
                    headerSizeSp = 11f,
                    periodSizeSp = 10f,
                    subjectSizeSp = 12f,
                    classSizeSp = 10f,
                    periodWidthDp = 30,
                    showClass = true,
                    maxRows = 7
                )
            }
        }

        private fun buildViews(
            ctx: Context,
            teacher: String,
            day: String,
            entries: List<DataFetcher.Entry>,
            widgetId: Int,
            ui: WidgetUi
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_layout)
            applySizing(v, ctx, ui)

            val dayName = when (day) {
                "월" -> "월요일"; "화" -> "화요일"; "수" -> "수요일"
                "목" -> "목요일"; "금" -> "금요일"; else -> day
            }

            v.setTextViewText(R.id.teacher_name, teacher)
            val summary = if (entries.isEmpty()) "$dayName · 수업 없음" else "$dayName · ${entries.size}시간"
            v.setTextViewText(R.id.widget_header, summary)

            val visibleEntries = entries.take(ui.maxRows)
            val res = ctx.resources
            val pkg = ctx.packageName
            for (slot in 1..7) {
                val rowId = res.getIdentifier("row_p$slot", "id", pkg)
                val periodId = res.getIdentifier("period_p$slot", "id", pkg)
                val subjectId = res.getIdentifier("subject_p$slot", "id", pkg)
                val classId = res.getIdentifier("class_p$slot", "id", pkg)

                val e = visibleEntries.getOrNull(slot - 1)
                if (e != null) {
                    v.setViewVisibility(rowId, View.VISIBLE)
                    v.setTextViewText(periodId, "${e.period}교시")
                    v.setTextViewText(subjectId, e.subject)
                    v.setTextViewText(classId, "${e.grade}-${e.ban}")
                    v.setViewVisibility(classId, if (ui.showClass) View.VISIBLE else View.GONE)
                } else {
                    v.setViewVisibility(rowId, View.GONE)
                }
            }

            val openConfigIntent = Intent(ctx, ConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val configPi = PendingIntent.getActivity(
                ctx,
                widgetId,
                openConfigIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.teacher_name, configPi)

            val refreshIntent = Intent(ctx, TeacherTimetableWidget::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(
                    AppWidgetManager.EXTRA_APPWIDGET_IDS,
                    AppWidgetManager.getInstance(ctx).getAppWidgetIds(
                        ComponentName(ctx, TeacherTimetableWidget::class.java)
                    )
                )
            }
            val refreshPi = PendingIntent.getBroadcast(
                ctx,
                widgetId + 10000,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_header, refreshPi)
            return v
        }

        private fun errorViews(
            ctx: Context,
            widgetId: Int,
            ui: WidgetUi,
            teacherLabel: String,
            msg: String
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_layout)
            applySizing(v, ctx, ui)
            v.setTextViewText(R.id.teacher_name, teacherLabel)
            v.setTextViewText(R.id.widget_header, msg)
            for (p in 1..7) {
                val rowId = ctx.resources.getIdentifier("row_p$p", "id", ctx.packageName)
                v.setViewVisibility(rowId, View.GONE)
            }

            val openConfigIntent = Intent(ctx, ConfigActivity::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_CONFIGURE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            val configPi = PendingIntent.getActivity(
                ctx,
                widgetId,
                openConfigIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.teacher_name, configPi)
            return v
        }

        private fun applySizing(v: RemoteViews, ctx: Context, ui: WidgetUi) {
            v.setViewPadding(R.id.widget_root, ui.rootPaddingH, ui.rootPaddingV, ui.rootPaddingH, ui.rootPaddingV)
            v.setTextViewTextSize(R.id.teacher_name, TypedValue.COMPLEX_UNIT_SP, ui.teacherSizeSp)
            v.setTextViewTextSize(R.id.widget_header, TypedValue.COMPLEX_UNIT_SP, ui.headerSizeSp)

            val res = ctx.resources
            val pkg = ctx.packageName
            for (p in 1..7) {
                val periodId = res.getIdentifier("period_p$p", "id", pkg)
                val subjectId = res.getIdentifier("subject_p$p", "id", pkg)
                val classId = res.getIdentifier("class_p$p", "id", pkg)
                v.setTextViewTextSize(periodId, TypedValue.COMPLEX_UNIT_SP, ui.periodSizeSp)
                v.setTextViewTextSize(subjectId, TypedValue.COMPLEX_UNIT_SP, ui.subjectSizeSp)
                if (ui.classSizeSp > 0f) {
                    v.setTextViewTextSize(classId, TypedValue.COMPLEX_UNIT_SP, ui.classSizeSp)
                }
                v.setInt(periodId, "setWidth", dpToPx(ctx, ui.periodWidthDp))
            }
        }

        private fun dpToPx(ctx: Context, dp: Int): Int {
            return (dp * ctx.resources.displayMetrics.density).toInt()
        }

        private fun todayLabel(): String {
            val cal = Calendar.getInstance()
            return when (cal.get(Calendar.DAY_OF_WEEK)) {
                Calendar.MONDAY -> "월"
                Calendar.TUESDAY -> "화"
                Calendar.WEDNESDAY -> "수"
                Calendar.THURSDAY -> "목"
                Calendar.FRIDAY -> "금"
                else -> "월"
            }
        }
    }
}
