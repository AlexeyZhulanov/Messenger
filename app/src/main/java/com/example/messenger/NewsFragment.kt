package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.messenger.databinding.FragmentNewsBinding
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

@AndroidEntryPoint
class NewsFragment : Fragment() {

    private lateinit var binding: FragmentNewsBinding
    private lateinit var adapter: NewsAdapter
    private val viewModel: NewsViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewModel.pagingFlow.collectLatest { pagingData ->
                adapter.submitData(pagingData)
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNewsBinding.inflate(inflater, container, false)
        lifecycleScope.launch {
            val permission = viewModel.getPermission()
            if(permission == 1) {
                binding.filesVoicesLayout.visibility = View.VISIBLE
                // todo всё остальное тут настроить
            }
        }
        return binding.root
    }
}