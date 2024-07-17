package com.example.messenger.model

data class Message(
    val id: Long,
    val idUser: Long,
    val idOpponent: Long,
    var name: String = "user",
    var image: String = "default", // переделать
    var lastMessage: String = ""
) {
    override fun toString(): String {
        return "id: $id, idUser: $idUser, idOpponent: $idOpponent, name: $name, image: $image, " +
                "lastMessage: $lastMessage"
    }
}