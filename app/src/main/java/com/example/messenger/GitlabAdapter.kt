package com.example.messenger

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.messenger.databinding.ItemRepoBinding
import com.example.messenger.model.Repo

interface GitlabActionListener {
    fun onOptionsClicked(repo: Repo)
    fun onRepoNameClicked(url: String)
}

class GitlabAdapter(
    private val actionListener: GitlabActionListener
) : RecyclerView.Adapter<GitlabAdapter.GitlabViewHolder>() {

    var repos: List<Repo> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GitlabViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        val binding = ItemRepoBinding.inflate(inflater, parent, false)
        return GitlabViewHolder(binding)
    }

    override fun onBindViewHolder(holder: GitlabViewHolder, position: Int) {
        val repo = repos[position]

        val idText = "#" + repo.projectId.toString()
        with(holder.binding) {
            projectIdTextView.text = idText
            repoNameTextView.text = repo.name
            lastChangeTextView.text = repo.lastActivity
            setDrawableEnd(hookPushTextView, repo.isHookPush)
            setDrawableEnd(hookMergeTextView, repo.isHookMerge)
            setDrawableEnd(hookTagTextView, repo.isHookTag)
            setDrawableEnd(hookIssueTextView, repo.isHookIssue)
            setDrawableEnd(hookNoteTextView, repo.isHookNote)
            setDrawableEnd(hookReleaseTextView, repo.isHookRelease)

            icOptions.setOnClickListener {
                actionListener.onOptionsClicked(repo)
            }
            repoNameTextView.setOnClickListener {
                actionListener.onRepoNameClicked(repo.url)
            }
        }
    }

    private fun setDrawableEnd(tv: TextView, isCheck: Boolean) {
        tv.setCompoundDrawablesWithIntrinsicBounds(
            0, 0, if(isCheck) R.drawable.ic_check else R.drawable.ic_clear, 0
        )
    }

    override fun getItemCount() = repos.size

    class GitlabViewHolder(
        val binding: ItemRepoBinding
    ) : RecyclerView.ViewHolder(binding.root)
}
