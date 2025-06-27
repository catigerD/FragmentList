package com.example.fragmentlist.adapter

import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder

open class FragmentListItemDragCallback(
    private val adapter: FragmentListAdapter
) : ItemTouchHelper.Callback() {
    override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: ViewHolder): Int {
        val dragFlags = ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.START or ItemTouchHelper.END
        val swipeFlags = 0 //禁用滑动删除
        return makeMovementFlags(dragFlags, swipeFlags)
    }

    override fun onMove(recyclerView: RecyclerView, viewHolder: ViewHolder, target: ViewHolder): Boolean {
        // 通知适配器数据项已移动
        adapter.onItemMove(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)
        return true
    }

    override fun onSwiped(viewHolder: ViewHolder, direction: Int) {
    }

    override fun isLongPressDragEnabled(): Boolean {
        // 启用长按拖拽
        return true
    }
}