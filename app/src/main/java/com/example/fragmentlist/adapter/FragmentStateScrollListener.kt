package com.example.fragmentlist.adapter

import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Job
import kotlinx.coroutines.MainScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal class FragmentStateScrollListener(
    private val action: (ignoreState: Boolean) -> Unit
) : RecyclerView.OnScrollListener() {

    private var triggerJob: Job? = null

    override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
        super.onScrollStateChanged(recyclerView, newState)
        if (newState == RecyclerView.SCROLL_STATE_IDLE) {
            releaseJob()
            action.invoke(false)
        }
    }

    override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (triggerJob?.isActive == true) {
            return
        }
        triggerJob = MainScope().launch {
            delay(100)
            action.invoke(true)
        }
    }

    private fun releaseJob() {
        triggerJob?.cancel()
        triggerJob = null
    }
}