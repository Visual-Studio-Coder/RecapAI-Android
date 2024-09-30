package com.click.recapai

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID

sealed interface GradingResultUiState {
    object Initial : GradingResultUiState
    object Loading : GradingResultUiState
    data class Success(val gradingResult: GradingResult) : GradingResultUiState
    data class Error(val errorMessage: String) : GradingResultUiState
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuizScreen(quiz: Quiz, onFinish: () -> Unit, onBack: () -> Unit, geminiAPIViewModel: GeminiAPIViewModel) {
    var currentQuestionIndex by remember { mutableStateOf(0) }
    var selectedOptions by remember { mutableStateOf<Set<Option>>(emptySet()) }
    var showAnswer by remember { mutableStateOf(false) }
    var score by remember { mutableStateOf(0) }
    var quizFinished by remember { mutableStateOf(false) }
    var userInput by remember { mutableStateOf("") }
    var gradingResultUiState by remember { mutableStateOf<GradingResultUiState>(GradingResultUiState.Initial) }
    var userAnswers by remember { mutableStateOf<List<UserAnswer>>(emptyList()) }
    val context = LocalContext.current
    val keyboardController = LocalSoftwareKeyboardController.current

    if (quizFinished) {
        QuizResultScreen(score, userAnswers, onFinish)
    } else {
        Column(modifier = Modifier.fillMaxSize().padding(16.dp)) {
            TopAppBar(
                title = { Text(text = quiz.quiz_title) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            )
            Spacer(modifier = Modifier.height(16.dp))

            val question = quiz.questions[currentQuestionIndex]
            val multipleCorrectAnswers = question.options?.count { it.correct } ?: 0 > 1

            QuestionItem(
                question = question,
                selectedOptions = selectedOptions,
                onOptionSelected = { option ->
                    selectedOptions = if (multipleCorrectAnswers) {
                        if (selectedOptions.contains(option)) {
                            selectedOptions - option
                        } else {
                            selectedOptions + option
                        }
                    } else {
                        setOf(option)
                    }
                },
                showAnswer = showAnswer,
                userInput = userInput,
                onUserInputChange = { userInput = it },
                gradingResultUiState = gradingResultUiState
            )

            Spacer(modifier = Modifier.height(16.dp))

            when (gradingResultUiState) {
                is GradingResultUiState.Loading -> {
                    CircularProgressIndicator()
                }
                is GradingResultUiState.Success -> {
                    val result = (gradingResultUiState as GradingResultUiState.Success).gradingResult
                    Text(
                        text = if (result.isCorrect) "Correct!" else "Wrong!",
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (result.isCorrect) Color.Green else Color.Red
                    )
                    Text(text = "Expected Answer:\n${result.expectedAnswer}", style = MaterialTheme.typography.bodyMedium)
                    Text(text = "Feedback:\n${result.feedback}", style = MaterialTheme.typography.bodyMedium)
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(onClick = {
                        val userAnswer = UserAnswer(
                            question = question,
                            userAnswer = selectedOptions.map { it.text },
                            isCorrect = result.isCorrect,
                            correctAnswer = result.expectedAnswer
                        )
                        userAnswers = userAnswers + userAnswer

                        if (currentQuestionIndex < quiz.questions.size - 1) {
                            currentQuestionIndex++
                            showAnswer = false
                            gradingResultUiState = GradingResultUiState.Initial
                            userInput = ""
                            selectedOptions = emptySet()
                        } else {
                            quizFinished = true
                        }
                    }) {
                        Text("Next")
                    }
                }
                is GradingResultUiState.Error -> {
                    Text(text = (gradingResultUiState as GradingResultUiState.Error).errorMessage, color = Color.Red)
                }
                else -> {
                    Button(onClick = {
                        if (question.type == "free_answer") {
                            gradingResultUiState = GradingResultUiState.Loading
                            keyboardController?.hide()
                            CoroutineScope(Dispatchers.IO).launch {
                                try {
                                    val response = geminiAPIViewModel.sendMessage(
                                        userInput = """
                                        Question: ${question.question}. The user's response is: $userInput. Grade this free response, keeping the answers as short as humanly possible, and only output **THIS JSON STRUCTURE** and nothing else. DO NOT RETURN ANY QUIZ INFORMATION OR GENERATE ANY QUIZZES. ONLY GRADE THIS ANSWER. THERE SHOULD BE ONLY ONE JSON
                                        {
                                             "expectedAnswer": "Example expected answer",
                                            "isCorrect": true,
                                            "feedback": "Example feedback"
                                        }
                                        """.trimIndent(),
                                        imageUris = null,
                                        context = context,
                                        generateQuiz = false,
                                        completion = {
                                            CoroutineScope(Dispatchers.Main).launch {
                                                val result = parseGradingResultJson(it)
                                                gradingResultUiState = GradingResultUiState.Success(result)
                                            }
                                        }
                                    )
                                } catch (e: Exception) {
                                    CoroutineScope(Dispatchers.Main).launch {
                                        gradingResultUiState = GradingResultUiState.Error("Failed to grade response")
                                    }
                                }
                            }
                        } else {
                            showAnswer = true
                            gradingResultUiState = GradingResultUiState.Success(
                                GradingResult(
                                    expectedAnswer = question.options?.filter { it.correct }?.joinToString { it.text } ?: "",
                                    isCorrect = selectedOptions.all { it.correct } && selectedOptions.size == question.options?.count { it.correct },
                                    feedback = "Review the correct and incorrect options."
                                )
                            )
                        }
                    }, enabled = userInput.isNotEmpty() || selectedOptions.isNotEmpty()) {
                        Text("Submit")
                    }
                }
            }
        }
    }
}

@Composable
fun QuestionItem(
    question: Question,
    selectedOptions: Set<Option>,
    onOptionSelected: (Option) -> Unit,
    showAnswer: Boolean,
    userInput: String,
    onUserInputChange: (String) -> Unit,
    gradingResultUiState: GradingResultUiState
) {
    Column {
        Text(text = question.question, style = MaterialTheme.typography.bodyLarge)
        Spacer(modifier = Modifier.height(8.dp))

        if (question.type == "free_answer") {
            TextField(
                value = userInput,
                onValueChange = onUserInputChange,
                label = { Text("Your Answer") },
                enabled = !showAnswer && gradingResultUiState !is GradingResultUiState.Loading
            )
        } else {
            val multipleCorrectAnswers = question.options?.count { it.correct } ?: 0 > 1

            question.options?.forEach { option ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (multipleCorrectAnswers) {
                        Checkbox(
                            checked = selectedOptions.contains(option),
                            onCheckedChange = { onOptionSelected(option) },
                            enabled = !showAnswer
                        )
                    } else {
                        RadioButton(
                            selected = selectedOptions.contains(option),
                            onClick = { onOptionSelected(option) },
                            enabled = !showAnswer
                        )
                    }
                    Text(
                        text = option.text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = when {
                            showAnswer && option.correct -> Color.Green
                            showAnswer && !option.correct -> Color.Red
                            else -> Color.Unspecified
                        }
                    )
                }
            }
        }
    }
}

data class UserAnswer(
    val id: UUID = UUID.randomUUID(),
    val question: Question,
    val userAnswer: List<String>,
    val isCorrect: Boolean,
    val correctAnswer: String?
)

@Composable
fun QuizResultScreen(score: Int, userAnswers: List<UserAnswer>, onFinish: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Text(text = "Your Score: $score", style = MaterialTheme.typography.headlineSmall)
        Spacer(modifier = Modifier.height(16.dp))
        userAnswers.forEach { userAnswer ->
            Text(text = "Question: ${userAnswer.question.question}", style = MaterialTheme.typography.bodyLarge)
            Text(text = "Your Answer: ${userAnswer.userAnswer.joinToString()}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "Correct Answer: ${userAnswer.correctAnswer ?: "N/A"}", style = MaterialTheme.typography.bodyMedium)
            Text(text = if (userAnswer.isCorrect) "Correct" else "Incorrect", color = if (userAnswer.isCorrect) Color.Green else Color.Red)
            Spacer(modifier = Modifier.height(8.dp))
        }
        Spacer(modifier = Modifier.height(16.dp))
        Button(onClick = onFinish) {
            Text("Dismiss")
        }
    }
}