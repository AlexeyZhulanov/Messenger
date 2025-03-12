package com.example.messenger.model

class InvertedIndex(private val messages: List<Message>) {
    private val index = mutableMapOf<String, MutableList<Message>>()

    init {
        // Строим индекс
        for (message in messages) {
            val words = message.text!!.split(" ") // С сервера приходят только not null text-сообщения
            for (word in words) {
                index.getOrPut(word.lowercase()) { mutableListOf() }.add(message)
            }
        }
    }

    fun searchMessages(query: String): List<Message> {
        val matchingMessages = mutableSetOf<Message>()
        for ((word, messages) in index) {
            if (word.startsWith(query.lowercase())) {
                matchingMessages.addAll(messages)
            }
        }
        return matchingMessages.toList()
    }
}