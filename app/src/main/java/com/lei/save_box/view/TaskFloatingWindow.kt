package com.lei.save_box.view

import android.content.Context
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.lei.save_box.R
import com.lei.save_box.manager.BackgroundTaskManager
import com.lei.save_box.model.BackgroundTask
import com.lei.save_box.model.TaskStatus

class TaskFloatingWindow(private val context: Context) {

    private val taskManager = BackgroundTaskManager.getInstance()
    private var rootView: FrameLayout
    private var badgeContainer: FrameLayout
    private var tvBadge: TextView
    private var panelContainer: LinearLayout
    private var rvTasks: RecyclerView
    private var btnCollapse: ImageButton
    private var adapter: TaskAdapter

    private var isPanelOpen = false
    private var dragDx = 0f
    private var dragDy = 0f
    private var downX = 0f
    private var downY = 0f
    private var hasMoved = false
    private var isAttached = false
    private var parentContainer: FrameLayout? = null

    private val updateRunnable = Runnable { refreshAll() }

    init {
        rootView = LayoutInflater.from(context).inflate(R.layout.view_task_floating, null) as FrameLayout
        badgeContainer = rootView.findViewById(R.id.badgeContainer)
        tvBadge = rootView.findViewById(R.id.tvBadge)
        panelContainer = rootView.findViewById(R.id.panelContainer)
        rvTasks = rootView.findViewById(R.id.rvTasks)
        btnCollapse = rootView.findViewById(R.id.btnCollapse)

        adapter = TaskAdapter { task, action ->
            when (action) {
                TaskAction.CANCEL -> taskManager.cancelTask(task.id)
                TaskAction.RETRY -> taskManager.retryTask(task.id)
                TaskAction.DELETE -> taskManager.deleteTask(task.id)
            }
        }

        rvTasks.layoutManager = LinearLayoutManager(context)
        rvTasks.adapter = adapter

        btnCollapse.setOnClickListener { collapse() }

        badgeContainer.setOnTouchListener { _, event -> handleDrag(event) }

        taskManager.addListener(updateRunnable)
    }

    fun attachTo(container: FrameLayout) {
        if (isAttached) return
        parentContainer = container
        val lp = FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.WRAP_CONTENT,
            FrameLayout.LayoutParams.WRAP_CONTENT
        ).apply {
            gravity = Gravity.RIGHT or Gravity.CENTER
            bottomMargin = 20
        }
        container.addView(rootView, lp)
        isAttached = true
        refreshAll()
    }

    fun detach() {
        if (!isAttached) return
        try { parentContainer?.removeView(rootView) } catch (_: Exception) {}
        isAttached = false
    }

    private fun expand() {
        isPanelOpen = true
        badgeContainer.visibility = View.GONE
        panelContainer.visibility = View.VISIBLE
        val lp = rootView.layoutParams as FrameLayout.LayoutParams
        lp.width = 280.dp2px
        lp.height = 420.dp2px
        rootView.layoutParams = lp
        refreshTaskList()
    }

    private fun collapse() {
        isPanelOpen = false
        panelContainer.visibility = View.GONE
        badgeContainer.visibility = View.VISIBLE
        val lp = rootView.layoutParams as FrameLayout.LayoutParams
        lp.width = FrameLayout.LayoutParams.WRAP_CONTENT
        lp.height = FrameLayout.LayoutParams.WRAP_CONTENT
        rootView.layoutParams = lp
        refreshBadge()
    }

    private fun handleDrag(event: MotionEvent): Boolean {
        return when (event.action) {
            MotionEvent.ACTION_DOWN -> {
                downX = event.rawX
                downY = event.rawY
                dragDx = rootView.x - event.rawX
                dragDy = rootView.y - event.rawY
                hasMoved = false
                true
            }
            MotionEvent.ACTION_MOVE -> {
                val dx = Math.abs(event.rawX - downX)
                val dy = Math.abs(event.rawY - downY)
                if (dx > 10f || dy > 10f) {
                    hasMoved = true
                }
                if (hasMoved) {
                    rootView.x = event.rawX + dragDx
                    rootView.y = event.rawY + dragDy
                }
                true
            }
            MotionEvent.ACTION_UP -> {
                if (!hasMoved) {
                    if (isPanelOpen) collapse() else expand()
                }
                true
            }
            MotionEvent.ACTION_CANCEL -> {
                hasMoved = false
                false
            }
            else -> false
        }
    }

    fun show() {
        rootView.visibility = View.VISIBLE
        refreshAll()
    }

    fun hide() {
        rootView.visibility = View.GONE
    }

    fun destroy() {
        taskManager.removeListener(updateRunnable)
        detach()
    }

    private fun refreshAll() {
        refreshBadge()
    }

    private fun refreshBadge() {
        val count = taskManager.activeCount
        tvBadge.text = count.toString()
//        rootView.visibility = if (count > 0 || isPanelOpen) View.VISIBLE else View.GONE
        refreshTaskList()
    }

    private fun refreshTaskList() {
        adapter.submitList(taskManager.tasks.toList())
    }

    private val Int.dp2px: Int
        get() = (this * context.resources.displayMetrics.density).toInt()
}

enum class TaskAction { CANCEL, RETRY, DELETE }

class TaskAdapter(
    private val onAction: (BackgroundTask, TaskAction) -> Unit
) : RecyclerView.Adapter<TaskAdapter.VH>() {

    private var items: List<BackgroundTask> = emptyList()

    fun submitList(list: List<BackgroundTask>) {
        items = list
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): VH {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_task, parent, false)
        return VH(view)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.bind(items[position])
    }

    override fun getItemCount(): Int = items.size

    inner class VH(view: View) : RecyclerView.ViewHolder(view) {
        private val progressBar: android.widget.ProgressBar = view.findViewById(R.id.progressBar)
        private val tvName: android.widget.TextView = view.findViewById(R.id.tvTaskName)
        private val tvStatus: android.widget.TextView = view.findViewById(R.id.tvTaskStatus)
        private val btnAction: android.widget.ImageButton = view.findViewById(R.id.btnTaskAction)

        fun bind(task: BackgroundTask) {
            tvName.text = task.sourceName
            btnAction.visibility = View.VISIBLE

            when (task.status) {
                TaskStatus.PENDING -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 0
                    tvStatus.text = "等待中"
                    btnAction.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    btnAction.setOnClickListener { onAction(task, TaskAction.CANCEL) }
                }
                TaskStatus.PROCESSING -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = task.progress
                    tvStatus.text = "${task.progress}%"
                    btnAction.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    btnAction.setOnClickListener { onAction(task, TaskAction.CANCEL) }
                }
                TaskStatus.COMPLETED -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    tvStatus.text = "完成"
                    btnAction.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    btnAction.setOnClickListener { onAction(task, TaskAction.DELETE) }
                }
                TaskStatus.FAILED -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    tvStatus.text = "失败"
                    btnAction.setImageResource(android.R.drawable.ic_menu_revert)
                    btnAction.setOnClickListener { onAction(task, TaskAction.RETRY) }
                }
                TaskStatus.CANCELLED -> {
                    progressBar.isIndeterminate = false
                    progressBar.progress = 100
                    tvStatus.text = "已取消"
                    btnAction.setImageResource(android.R.drawable.ic_menu_close_clear_cancel)
                    btnAction.setOnClickListener { onAction(task, TaskAction.DELETE) }
                }
            }
        }
    }
}
