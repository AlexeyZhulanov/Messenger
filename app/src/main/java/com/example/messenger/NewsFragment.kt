package com.example.messenger

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.net.Uri
import android.os.Bundle
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
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
import com.example.messenger.model.ParcelableFile
import com.example.messenger.model.User
import com.example.messenger.model.getParcelableCompat
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
class NewsFragment : Fragment() {

    private var currentUserUri: Uri? = null
    private var currentUser: User? = null

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

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUserUri = arguments?.getParcelableCompat<Uri>(ARG_USER_URI)
        currentUser = arguments?.getParcelableCompat<User>(ARG_USER)
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
        val typedValue = TypedValue()
        requireActivity().theme.resolveAttribute(R.attr.colorBar, typedValue, true)
        val colorBar = typedValue.data
        requireActivity().window.statusBarColor = colorBar
        val toolbarContainer: FrameLayout = view.findViewById(R.id.toolbar_container)
        val defaultToolbar = LayoutInflater.from(context)
            .inflate(R.layout.toolbar_news, toolbarContainer, false)
        toolbarContainer.addView(defaultToolbar)
        val avatarImageView: ImageView = view.findViewById(R.id.toolbar_avatar)
        avatarImageView.setOnClickListener {
            goToSettingsFragment()
        }
        val titleTextView: TextView = view.findViewById(R.id.toolbar_title)
        titleTextView.setOnClickListener {
            goToSettingsFragment()
        }
        if(currentUserUri != null) {
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
        viewModel.setEncryptHelper(currentUser?.id)
        requireActivity().onBackPressedDispatcher.addCallback(
            viewLifecycleOwner,
            object : OnBackPressedCallback(true) {
                override fun handleOnBackPressed() {
                    backPressed()
                    remove()
                }
            })
        binding.button3.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG9")
                .commit()
        }
        binding.button4.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, GitlabFragment.newInstance(currentUserUri, currentUser), "GITLAB_FRAGMENT_TAG")
                .addToBackStack(null)
                .commit()
        }
        filePickerManager = FilePickerManager(fragment4 = this)
        adapter = NewsAdapter(object: NewsActionListener {
            override fun onEditItem(news: News, triple: Triple<ArrayList<LocalMedia>, List<File>, List<File>>) {
                val triple2 = triple.second.map { ParcelableFile(it.absolutePath) }
                val triple3 = triple.third.map { ParcelableFile(it.absolutePath) }
                val tripleForBundle = Triple(triple.first, triple2, triple3)
                BottomSheetNewsFragment.newInstance(news, currentUser?.id ?: -1, tripleForBundle, object : BottomSheetNewsListener {
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
        lifecycleScope.launch {
            permission = viewModel.getPermission()
            if(permission == 1) {
                binding.floatingActionButtonAdd.visibility = View.VISIBLE
                binding.floatingActionButtonAdd.setOnClickListener {
                    BottomSheetNewsFragment.newInstance(null, currentUser?.id ?: -1, null, object : BottomSheetNewsListener {
                        override fun onPostSent() {
                            viewModel.refresh()
                        }
                    }).show(childFragmentManager, "NewPostTag")
                }
                adapter.setPermission(permission)
            }
        }
        return binding.root
    }

    private fun backPressed() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, MessengerFragment())
            .commit()
    }

    private fun goToSettingsFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment.newInstance(currentUser ?: User(0, "", "")), "SETTINGS_FRAGMENT_TAG2")
            .addToBackStack(null)
            .commit()
    }

    class SpacingItemDecorator(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.bottom = space
        }
    }

    companion object {
        private const val ARG_USER_URI = "currentUserUri"
        private const val ARG_USER = "currentUser"

        fun newInstance(currentUserUri: Uri? = null, currentUser: User? = null) = NewsFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_USER_URI, currentUserUri)
                putParcelable(ARG_USER, currentUser)
            }
        }
    }
}