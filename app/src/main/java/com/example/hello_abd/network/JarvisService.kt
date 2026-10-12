package com.example.hello_abd.network

import io.ktor.client.*
import io.ktor.client.call.*
import io.ktor.client.engine.cio.*
import io.ktor.client.plugins.contentnegotiation.*
import io.ktor.client.request.*
import io.ktor.http.*
import io.ktor.serialization.kotlinx.json.*
import kotlinx.serialization.Serializable

// Corrected data classes to match your FastAPI backend
@Serializable
data class JarvisQuery(val question: String)

@Serializable
data class JarvisResponse(val answer: String)

object JarvisService {
    private val client = HttpClient(CIO) {
        install(ContentNegotiation) {
            json()
        }
    }

    private const val BASE_URL = "https://backend-m31m.onrender.com"

    suspend fun getResponse(query: String): Result<String> {
        return try {
            // Corrected endpoint to /jarvis
            val response = client.post("$BASE_URL/jarvis") {
                contentType(ContentType.Application.Json)
                setBody(JarvisQuery(question = query))
            }
            if (response.status == HttpStatusCode.OK) {
                Result.success(response.body<JarvisResponse>().answer)
            } else {
                Result.failure(Exception("Your Network might be Slow!"))
            }
        } catch (e: Exception) {
            Result.failure(Exception("Your Network might be slow! Check it"))
        }
    }
}
