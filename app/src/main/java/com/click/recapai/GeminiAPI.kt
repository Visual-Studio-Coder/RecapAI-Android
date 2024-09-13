package com.click.recapai

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")




class GeminiAPIViewModel(application: Application) : AndroidViewModel(application) {
    private val _uiState: MutableStateFlow<UiState> = MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val dataStore: DataStore<Preferences> = application.dataStore

    companion object {
        private val API_KEY = stringPreferencesKey("api_key")
        private val MODEL_NAME = stringPreferencesKey("model_name")
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
        }
    }

    private var apiKey: String = "" // Initially empty

    fun getAPIKey(): String {
        return apiKey
    }

    fun getModelName(): String {
        return modelName
    }

    fun updateSettings(newApiKey: String, newModelName: String) {
        apiKey = newApiKey
        modelName = newModelName
        initializeGenerativeModel() // Re-initialize with the new values
    }

    private lateinit var generativeModel: GenerativeModel // Do not initialize here

    private var modelName: String = "gemini-1.5-pro"

    private fun initializeGenerativeModel() {
        if (apiKey.isNotBlank()) { // Only initialize if apiKey is not empty
            generativeModel = GenerativeModel(
                modelName = modelName,
                apiKey = apiKey
            )
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
        // Implement clear history functionality if required
    }

    fun sendMessage(
        userInput: String,
        bitmap: Bitmap?,
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
                val response = generativeModel.generateContent(
                    content {
                        text(userInput)
                        bitmap?.let { image(it) }
                    }
                )

                response.text?.let { outputContent ->
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