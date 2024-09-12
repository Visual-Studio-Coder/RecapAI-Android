package com.click.recapai

import android.graphics.Bitmap
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.google.ai.client.generativeai.GenerativeModel
import com.google.ai.client.generativeai.type.content
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

// UI State class to represent loading, success, and error states
sealed class UiState1 {
    object Initial : UiState1()
    object Loading : UiState1()
    data class Success(val output: String) : UiState1()
    data class Error(val message: String) : UiState1()
}

class GeminiAPIViewModel : ViewModel() {
    private val _uiState: MutableStateFlow<UiState> =
        MutableStateFlow(UiState.Initial)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    private val generativeModel = GenerativeModel(
        modelName = "your-model-name",  // Specify your model name here
        apiKey = BuildConfig.apiKey     // Use your API key from your configuration
    )

    private var numberOfQuestions: Int = 5
    private var selectedLanguage: String = "English"
    private var safetySettings: Boolean = true

    // Initialize the model with provided values
    fun initializeModel(modelName: String, language: String, safety: Boolean, questions: Int) {
        this.numberOfQuestions = if (questions == 0) 5 else questions
        this.selectedLanguage = language
        this.safetySettings = safety
    }

    // Clear chat history (placeholder function)
    fun clearChat() {
        // Implement clear history functionality if required
    }

    // Send message with user input and optional image, with quiz generation flag
    fun sendMessage(
        userInput: String,
        bitmap: Bitmap?,
        generateQuiz: Boolean,
        completion: (String) -> Unit
    ) {
        _uiState.value = UiState.Loading

        // Generate quiz prompt if required
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
                // Send message to the model
                val response = generativeModel.generateContent(
                    content {
                        text(userInput)
                        bitmap?.let { image(it) }
                    }
                )

                // Process response
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