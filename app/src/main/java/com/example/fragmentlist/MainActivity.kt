package com.example.fragmentlist

import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Color.Companion
import androidx.compose.ui.graphics.toArgb
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.fragmentlist.adapter.FragmentStateAdapter
import com.example.fragmentlist.databinding.ActMainBinding
import com.example.fragmentlist.databinding.FragMainBinding

class MainActivity : FragmentActivity() {

    private lateinit var binding: ActMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        initRV()
    }

    private fun initRV() {
        fun execute(it: RecyclerView) {
            it.layoutManager = LinearLayoutManager(this)
            it.adapter = MainFragmentAdapter(this)
            it.setItemViewCacheSize(10)
        }

        execute(binding.flVh.recyclerView)
//        execute(binding.rvList)
    }
}

class MainFragmentAdapter(
    activity: FragmentActivity
) : FragmentStateAdapter(activity) {

    companion object {
        private const val TAG = "MainFragmentAdapter"
    }

    override fun getItemCount(): Int {
        return 10
    }

    override fun createFragment(position: Int): Fragment {
        Log.d(TAG, "createFragment: position : ${position}")
        return MainFragment.newInstance(position)
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