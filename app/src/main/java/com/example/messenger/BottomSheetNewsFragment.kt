package com.example.messenger

import android.content.Context
import android.graphics.PorterDuff
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.util.Log
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.messenger.databinding.FragmentNewsCreateBinding
import com.example.messenger.model.News
import com.example.messenger.picker.ExoPlayerEngine
import com.example.messenger.picker.FilePickerManager
import com.example.messenger.picker.GlideEngine
import com.example.messenger.voicerecorder.AudioConverter
import com.example.messenger.voicerecorder.AudioRecorder
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.luck.picture.lib.basic.PictureSelector
import com.luck.picture.lib.config.InjectResourceSource
import com.luck.picture.lib.entity.LocalMedia
import com.luck.picture.lib.interfaces.OnExternalPreviewEventListener
import com.luck.picture.lib.interfaces.OnInjectLayoutResourceListener
import com.tougee.recorderview.AudioRecordView
import kotlinx.coroutines.async
import kotlinx.coroutines.launch
import java.io.File
import java.io.PrintWriter
import kotlin.coroutines.cancellation.CancellationException

interface BottomSheetNewsListener {
    fun onPostSent()
}

class BottomSheetNewsFragment(
    private val newsViewModel: NewsViewModel,
    private val currentNews: News? = null,
    private val triple: Triple<ArrayList<LocalMedia>, List<File>, List<File>>? = null,
    private val bottomSheetNewsListener: BottomSheetNewsListener
) : BottomSheetDialogFragment(), AudioRecordView.Callback {

    private lateinit var binding: FragmentNewsCreateBinding
    private lateinit var imageAdapter: ImageAdapter
    private lateinit var recAdapterFiles: NewsRecAdapter
    private lateinit var recAdapterVoices: NewsRecAdapter
    private lateinit var filePickerManager: FilePickerManager
    private var audioRecord: AudioRecorder? = null
    private lateinit var pickFileLauncher: ActivityResultLauncher<Array<String>>
    private var filesList: MutableList<File> = mutableListOf()
    private var voicesList: MutableList<File> = mutableListOf()
    private var canDrag: Boolean = true
    private var isEmojiOffDrag: Boolean = false


    private val file: File by lazy {
        val f = File("${requireContext().externalCacheDir?.absolutePath}${File.separator}audio.pcm")
        if (!f.exists()) {
            f.createNewFile()
        }
        f
    }

    private val tmpFile: File by lazy {
        val f = File("${requireContext().externalCacheDir?.absolutePath}${File.separator}tmp.pcm")
        if (!f.exists()) {
            f.createNewFile()
        }
        f
    }

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
            binding.headerEditText.setText(currentNews.headerText)
            binding.textEditText.setText(currentNews.text)
            if(triple?.first != null) {
                if(canDrag) disableBottomSheetDrag()
                imageAdapter.images = triple.first
            }
            if(triple?.second != null) {
                if(canDrag) disableBottomSheetDrag()
                filesList = triple.second.toMutableList()
                triple.second.forEach {
                    recAdapterFiles.addItem(it.name)
                }
            }
            if(triple?.third != null) {
                if(canDrag) disableBottomSheetDrag()
                voicesList = triple.third.toMutableList()
                val retriever = MediaMetadataRetriever()
                triple.third.forEach {
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
                } catch (e: CancellationException) {
                    Toast.makeText(requireContext(), "Выходим...", Toast.LENGTH_SHORT).show()
                } catch (e: Exception) {
                    Toast.makeText(requireContext(), "Неизвестная ошибка", Toast.LENGTH_SHORT).show()
                }
            }
        }
        binding.fileButton.setOnClickListener {
            pickFileLauncher.launch(arrayOf("*/*"))
        }

        binding.emojiButton.setOnClickListener {
            // Если текстовый контейнер пустой, то смайлы будут добавляться в заголовок
            if(binding.emojiPicker.visibility == View.VISIBLE) {
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
                                newsViewModel.getFileFromContentUri(requireContext(), Uri.parse(item1.availablePath))
                            } else {
                                File(item1.availablePath)
                            } ?: continue

                            val (path, f) = async { newsViewModel.uploadNews(file) }.await()
                            list += path
                            if (!f) break
                        }
                        return@async if (list.size == photos.size) list else null
                    }
                    return@async null
                }
                val filesJob = async { newsViewModel.uploadFiles(filesList)?.toMutableList() }
                val voicesJob = async { newsViewModel.uploadFiles(voicesList)?.toMutableList() }

                val photosFinal = photosJob.await()
                val filesFinal = filesJob.await()
                val voicesFinal = voicesJob.await()
                val success = if(currentNews == null)
                    newsViewModel.sendNews(headerTxt, txt, photosFinal, voicesFinal, filesFinal)
                else newsViewModel.editNews(currentNews.id, headerTxt, txt, photosFinal, voicesFinal, filesFinal)
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
        return binding.root
    }

    override fun isReady(): Boolean = true

    override fun onRecordStart() {
        clearFile(tmpFile)

        audioRecord = AudioRecorder(ParcelFileDescriptor.open(tmpFile, ParcelFileDescriptor.MODE_READ_WRITE))
        audioRecord?.start(null, this)
    }

    private fun clearFile(f: File) {
        PrintWriter(f).run {
            print("")
            close()
        }
    }

    override fun onRecordEnd() {
        audioRecord?.stop()
        tmpFile.copyTo(file, true)

        val fileOgg = File("${requireContext().externalCacheDir?.absolutePath}${File.separator}audio.ogg")
        if (fileOgg.exists()) fileOgg.delete()
        val converter = AudioConverter()
        converter.convertPcmToOgg(file.path, fileOgg.path) { success, _ ->
            if (success) {
                val retriever = MediaMetadataRetriever()
                retriever.setDataSource(fileOgg.absolutePath)
                val duration = retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
                retriever.release()
                val str = "Гс ${duration/1000} сек"
                if(canDrag) disableBottomSheetDrag()
                voicesList.add(fileOgg)
                recAdapterVoices.addItem(str)
            }
        }
    }

    override fun onRecordCancel() {
        audioRecord?.stop()
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
}