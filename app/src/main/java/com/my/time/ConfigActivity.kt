package com.my.time

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class ConfigActivity : AppCompatActivity() {

    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)
        setContentView(R.layout.activity_config)

        widgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish(); return
        }

        val loading = findViewById<ProgressBar>(R.id.loading)
        val spinner = findViewById<Spinner>(R.id.teacher_spinner)
        val confirm = findViewById<Button>(R.id.confirm_btn)

        CoroutineScope(Dispatchers.IO).launch {
            val result = runCatching { DataFetcher.allTeachers() }
            withContext(Dispatchers.Main) {
                loading.visibility = View.GONE
                result.onSuccess { teachers ->
                    if (teachers.isEmpty()) {
                        Toast.makeText(
                            this@ConfigActivity,
                            "교사 데이터 없음",
                            Toast.LENGTH_LONG
                        ).show()
                        finish(); return@onSuccess
                    }
                    spinner.adapter = ArrayAdapter(
                        this@ConfigActivity,
                        android.R.layout.simple_spinner_dropdown_item,
                        teachers
                    )
                    spinner.visibility = View.VISIBLE
                    confirm.visibility = View.VISIBLE
                }.onFailure { e ->
                    Toast.makeText(
                        this@ConfigActivity,
                        "교사 목록 로드 실패: ${e.message}",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }

        confirm.setOnClickListener {
            val picked = spinner.selectedItem?.toString() ?: return@setOnClickListener
            getSharedPreferences(TeacherTimetableWidget.PREFS, MODE_PRIVATE)
                .edit()
                .putString(TeacherTimetableWidget.keyFor(widgetId), picked)
                .apply()

            TeacherTimetableWidget.updateWidget(
                this, AppWidgetManager.getInstance(this), widgetId
            )
            setResult(
                Activity.RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId)
            )
            finish()
        }
    }
}
