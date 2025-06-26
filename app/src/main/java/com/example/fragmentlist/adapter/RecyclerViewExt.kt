@file:JvmName("RecyclerViewExt")

package com.example.fragmentlist.adapter

import android.util.Pair
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager

/**
 * 获取第一个和最后一个可见项的位置（包括部分可见）
 * @return Pair<第一个可见位置, 最后一个可见位置> (可能为-1表示无可见项)
 */
fun RecyclerView.getVisiblePositions(): Pair<Int, Int> {
    return layoutManager?.let { manager ->
        when (manager) {
            is LinearLayoutManager -> Pair(
                manager.findFirstVisibleItemPosition(),
                manager.findLastVisibleItemPosition()
            )

            is StaggeredGridLayoutManager -> {
                val first = IntArray(manager.spanCount).also {
                    manager.findFirstVisibleItemPositions(it)
                }.minOrNull() ?: -1

                val last = IntArray(manager.spanCount).also {
                    manager.findLastVisibleItemPositions(it)
                }.maxOrNull() ?: -1

                Pair(first, last)
            }

            else -> Pair(-1, -1)
        }
    } ?: Pair(-1, -1)
}

/**
 * 获取第一个和最后一个完全可见项的位置
 * @return Pair<第一个完全可见位置, 最后一个完全可见位置> (可能为-1表示无完全可见项)
 */
fun RecyclerView.getFullyVisiblePositions(): Pair<Int, Int> {
    return layoutManager?.let { manager ->
        when (manager) {
            is LinearLayoutManager -> Pair(
                manager.findFirstCompletelyVisibleItemPosition(),
                manager.findLastCompletelyVisibleItemPosition()
            )

            else -> Pair(-1, -1) // StaggeredGridLayoutManager 不支持完全可见API
        }
    } ?: Pair(-1, -1)
}

/**
 * 获取第一个可见的 ViewHolder（可能为null）
 */
fun RecyclerView.findFirstVisibleViewHolder(): RecyclerView.ViewHolder? {
    return findViewHolderForAdapterPosition(getVisiblePositions().first)
}

/**
 * 获取最后一个可见的 ViewHolder（可能为null）
 */
fun RecyclerView.findLastVisibleViewHolder(): RecyclerView.ViewHolder? {
    return findViewHolderForAdapterPosition(getVisiblePositions().second)
}

// 扩展函数
fun RecyclerView.orientation(): Int {
    return layoutManager?.let { lm ->
        when (lm) {
            is LinearLayoutManager -> lm.orientation
            is StaggeredGridLayoutManager -> lm.orientation
            else -> RecyclerView.VERTICAL
        }
    } ?: RecyclerView.VERTICAL
}