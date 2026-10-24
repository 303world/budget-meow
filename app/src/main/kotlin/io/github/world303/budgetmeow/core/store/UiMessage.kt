package io.github.world303.budgetmeow.core.store

enum class MessageKind { Info, Success, Warning, Error }

data class UiMessage(val text: String, val kind: MessageKind = MessageKind.Info)
