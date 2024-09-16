package com.click.recapai

import android.app.Application
import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.Chat
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import com.google.ai.client.generativeai.type.generationConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.decodeFromString

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class GeminiAPIViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val dataStore: DataStore<Preferences> = application.dataStore

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL_NAME = stringPreferencesKey("model_name")
    }

    private var apiKey: String = ""
    private var modelName: String = "gemini-1.5-pro"
    private lateinit var generativeModel: GenerativeModel
    private var chat: Chat? = null

    init {
        loadSettings()
    }

    fun saveSettings() {
        viewModelScope.launch {
            dataStore.edit { settings ->
                settings[API_KEY] = apiKey
                settings[MODEL_NAME] = modelName
            }
        }
    }

    fun loadSettings() {
        viewModelScope.launch {
            val settings = dataStore.data.map { preferences ->
                apiKey = preferences[API_KEY] ?: ""
                modelName = preferences[MODEL_NAME] ?: "gemini-1.5-pro"
            }.first()
            initializeGenerativeModel()
        }
    }

    fun getAPIKey(): String = apiKey

    fun getModelName(): String = modelName

    fun updateSettings(newApiKey: String, newModelName: String) {
        apiKey = newApiKey
        modelName = newModelName

        initializeGenerativeModel()
    }

    private fun initializeGenerativeModel() {
        if (apiKey.isNotBlank()) {
            generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey,
                generationConfig {
                    responseMimeType = "application/json"
                }
            )
            chat = generativeModel.startChat()
        }
    }

    private var numberOfQuestions: Int = 5
    private var selectedLanguage: String = "English"
    private var safetySettings: Boolean = true

    fun initializeModel(modelName: String, language: String, safety: Boolean, questions: Int) {
        this.numberOfQuestions = if (questions == 0) 5 else questions
        this.selectedLanguage = language
        this.safetySettings = safety
    }

    fun clearChat() {
        chat = generativeModel.startChat()
    }

    fun sendMessage(
        userInput: String,
        imageUris: List<Uri>?,
        context: Context,
        generateQuiz: Boolean,
        completion: (String) -> Unit
    ) {
        _uiState.value = UiState.Loading

        val quizPrompt = if (generateQuiz) {
            """
            Use this JSON schema to generate $numberOfQuestions questions:
            {
                "quiz_title": "Sample Quiz",
                "questions": [
                    {
                        "type": "multiple_choice",
                        "question": "What is the capital of France?",
                        "options": [
                            {"text": "Paris", "correct": true},
                            {"text": "London",  "correct": false},
                            {"text": "Berlin", "correct": false},
                            {"text": "Rome", "correct": false}
                        ]
                    },
                    {
                        "type": "free_answer",
                        "question": "What is the meaning of life?",
                        "answer": ""
                    }
                ]
            }
        """.trimIndent()
        } else {
            "Please follow the example JSON EXACTLY"
        }

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val contentBlock = content {
                    text("$userInput\n$quizPrompt")
                    imageUris?.forEach { uri ->
                        val inputStream = context.contentResolver.openInputStream(uri)
                        val bitmap = BitmapFactory.decodeStream(inputStream)
                        image(bitmap)
                    }
                }

                val response = chat?.sendMessage(contentBlock)

                response?.text?.let { outputContent ->
                    _uiState.value = UiState.Success(outputContent)
                    completion(outputContent)
                }
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.localizedMessage ?: "")
                completion("Error: ${e.localizedMessage}")
            }
        }
    }
}

@Serializable
data class Explanation(
    val question: String,
    val choices: List<Choice>
) {
    @Serializable
    data class Choice(
        val answer_option: String,
        val correct: Boolean,
        val explanation: String
    )
}

@Serializable
data class Quiz(
    val quiz_title: String,
    val questions: List<Question>,
    val userPrompt: String? = null,
    val userLinks: List<String>? = null,
    val userPhotos: List<ByteArray>? = null
)

@Serializable
data class Option(
    val text: String,
    val correct: Boolean
)

@Serializable
data class Question(
    val type: String,
    val question: String,
    val options: List<Option>? = null,
    val answer: String? = null
)

fun parseQuizJson(jsonString: String): Quiz {
    return Json.decodeFromString(jsonString)
}