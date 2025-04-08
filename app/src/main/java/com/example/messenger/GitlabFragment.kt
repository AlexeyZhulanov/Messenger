package com.example.messenger

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.request.RequestOptions
import com.example.messenger.databinding.FragmentGitlabBinding
import com.example.messenger.model.Repo
import com.example.messenger.model.User
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class GitlabFragment(
    private val currentUserUri: Uri?,
    private val currentUser: User?
) : Fragment(), OnSaveButtonGitlabClickListener {

    private lateinit var binding: FragmentGitlabBinding
    private lateinit var adapter: GitlabAdapter
    private val viewModel: GitlabViewModel by viewModels()

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        requireActivity().window.statusBarColor = ContextCompat.getColor(requireContext(), R.color.colorBar)
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

        viewModel.repos.observe(viewLifecycleOwner) { repos ->
            Log.d("testReposFetch", repos.toString()) // todo потом убрать
            adapter.repos = repos
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        binding = FragmentGitlabBinding.inflate(inflater, container, false)
        binding.tokenTextView.text = viewModel.getGitlabToken()

        binding.button.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, NewsFragment(currentUserUri, currentUser), "NEWS_FRAGMENT_TAG2")
                .addToBackStack(null)
                .commit()
        }

        binding.button3.setOnClickListener {
            parentFragmentManager.beginTransaction()
                .replace(R.id.fragmentContainer, MessengerFragment(), "MESSENGER_FRAGMENT_TAG8")
                .commit()
        }

        binding.openGitlab.setOnClickListener {
            openUrlInBrowser("https://gitlab.amessenger.ru")
        }

        binding.editTokenButton.setOnClickListener {
            showChangeTokenDialog()
        }

        if(viewModel.isNeedFetchRepos) viewModel.fetchRepos() // При повторном заходе во фрагмент
        setupRecyclerView()
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        viewModel.isNeedFetchRepos = true
    }

    private fun setupRecyclerView() {
        adapter = GitlabAdapter(object : GitlabActionListener {
            override fun onOptionsClicked(repo: Repo) {
                val dialog = SaveGitlabSettingsDialogFragment()
                dialog.setSaveButtonClickListener(this@GitlabFragment)
                dialog.setRepo(repo)
                dialog.show(parentFragmentManager, "SaveGitlabSettingsDialogFragment")
            }

            override fun onRepoNameClicked(url: String) {
                openUrlInBrowser(url)
            }
        })
        binding.recyclerview.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerview.adapter = adapter
    }

    override fun onSaveButtonClicked(id: Int, list: List<Boolean?>) {
        lifecycleScope.launch {
            viewModel.updateRepo(id, list) { success ->
                if(success) {
                    viewModel.isNeedFetchRepos = true
                    viewModel.fetchRepos()
                } else Toast.makeText(requireContext(), "Ошибка: не удалось изменить настройки", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun goToSettingsFragment() {
        parentFragmentManager.beginTransaction()
            .replace(R.id.fragmentContainer, SettingsFragment(currentUser ?: User(0, "", "")), "SETTINGS_FRAGMENT_TAG2")
            .addToBackStack(null)
            .commit()
    }

    private fun openUrlInBrowser(url: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                // Добавляем флаг для нового task'а (опционально)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }

            // Проверяем доступность браузеров
            val activities = requireContext().packageManager.queryIntentActivities(intent, 0)

            if (activities.isNotEmpty()) {
                startActivity(intent)
            } else {
                // Fallback: открываем в WebView или показываем ошибку
                Toast.makeText(requireContext(), "Браузер не найден", Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Toast.makeText(requireContext(), "Ошибка: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showChangeTokenDialog() {
        // Inflate the custom layout for the dialog
        val dialogView = layoutInflater.inflate(R.layout.dialog_change_token, null)

        // Create the AlertDialog
        val dialog = AlertDialog.Builder(requireContext())
            .setView(dialogView)
            .setPositiveButton("Изменить") { dialogInterface, _ ->
                val input = dialogView.findViewById<EditText>(R.id.dialog_input).text.toString()
                viewModel.changeGitlabToken(input)
                Toast.makeText(requireContext(), "Токен успешно обновлен", Toast.LENGTH_SHORT).show()
                binding.tokenTextView.text = input
                dialogInterface.dismiss()
            }
            .setNegativeButton("Назад") { dialogInterface, _ ->
                dialogInterface.dismiss()
            }
            .create()

        dialog.show()
    }
}