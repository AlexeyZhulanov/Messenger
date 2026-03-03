package com.example.messenger

import android.content.Context
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.messenger.databinding.FragmentNewsCreateBinding
import com.example.messenger.model.News
import com.example.messenger.model.ParcelableFile
import com.example.messenger.utils.getParcelableArrayListCompat
import com.example.messenger.utils.getParcelableCompat
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.example.messenger.recorderview.AudioRecordView
import com.example.messenger.voicerecorder.VoiceRecorder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnExternalPreviewEventListener
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import kotlin.coroutines.cancellation.CancellationException
import androidx.core.view.isVisible
import androidx.core.net.toUri

interface BottomSheetNewsListener {
    fun onPostSent()
}

@AndroidEntryPoint
class BottomSheetNewsFragment : BottomSheetDialogFragment(), AudioRecordView.Callback {

    private val newsViewModel: NewsViewModel by viewModels()
    private var currentNews: News? = null
    private var userId: Int = -1
    private var triple: Triple<ArrayList<LocalMedia>, List<File>, List<File>>? = null
    private lateinit var bottomSheetNewsListener: BottomSheetNewsListener

    private lateinit var binding: FragmentNewsCreateBinding
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var recAdapterFiles: NewsRecAdapter
    private lateinit var recAdapterVoices: NewsRecAdapter
    private lateinit var filePickerManager: FilePickerManager
    private var recorder: VoiceRecorder? = null
    private var outputFile: File? = null
    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>
    private var filesList: MutableList<File> = mutableListOf()
    private var voicesList: MutableList<File> = mutableListOf()
    private var canDrag: Boolean = true
    private var isEmojiOffDrag: Boolean = false

    override fun onStart() {
        super.onStart()

        dialog?.let { dialog ->
            val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.let {
                val behavior = BottomSheetBehavior.from(it)
                it.post {
                    behavior.peekHeight = it.height
                    behavior.state = BottomSheetBehavior.STATE_EXPANDED
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        currentNews = arguments?.getParcelableCompat(ARG_NEWS)
        userId = arguments?.getInt(ARG_USER_ID) ?: -1
        val first = arguments?.getParcelableArrayListCompat<LocalMedia>(ARG_TRIPLE_FIRST)
        val second = arguments?.getParcelableArrayListCompat<ParcelableFile>(ARG_TRIPLE_SECOND)?.map { it.toFile() }
        val third = arguments?.getParcelableArrayListCompat<ParcelableFile>(ARG_TRIPLE_THIRD)?.map { it.toFile() }
        triple = if (first != null && second != null && third != null) {
            Triple(ArrayList(first), second, third)
        } else null

        pickFileLauncher = registerForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri>? ->
            if (uris != null) {
                if (uris.size > 5) {
                    Toast.makeText(requireContext(), "Можно выбрать не более 5 файлов", Toast.LENGTH_SHORT).show()
                } else {
                    uris.forEach { handleFileUri(it) }
                }
            }
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentNewsCreateBinding.inflate(inflater, container, false)
        newsViewModel.setEncryptHelper(userId)
        filePickerManager = FilePickerManager(fragment5 = this)
        binding.cancelButton.setOnClickListener {
            dismiss()
        }
        binding.recordView.apply {
            activity = requireActivity()
            callback = this@BottomSheetNewsFragment
        }
        imageAdapter = ImageAdapter(requireContext(), object: ImageActionListener {
            override fun onImageClicked(image: LocalMedia, position: Int) {
                PictureSelector.create(requireActivity())
                    .openPreview()
                    .setImageEngine(GlideEngine.createGlideEngine())
                    .setVideoPlayerEngine(ExoPlayerEngine())
                    .isAutoVideoPlay(false)
                    .isLoopAutoVideoPlay(false)
                    .isVideoPauseResumePlay(true)
                    .setSelectorUIStyle(filePickerManager.selectorStyle)
                    .isPreviewFullScreenMode(true)
                    .setExternalPreviewEventListener(MyExternalPreviewEventListener(imageAdapter))
                    .setInjectLayoutResourceListener(object: OnInjectLayoutResourceListener {
                        override fun getLayoutResourceId(context: Context?, resourceSource: Int): Int {
                            @Suppress("DEPRECATED_IDENTITY_EQUALS")
                            return if (resourceSource === InjectResourceSource.PREVIEW_LAYOUT_RESOURCE
                            ) R.layout.ps_custom_fragment_preview
                            else InjectResourceSource.DEFAULT_LAYOUT_RESOURCE
                        }
                    })
                    .startActivityPreview(position, true, imageAdapter.getData())
            }

            override fun onDeleteImage(position: Int) {
                imageAdapter.delete(position)
                if(imageAdapter.getData().isEmpty()) {
                    if(filesList.isEmpty() && voicesList.isEmpty()) enableBottomSheetDrag()
                }
            }
        })
        binding.imageRecyclerView.adapter = imageAdapter
        binding.imageRecyclerView.layoutManager = LinearLayoutManager(requireContext(), LinearLayoutManager.HORIZONTAL, false)
        recAdapterFiles = NewsRecAdapter(object : NewsRecActionListener {
            override fun onItemDeleteClicked(actualPos: Int) {
                filesList.removeAt(actualPos)
                if(filesList.isEmpty()) {
                    if(imageAdapter.getData().isEmpty() && voicesList.isEmpty()) enableBottomSheetDrag()
                }
            }
        })
        binding.fileRecyclerView.adapter = recAdapterFiles
        binding.fileRecyclerView.layoutManager = LinearLayoutManager(requireContext())
        recAdapterVoices = NewsRecAdapter(object : NewsRecActionListener {
            override fun onItemDeleteClicked(actualPos: Int) {
                voicesList.removeAt(actualPos)
                if(voicesList.isEmpty()) {
                    if(imageAdapter.getData().isEmpty() && filesList.isEmpty()) enableBottomSheetDrag()
                }
            }
        })
        binding.voiceRecyclerView.adapter = recAdapterVoices
        binding.voiceRecyclerView.layoutManager = LinearLayoutManager(requireContext())

        if(currentNews != null) {
            binding.header.text = "Редактирование поста"
            binding.headerEditText.setText(currentNews?.headerText)
            binding.textEditText.setText(currentNews?.text)
            if(triple?.first != null) {
                if(canDrag) disableBottomSheetDrag()
                imageAdapter.images = triple?.first ?: arrayListOf()
            }
            if(triple?.second != null) {
                if(canDrag) disableBottomSheetDrag()
                filesList = triple?.second?.toMutableList() ?: mutableListOf()
                triple?.second?.forEach {
                    recAdapterFiles.addItem(it.name)
                }
            }
            if(triple?.third != null) {
                if(canDrag) disableBottomSheetDrag()
                voicesList = triple?.third?.toMutableList() ?: mutableListOf()
                val retriever = MediaMetadataRetriever()
                triple?.third?.forEach {
                    retriever.setDataSource(it.absolutePath)
                    val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                    val str = "Гс ${duration/1000} сек"
                    recAdapterVoices.addItem(str)
                }
                retriever.release()
            }
        }

        binding.attachButton.setOnClickListener {
            lifecycleScope.launch {
                try {
                    val res = async { filePickerManager.openFilePicker(isCircle = false, isFreeStyleCrop = false, imageAdapter.getData()) }
                    imageAdapter.images = res.await()
                    if(res.await().isNotEmpty()) {
                        if(canDrag) disableBottomSheetDrag()
                    }
                    Log.d("testImagesAdapterNewsCreate", imageAdapter.getData().toString())
                } catch (_: CancellationException) {
                    Toast.makeText(requireContext(), "Выходим...", Toast.LENGTH_SHORT).show()
                } catch (_: Exception) {
                    Toast.makeText(requireContext(), "Неизвестная ошибка", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.fileButton.setOnClickListener {
            pickFileLauncher.launch(arrayOf("*/*"))
        }

        binding.emojiButton.setOnClickListener {
            // Если текстовый контейнер пустой, то смайлы будут добавляться в заголовок
            if(binding.emojiPicker.isVisible) {
                binding.emojiPicker.visibility = View.GONE
                if(isEmojiOffDrag) {
                    enableBottomSheetDrag()
                    isEmojiOffDrag = false
                }
            }
            else {
                if(canDrag) {
                    disableBottomSheetDrag()
                    isEmojiOffDrag = true
                }
                var enterText: EditText = requireView().findViewById(R.id.textEditText)
                if(enterText.text.isEmpty()) enterText = requireView().findViewById(R.id.headerEditText)
                binding.emojiPicker.visibility = View.VISIBLE
                binding.emojiPicker.setOnEmojiPickedListener { emojicon ->
                    val emoji = emojicon.emoji
                    val start = enterText.selectionStart
                    val end = enterText.selectionEnd
                    enterText.text.replace(start.coerceAtLeast(0), end.coerceAtLeast(0), emoji)
                }
            }
        }
        binding.confirmButton.setOnClickListener {
            binding.confirmButton.isEnabled = false // Отключаем кнопку
            val typedValue = TypedValue()
            val theme = requireContext().theme
            theme.resolveAttribute(androidx.appcompat.R.attr.colorAccent, typedValue, true)
            val color = typedValue.data
            binding.confirmButton.setColorFilter(color, PorterDuff.Mode.SRC_IN)

            val headerTxt = binding.headerEditText.text.toString()
            val txt = binding.textEditText.text.toString()
            val photos = imageAdapter.getData()
            lifecycleScope.launch {
                val photosJob = async {
                    if (photos.isNotEmpty()) {
                        val list = mutableListOf<String>()
                        for (item1 in photos) {
                            val file = if (item1.duration > 0) {
                                newsViewModel.getFileFromContentUri(requireContext(),
                                    item1.availablePath.toUri())
                            } else {
                                File(item1.availablePath)
                            } ?: continue

                            val (path, f) = async { newsViewModel.uploadNews(file, requireContext()) }.await()
                            list += path
                            if (!f) break
                        }
                        return@async if (list.size == photos.size) list else null
                    }
                    return@async null
                }
                val filesJob = async { newsViewModel.uploadFiles(filesList, requireContext())?.toMutableList() }
                val voicesJob = async { newsViewModel.uploadFiles(voicesList, requireContext())?.toMutableList() }

                val photosFinal = photosJob.await()
                val filesFinal = filesJob.await()
                val voicesFinal = voicesJob.await()
                Log.d("testNEWSSEND", headerTxt + txt)
                val success = if(currentNews == null)
                    newsViewModel.sendNews(headerTxt, txt, photosFinal, voicesFinal, filesFinal)
                else newsViewModel.editNews(currentNews?.id ?: -1, headerTxt, txt, photosFinal, voicesFinal, filesFinal)
                if(success) {
                    bottomSheetNewsListener.onPostSent()
                    dismiss()
                } else {
                    Toast.makeText(requireContext(), "Не удалось отправить новость", Toast.LENGTH_SHORT).show()
                    binding.confirmButton.isEnabled = true
                    binding.confirmButton.clearColorFilter()
                }
            }
        }
        recorder = VoiceRecorder(requireContext())
        return binding.root
    }

    private fun createVoiceFile(): File {
        val dir = requireContext().externalCacheDir ?: requireContext().cacheDir
        return File(dir, "voice_${System.currentTimeMillis()}.m4a")
    }

    override fun onRecordStart() {
        outputFile = createVoiceFile()
        outputFile?.let { file ->
            recorder?.start(
                file = file,
                scope = lifecycleScope,
                onError = { Log.e("testVoiceRecorder", it) }
            )
        }
    }

    override fun isReady(): Boolean = true

    override fun onRecordCancel() {
        lifecycleScope.launch {
            recorder?.stop()
            outputFile?.delete()
        }
    }

    override fun onRecordEnd() {
        lifecycleScope.launch {
            val result = recorder?.stop() ?: return@launch
            val file = result.file
            if (result.durationMs < 300) { // Ошибочное аудио (миссклик)
                file.delete()
                return@launch
            }

            val retriever = MediaMetadataRetriever()
            retriever.setDataSource(file.absolutePath)
            val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
            retriever.release()
            val str = "Гс ${duration/1000} сек"
            if(canDrag) disableBottomSheetDrag()
            voicesList.add(file)
            recAdapterVoices.addItem(str)
        }
    }

    private fun handleFileUri(uri: Uri) {
        val file = newsViewModel.uriToFile(uri, requireContext())
        if (file != null) {
            if(canDrag) disableBottomSheetDrag()
            filesList.add(file)
            recAdapterFiles.addItem(file.name)
        }
    }

    private class MyExternalPreviewEventListener(private val imageAdapter: ImageAdapter) :
        OnExternalPreviewEventListener {
        override fun onPreviewDelete(position: Int) {
            imageAdapter.remove(position)
            imageAdapter.notifyItemRemoved(position)
        }

        override fun onLongPressDownload(context: Context, media: LocalMedia): Boolean {
            return false
        }
    }

    private fun disableBottomSheetDrag() {
        canDrag = false
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.isDraggable = false // Отключаем скроллинг
        }
    }

    private fun enableBottomSheetDrag() {
        canDrag = true
        val bottomSheet = dialog?.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        bottomSheet?.let {
            val behavior = BottomSheetBehavior.from(it)
            behavior.isDraggable = true // Включаем скроллинг
        }
    }

    companion object {
        private const val ARG_NEWS = "arg_news"
        private const val ARG_USER_ID = "arg_user_id"
        private const val ARG_TRIPLE_FIRST = "arg_triple_first"
        private const val ARG_TRIPLE_SECOND = "arg_triple_second"
        private const val ARG_TRIPLE_THIRD = "arg_triple_third"

        fun newInstance(
            news: News?,
            currentUserId: Int,
            triple: Triple<ArrayList<LocalMedia>, List<ParcelableFile>, List<ParcelableFile>>?,
            listener: BottomSheetNewsListener
        ) = BottomSheetNewsFragment().apply {
            arguments = Bundle().apply {
                putParcelable(ARG_NEWS, news)
                putInt(ARG_USER_ID, currentUserId)
                putParcelableArrayList(ARG_TRIPLE_FIRST, triple?.first)
                putParcelableArrayList(ARG_TRIPLE_SECOND, triple?.second?.let { ArrayList(it) })
                putParcelableArrayList(ARG_TRIPLE_THIRD, triple?.third?.let { ArrayList(it) })
            }
            this.bottomSheetNewsListener = listener // Передаем listener отдельно
        }
    }
}