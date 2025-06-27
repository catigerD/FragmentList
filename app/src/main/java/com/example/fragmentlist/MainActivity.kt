package com.example.fragmentlist

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.RecyclerView.ViewHolder
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.fragmentlist.adapter.FragmentListItemDragCallback
import com.example.fragmentlist.adapter.FragmentListAdapter
import com.example.fragmentlist.databinding.ActMainBinding
import com.example.fragmentlist.databinding.FragMainBinding

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActMainBinding
    private val adapter by lazy {
        MainFragmentAdapter(this)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRV()
    }

    private fun initRV() {
        fun execute(it: RecyclerView) {
//            it.layoutManager = LinearLayoutManager(this, RecyclerView.VERTICAL, false)
//            it.layoutManager = LinearLayoutManager(this, RecyclerView.HORIZONTAL, false)
            it.layoutManager = GridLayoutManager(this, 3, RecyclerView.VERTICAL, false)
//            it.layoutManager = GridLayoutManager(this, 3, RecyclerView.HORIZONTAL, false)
//            it.layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
//            it.layoutManager = StaggeredGridLayoutManager(3, RecyclerView.HORIZONTAL)
            it.adapter = adapter
            it.setItemViewCacheSize(10)
            ItemTouchHelper(object : FragmentListItemDragCallback(adapter) {
                override fun onSelectedChanged(viewHolder: ViewHolder?, actionState: Int) {
                    super.onSelectedChanged(viewHolder, actionState)
                    // 拖拽开始时改变Item外观
                    if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) {
                        viewHolder?.itemView?.alpha = 0.3f
                    }
                }

                override fun clearView(recyclerView: RecyclerView, viewHolder: ViewHolder) {
                    super.clearView(recyclerView, viewHolder)
                    // 拖拽结束时恢复Item外观
                    viewHolder.itemView.alpha = 1.0f
                }
            }).attachToRecyclerView(it)
        }

        execute(binding.flVh.recyclerView)
//        execute(binding.rvList)
    }
}

class MainFragmentAdapter(
    activity: FragmentActivity
) : FragmentListAdapter(activity) {

    companion object {
        private const val TAG = "MainFragmentAdapter"
    }

    override val itemConfigList: MutableList<ItemConfig> = (0..10).map {
        ItemConfig(
            uniqueId = it.toLong(),
            creator = MainFragment::newInstance
        )
    }.toMutableList()

    override fun createFragment(position: Int): Fragment {
        Log.d(TAG, "createFragment: position : ${position}")
        return super.createFragment(position)
    }
}

class MainFragment : Fragment() {

    companion object {

        private const val KEY_POSITION = "position"
        private const val TAG = "MainFragment"

        private val COLORS = arrayOf(Color.Red, Color.Blue, Color.Cyan, Color.Gray, Color.Black, Color.Green, Color.Magenta)

        fun newInstance(
            position: Int
        ): Fragment {
            return MainFragment().apply {
                arguments = Bundle().also {
                    it.putInt(KEY_POSITION, position)
                }
            }
        }
    }

    private lateinit var binding: FragMainBinding
    private var position: Int = 0

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        position = requireArguments().getInt(KEY_POSITION)
        log("onCreate")
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        log("onCreateView")
        binding = FragMainBinding.inflate(layoutInflater, container, false)
        binding.root.setBackgroundColor(COLORS[position % COLORS.size].toArgb())
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.tvText.text = position.toString()
    }

    override fun onStart() {
        super.onStart()
        log("onStart")
    }

    override fun onResume() {
        super.onResume()
        log("onResume")
    }

    override fun onPause() {
        super.onPause()
        log("onPause")
    }

    override fun onStop() {
        super.onStop()
        log("onStop")
    }

    override fun onDestroyView() {
        super.onDestroyView()
        log("onDestroyView")
    }

    override fun onDestroy() {
        super.onDestroy()
        log("onDestroy")
    }

    private fun log(method: String) {
        Log.d(TAG, "${method}: position : ${position} ; this : ${this}")
    }
}