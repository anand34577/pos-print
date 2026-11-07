package com.posprint.app.ui.templates

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.posprint.app.data.TemplateRepository
import com.posprint.app.domain.template.Template
import com.posprint.app.domain.template.TemplateKind
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class TemplatesViewModel @Inject constructor(private val repo: TemplateRepository) : ViewModel() {

    val templates: StateFlow<List<Template>?> =
        repo.observeAll().stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    private val _messages = Channel<String>(Channel.BUFFERED)
    val messages = _messages.receiveAsFlow()

    fun create(kind: TemplateKind, onCreated: (Template) -> Unit) {
        viewModelScope.launch { onCreated(repo.createBlank(kind)) }
    }

    fun say(message: String) { _messages.trySend(message) }

    fun duplicate(t: Template) { viewModelScope.launch { repo.duplicate(t.id); _messages.send("Duplicated ${t.name}") } }
    fun delete(t: Template) { viewModelScope.launch { repo.delete(t.id); _messages.send("Deleted ${t.name}") } }
    fun rename(t: Template, name: String) {
        // Re-read so a rename never overwrites newer edits with the list's copy of the template.
        viewModelScope.launch { repo.get(t.id)?.let { repo.save(it.copy(name = name.trim().ifBlank { it.name })) } }
    }
    fun export(t: Template): String = repo.export(t)

    fun import(json: String, onDone: (Template) -> Unit) {
        viewModelScope.launch {
            try {
                val id = repo.import(json)
                repo.get(id)?.let(onDone)
                _messages.send("Template imported")
            } catch (e: Exception) {
                _messages.send("That file is not a valid template.")
            }
        }
    }
}
