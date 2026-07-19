package com.example.data

import android.util.Log
import com.example.BuildConfig
import com.example.data.api.Content
import com.example.data.api.GenerateContentRequest
import com.example.data.api.GenerationConfig
import com.example.data.api.ImageConfig
import com.example.data.api.Part
import com.example.data.api.RetrofitClient
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

class AuraRepository(private val dao: AuraDao) {

    val allSessions: Flow<List<ChatSession>> = dao.getAllSessions()
    val allImages: Flow<List<GeneratedImage>> = dao.getAllGeneratedImages()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessage>> =
        dao.getMessagesForSession(sessionId)

    suspend fun createSession(title: String): Long = withContext(Dispatchers.IO) {
        dao.insertSession(ChatSession(title = title))
    }

    suspend fun deleteSession(sessionId: Long) = withContext(Dispatchers.IO) {
        dao.deleteMessagesForSession(sessionId)
        dao.deleteSessionById(sessionId)
    }

    suspend fun addMessage(sessionId: Long, role: String, text: String) = withContext(Dispatchers.IO) {
        dao.insertMessage(
            ChatMessage(
                sessionId = sessionId,
                role = role,
                text = text
            )
        )
        dao.updateSessionActivity(sessionId, System.currentTimeMillis())
    }

    suspend fun renameSession(sessionId: Long, title: String) = withContext(Dispatchers.IO) {
        dao.updateSessionTitle(sessionId, title)
    }

    suspend fun deleteImage(imageId: Long) = withContext(Dispatchers.IO) {
        dao.deleteGeneratedImageById(imageId)
    }

    suspend fun saveImage(prompt: String, aspectRatio: String, base64Data: String): Long =
        withContext(Dispatchers.IO) {
            dao.insertGeneratedImage(
                GeneratedImage(
                    prompt = prompt,
                    aspectRatio = aspectRatio,
                    base64Data = base64Data
                )
            )
        }

    // --- Gemini API: Chat & Problem Solving ---
    suspend fun generateChatResponse(
        sessionId: Long,
        userPrompt: String,
        sessionMessages: List<ChatMessage>
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key is empty or placeholder! Please set it in the Secrets panel in AI Studio."))
        }

        // Add user message to DB
        addMessage(sessionId, "user", userPrompt)

        // Build history contents for request
        val contents = mutableListOf<Content>()
        
        // Append previous conversation context
        sessionMessages.forEach { msg ->
            contents.add(
                Content(
                    role = if (msg.role == "user") "user" else "model",
                    parts = listOf(Part(text = msg.text))
                )
            )
        }
        
        // Append current prompt
        contents.add(
            Content(
                role = "user",
                parts = listOf(Part(text = userPrompt))
            )
        )

        // System instruction to act as a friendly, helpful companion and problem solver
        val systemInstruction = Content(
            parts = listOf(
                Part(
                    text = "You are Aura, a friendly, encouraging AI companion and expert problem solver. " +
                            "Help the user solve their tasks, answer their questions with clarity, structure your answers with markdown, " +
                            "and maintain a warm, supportive, and engaging tone. Avoid dry responses and guide them step-by-step when appropriate."
                )
            )
        )

        val request = GenerateContentRequest(
            contents = contents,
            systemInstruction = systemInstruction,
            generationConfig = GenerationConfig(temperature = 0.7f)
        )

        try {
            val response = RetrofitClient.service.generateContent(
                model = "gemini-3.5-flash",
                apiKey = apiKey,
                request = request
            )

            val reply = response.candidates?.firstOrNull()?.content?.parts?.firstOrNull()?.text
                ?: "I apologize, but I couldn't formulate a response. Could you try rephrasing your question?"

            // Add model response to DB
            addMessage(sessionId, "model", reply)

            // Auto-rename session if it has only 1-2 messages (i.e. still "New Chat")
            if (sessionMessages.size <= 1) {
                val newTitle = if (userPrompt.length > 30) userPrompt.take(27) + "..." else userPrompt
                renameSession(sessionId, newTitle)
            }

            Result.success(reply)
        } catch (e: Exception) {
            Log.e("AuraRepository", "Error generating chat response", e)
            Result.failure(e)
        }
    }

    // --- Gemini API: Image Generation (Imagen 3) ---
    suspend fun generateImage(
        prompt: String,
        aspectRatio: String // "1:1", "3:4", "4:3", "9:16", "16:9"
    ): Result<String> = withContext(Dispatchers.IO) {
        val apiKey = BuildConfig.GEMINI_API_KEY
        if (apiKey.isEmpty() || apiKey == "MY_GEMINI_API_KEY") {
            return@withContext Result.failure(Exception("Gemini API Key is empty or placeholder! Please set it in the Secrets panel in AI Studio."))
        }

        val request = GenerateContentRequest(
            contents = listOf(
                Content(
                    parts = listOf(Part(text = prompt))
                )
            ),
            generationConfig = GenerationConfig(
                imageConfig = ImageConfig(aspectRatio = aspectRatio, imageSize = "1K"),
                responseModalities = listOf("TEXT", "IMAGE")
            )
        )

        try {
            val response = RetrofitClient.service.generateContent(
                model = "gemini-2.5-flash-image",
                apiKey = apiKey,
                request = request
            )

            // Search candidates for part with inlineData (binary image)
            var base64Image: String? = null
            response.candidates?.forEach { candidate ->
                candidate.content?.parts?.forEach { part ->
                    if (part.inlineData != null && part.inlineData.mimeType.startsWith("image/")) {
                        base64Image = part.inlineData.data
                    }
                }
            }

            val imgData = base64Image
            if (imgData != null) {
                // Save it to history automatically so the user never loses it!
                saveImage(prompt, aspectRatio, imgData)
                Result.success(imgData)
            } else {
                Result.failure(Exception("No image data found in the response. Check if prompt violated policy or try another prompt."))
            }
        } catch (e: Exception) {
            Log.e("AuraRepository", "Error generating image", e)
            Result.failure(e)
        }
    }
}
