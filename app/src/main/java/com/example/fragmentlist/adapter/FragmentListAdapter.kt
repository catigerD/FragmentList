package com.example.fragmentlist.adapter

import android.R.attr.data
import android.util.Log
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import androidx.lifecycle.Lifecycle
import java.util.Collections


abstract class FragmentListAdapter : _FragmentListAdapter {

    companion object {
        private const val TAG = "FragmentListAdapter"
    }

    constructor(fragment: Fragment) : super(fragment)

    constructor(activity: FragmentActivity) : super(activity)

    constructor(fragmentManager: FragmentManager, lifecycle: Lifecycle) : super(fragmentManager, lifecycle)

    abstract val itemConfigList: MutableList<ItemConfig>

    fun onItemMove(fromPosition: Int, toPosition: Int) {
        if (fromPosition == toPosition) {
            return
        }

        if (fromPosition < toPosition) {
            for (i in fromPosition..<toPosition) {
                Collections.swap(itemConfigList, i, i + 1)
            }
        } else {
            for (i in fromPosition downTo toPosition + 1) {
                Collections.swap(itemConfigList, i, i - 1)
            }
        }

        notifyItemMoved(fromPosition, toPosition)
    }

    override fun getItemId(position: Int): Long {
        return itemConfigList[position].uniqueId
    }

    override fun containsItem(itemId: Long): Boolean {
        return itemConfigList.any { it.uniqueId == itemId }
    }

    override fun createFragment(position: Int): Fragment {
        return itemConfigList[position].creator.invoke(position)
    }

    override fun getItemCount(): Int {
        return itemConfigList.size
    }

    data class ItemConfig(
        val uniqueId: Long,
        val creator: (position: Int) -> Fragment
    )
}