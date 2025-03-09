package com.example.messenger

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentNewsBinding
import com.example.messenger.model.News
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.io.File

@AndroidEntryPoint
class NewsFragment(
    private val currentUserUri: Uri?,
    private val currentUserId: Int?
) : Fragment() {

    private lateinit var binding: FragmentNewsBinding
    private lateinit var adapter: NewsAdapter
    private lateinit var filePickerManager: FilePickerManager
    private var permission: Int = 0
    private val viewModel: NewsViewModel by viewModels()

    private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            binding.recyclerview.scrollToPosition(0)
            binding.recyclerview.adapter?.unregisterAdapterDataObserver(this)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        lifecycleScope.launch {
            viewModel.pagingFlow.collectLatest { pagingData ->
                try {
                    binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                } catch (e: Exception) {
                    binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
                    binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
                }
                adapter.submitData(pagingData)
            }
        }
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.toolbar_news, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)
        if(currentUserUri != null) {
            val avatarImageView: ImageView = view.findViewById(R.id.toolbar_avatar)
            avatarImageView.imageTintList = null
            Glide.with(requireContext())
                .load(currentUserUri)
                .apply(RequestOptions.circleCropTransform())
                .diskCacheStrategy(DiskCacheStrategy.ALL)
                .into(avatarImageView)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNewsBinding.inflate(inflater, container, false)
        viewModel.setEncryptHelper(currentUserId)
        lifecycleScope.launch {
            permission = viewModel.getPermission()
            if(permission == 1) {
                binding.floatingActionButtonAdd.visibility = View.VISIBLE
                binding.floatingActionButtonAdd.setOnClickListener {
                    BottomSheetNewsFragment(viewModel, null, null, object : BottomSheetNewsListener {
                        override fun onPostSent() {
                            viewModel.refresh()
                        }
                    }).show(childFragmentManager, "NewPostTag")
                }
                adapter.setPermission(permission)
            }
        }
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    backPressed()
                    remove()
                }
            })
        binding.button3.setOnClickListener {
            backPressed()
        }
        filePickerManager = FilePickerManager(fragment4 = this)
        adapter = NewsAdapter(object: NewsActionListener {
            override fun onEditItem(news: News, triple: Triple<ArrayList<LocalMedia>, List<File>, List<File>>) {
                BottomSheetNewsFragment(viewModel, news, triple, object : BottomSheetNewsListener {
                    override fun onPostSent() {
                        viewModel.refresh()
                    }
                }).show(childFragmentManager, "EditPostTag")
            }

            override fun onDeleteItem(newsId: Int) {
               lifecycleScope.launch {
                   val res = viewModel.deleteNews(newsId)
                   if(res) {
                       Toast.makeText(requireContext(), "Новость успешно удалена", Toast.LENGTH_SHORT).show()
                       viewModel.refresh()
                   } else Toast.makeText(requireContext(), "Не удалось удалить новость", Toast.LENGTH_SHORT).show()
               }
            }

            override fun onImagesClick(images: ArrayList<LocalMedia>, position: Int) {
                PictureSelector.create(requireActivity())
                    .openPreview()
                    .setImageEngine(GlideEngine.createGlideEngine())
                    .setVideoPlayerEngine(ExoPlayerEngine())
                    .isAutoVideoPlay(false)
                    .isLoopAutoVideoPlay(false)
                    .isVideoPauseResumePlay(true)
                    .setSelectorUIStyle(filePickerManager.selectorStyle)
                    .isPreviewFullScreenMode(true)
                    .setInjectLayoutResourceListener(object: OnInjectLayoutResourceListener {
                        override fun getLayoutResourceId(context: Context?, resourceSource: Int): Int {
                            @Suppress("DEPRECATED_IDENTITY_EQUALS")
                            return if (resourceSource === InjectResourceSource.PREVIEW_LAYOUT_RESOURCE
                            ) R.layout.ps_custom_fragment_preview
                            else InjectResourceSource.DEFAULT_LAYOUT_RESOURCE
                        }
                    })
                    .startActivityPreview(position, false, images)
            }

            override fun onFileClick(file: File) {
                try {
                    val uri: Uri = FileProvider.getUriForFile(requireContext(), requireContext().applicationContext.packageName + ".provider", file)

                    val intent = Intent(Intent.ACTION_VIEW)
                    intent.setDataAndType(uri, requireContext().contentResolver.getType(uri))
                    intent.flags = Intent.FLAG_GRANT_READ_URI_PERMISSION

                    val chooser = Intent.createChooser(intent, "Выберите приложение для открытия файла")
                    requireContext().startActivity(chooser)
                } catch (e: IllegalArgumentException) {
                    e.printStackTrace()
                }
            }
        }, requireContext(), viewModel, permission)
        binding.recyclerview.adapter = adapter
        binding.recyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.addItemDecoration(SpacingItemDecorator(20))
        return binding.root
    }

    private fun backPressed() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MessengerFragment())
            .commit()
    }

    class SpacingItemDecorator(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.bottom = space
        }
    }
}