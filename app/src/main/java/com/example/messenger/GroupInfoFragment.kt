package com.example.messenger

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.messenger.model.Group

class GroupInfoFragment(
    private val group: Group,
    private val groupName : String
) : BaseInfoFragment() {
    override val viewModel: BaseInfoViewModel
        get() = TODO("Not yet implemented")

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        super.onCreateView(inflater, container, savedInstanceState)
        // todo GroupViewModel.fetchMembersList
        with(binding) {
            nickTextView.visibility = View.GONE
            nickWordTextView.visibility = View.GONE
            copyImageView.visibility = View.GONE
            addMembersTextView.visibility = View.VISIBLE
            addUsersImageView.visibility = View.VISIBLE
            buttonMembers.visibility = View.VISIBLE // todo либо здесь допилить логику через abstract fun,
            // todo либо там через abstract val flag
            addUsersImageView.setOnClickListener {
                // todo Вывести AlertDialog с добавлением пользователя
            }
        }

        return binding.root
    }

    override fun getAvatarString(): String = group.avatar ?: ""

    override fun getUpperName(): String = groupName

    override fun getSessionName(): String {
        TODO("Not yet implemented") // как-то здесь получать список со всеми пользователями и хранить во viewmodel его
    }

    override fun getCanDelete(): Boolean = group.canDelete

    override fun getInterval(): Int = group.autoDeleteInterval
}