package com.example.messenger

import android.content.Context
import android.content.Intent
import android.graphics.Rect
import android.media.MediaPlayer
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.OnBackPressedCallback
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.messenger.databinding.FragmentNewsBinding
import com.example.messenger.model.User
import com.example.messenger.utils.getParcelableCompat
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.example.messenger.states.ImagesState
import com.example.messenger.states.NewsUi
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

    // Голосовые сообщения
    private var mediaPlayer: MediaPlayer? = null
    private val handler = Handler(Looper.getMainLooper())
    private var updateRunnable: Runnable? = null
    private var playingPath: String? = null

    private val adapterDataObserver = object : RecyclerView.AdapterDataObserver() {
        override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
            binding.recyclerview.scrollToPosition(0)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentUserUri = arguments?.getParcelableCompat<Uri>(ARG_USER_URI)
        currentUser = arguments?.getParcelableCompat<User>(ARG_USER)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.recyclerview.adapter?.registerAdapterDataObserver(adapterDataObserver)
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    viewModel.pagingFlow.collectLatest { pagingData ->
                        adapter.submitData(pagingData)
                    }
                }
                launch {
                    viewModel.attachmentsState.collect { stateMap ->
                        if(stateMap.isNotEmpty()) {
                            binding.recyclerview.post {
                                adapter.updateAttachmentsState(stateMap)
                            }
                        }
                    }
                }
            }
        }
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
            Glide.with(avatarImageView)
                .load(currentUserUri)
                .circleCrop()
                .dontAnimate()
                .into(avatarImageView)
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNewsBinding.inflate(inflater, container, false)
        viewModel.setEncryptHelper(currentUser?.id)

        WindowCompat.setDecorFitsSystemWindows(requireActivity().window, false)
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())

            binding.statusBarScrim.layoutParams.height = systemBars.top
            binding.navigationBarScrim.layoutParams.height = systemBars.bottom

            binding.statusBarScrim.requestLayout()
            binding.navigationBarScrim.requestLayout()

            insets
        }

        val typedValue = TypedValue()
        requireActivity().theme.resolveAttribute(R.attr.colorBar, typedValue, true)
        val colorBar = typedValue.data
        binding.statusBarScrim.setBackgroundColor(colorBar)
        binding.navigationBarScrim.setBackgroundColor(ContextCompat.getColor(requireContext(), R.color.navigation_bar_color))

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
            override fun onEditItem(ui: NewsUi) {
                BottomSheetNewsFragment.newInstance(ui, currentUser?.id ?: -1, object : BottomSheetNewsListener {
                    override fun onPostSent() {
                        viewModel.refresh()
                    }
                }).show(childFragmentManager, "EditPostTag")
            }

            override fun onDeleteItem(newsId: Int) {
                viewLifecycleOwner.lifecycleScope.launch {
                   val res = viewModel.deleteNews(newsId)
                   if(res) {
                       Toast.makeText(requireContext(), "Новость успешно удалена", Toast.LENGTH_SHORT).show()
                       viewModel.refresh()
                   } else Toast.makeText(requireContext(), "Не удалось удалить новость", Toast.LENGTH_SHORT).show()
               }
            }

            override fun onImagesClick(ui: NewsUi, position: Int) {
                val images = getLocalMedias(ui)
                if(images.isEmpty()) return
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

            override fun onFileClick(filePath: String) {
                try {
                    val file = File(filePath)
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

            override fun onPlayVoiceClick(filePath: String) {
                when {
                    playingPath == filePath -> pauseAudio()
                    playingPath != null -> {
                        // играл другой — стопаем его
                        stopAudio()
                        startAudio(filePath)
                    }
                    else -> startAudio(filePath)
                }
            }

            override fun onVoiceSeek(filePath: String, progress: Int) {
                if (playingPath == filePath) {
                    val player = mediaPlayer ?: return
                    player.seekTo(progress)
                }
            }
        }, requireContext(), permission, viewModel.audioPlaybackState, viewLifecycleOwner.lifecycleScope)
        binding.recyclerview.adapter = adapter
        binding.recyclerview.setItemViewCacheSize(20)
        binding.recyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.addItemDecoration(SpacingItemDecorator(20))
        viewLifecycleOwner.lifecycleScope.launch {
            permission = viewModel.getPermission()
            if(permission == 1) {
                binding.floatingActionButtonAdd.visibility = View.VISIBLE
                binding.floatingActionButtonAdd.setOnClickListener {
                    BottomSheetNewsFragment.newInstance(null, currentUser?.id ?: -1, object : BottomSheetNewsListener {
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

    fun getLocalMedias(ui: NewsUi): ArrayList<LocalMedia> {
        val state = ui.imagesState as? ImagesState.Ready ?:
        viewModel.attachmentsState.value[ui.news.id]?.imagesState as? ImagesState.Ready ?: return arrayListOf()
        val list = state.imageItems.map { item ->
            LocalMedia().apply {
                path = item.localPath
                mimeType = item.mimeType
                duration = item.duration
                isCompressed = false
                isCut = false
                isOriginal = false
            }
        }
        return ArrayList(list)
    }

    private fun startAudio(path: String) {
        mediaPlayer = MediaPlayer().apply {
            setDataSource(path)
            prepare()
            start()
            setOnCompletionListener {
                stopAudio()
            }
        }
        playingPath = path
        viewModel.updateAudioState(path, isPlaying = true, progress = 0)
        startSeekUpdates()
    }

    private fun pauseAudio() {
        val player = mediaPlayer ?: return
        val path = playingPath ?: return
        if (player.isPlaying) {
            player.pause()
            stopSeekUpdates()
            viewModel.updateAudioState(path, isPlaying = false, progress = player.currentPosition)
        } else { // Если то же аудио мы стопаем и снова запускаем
            player.start()
            startSeekUpdates()
            viewModel.updateAudioState(path, isPlaying = true, progress = player.currentPosition)
        }
    }

    private fun stopAudio() {
        stopSeekUpdates()
        mediaPlayer?.release()
        mediaPlayer = null
        viewModel.updateAudioState(null, isPlaying = false, progress = 0)
        playingPath = null
    }

    private fun startSeekUpdates() {
        updateRunnable = object : Runnable {
            override fun run() {
                val player = mediaPlayer ?: return
                if (player.isPlaying) {
                    viewModel.updateAudioProgress(player.currentPosition)
                    handler.postDelayed(this, 100)
                }
            }
        }
        updateRunnable?.let { handler.post(it) }
    }

    private fun stopSeekUpdates() {
        updateRunnable?.let {
            handler.removeCallbacks(it)
        }
        updateRunnable = null
    }

    class SpacingItemDecorator(private val space: Int) : RecyclerView.ItemDecoration() {
        override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView, state: RecyclerView.State) {
            outRect.bottom = space
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        binding.recyclerview.adapter?.unregisterAdapterDataObserver(adapterDataObserver)
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