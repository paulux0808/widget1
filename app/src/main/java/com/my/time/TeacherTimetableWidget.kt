package com.my.time

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.util.Log
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

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
        appWidgetIds.forEach { prefs.remove(keyFor(it)) }
        prefs.apply()
    }

    companion object {
        const val PREFS = "widget_prefs"
        private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

        fun keyFor(id: Int) = "teacher_$id"

        fun updateWidget(ctx: Context, mgr: AppWidgetManager, id: Int) {
            val teacher = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(keyFor(id), null)

            if (teacher.isNullOrBlank()) {
                mgr.updateAppWidget(id, errorViews(ctx, id, "선생님 미설정", "눌러서 설정"))
                return
            }

            mgr.updateAppWidget(id, errorViews(ctx, id, teacher, "불러오는 중…"))

            scope.launch {
                try {
                    val day = todayLabel()
                    val entries = DataFetcher.teacherToday(teacher, day)
                    val views = buildViews(ctx, teacher, day, entries, id)
                    withContext(Dispatchers.Main) { mgr.updateAppWidget(id, views) }
                } catch (e: Exception) {
                    Log.e("TeacherWidget", "update failed: ${e.message}", e)
                    withContext(Dispatchers.Main) {
                        mgr.updateAppWidget(
                            id,
                            errorViews(ctx, id, teacher, "로드 실패")
                        )
                    }
                }
            }
        }

        private fun buildViews(
            ctx: Context,
            teacher: String,
            day: String,
            entries: List<DataFetcher.Entry>,
            widgetId: Int
        ): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_layout)
            val dayName = when (day) {
                "월" -> "월요일"; "화" -> "화요일"; "수" -> "수요일"
                "목" -> "목요일"; "금" -> "금요일"; else -> day
            }

            v.setTextViewText(R.id.teacher_name, teacher)
            val summary = if (entries.isEmpty()) "$dayName · 수업 없음" else "$dayName · ${entries.size}시간"
            v.setTextViewText(R.id.widget_header, summary)

            val res = ctx.resources
            val pkg = ctx.packageName
            for (p in 1..7) {
                val rowId = res.getIdentifier("row_p$p", "id", pkg)
                val periodId = res.getIdentifier("period_p$p", "id", pkg)
                val subjectId = res.getIdentifier("subject_p$p", "id", pkg)
                val classId = res.getIdentifier("class_p$p", "id", pkg)
                val e = entries.find { it.period == p }
                if (e != null) {
                    v.setViewVisibility(rowId, View.VISIBLE)
                    v.setTextViewText(periodId, "${p}교시")
                    v.setTextViewText(subjectId, e.subject)
                    v.setTextViewText(classId, "${e.grade}-${e.ban}")
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
                widgetId,
                refreshIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_header, refreshPi)
            return v
        }

        private fun errorViews(ctx: Context, widgetId: Int, teacherLabel: String, msg: String): RemoteViews {
            val v = RemoteViews(ctx.packageName, R.layout.widget_layout)
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
