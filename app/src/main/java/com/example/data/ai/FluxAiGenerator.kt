package com.example.data.ai

import com.google.firebase.Firebase
import com.google.firebase.ai.ai
import com.google.firebase.ai.type.Schema
import com.google.firebase.ai.type.generationConfig
import kotlinx.coroutines.withTimeout
import org.json.JSONObject

data class GeneratedSiteResult(
    val title: String,
    val summary: String,
    val subdomain: String,
    val htmlContent: String,
)

class FluxAiGenerator {

    private val siteSchema = Schema.obj(
        properties = mapOf(
            "title" to Schema.string(description = "Concise 2-4 word brand or website title"),
            "summary" to Schema.string(description = "One-sentence summary of sections and visual style"),
            "subdomain" to Schema.string(description = "Lowercase hyphenated URL slug using only a-z, 0-9, and -"),
            "htmlContent" to Schema.string(
                description = "Complete, self-contained responsive HTML5 document with embedded <style> CSS and optional interactive <script> JS. Must look polished on mobile screens (360px+) and desktop."
            ),
        )
    )

    private val model by lazy {
        Firebase.ai.generativeModel(
            modelName = "gemini-3.8-flash",
            generationConfig = generationConfig {
                responseMimeType = "application/json"
                responseSchema = siteSchema
                temperature = 0.7f
            }
        )
    }

    suspend fun generateSite(prompt: String, category: String): GeneratedSiteResult {
        val fullPrompt = """
            You are Flux AI, an expert web designer and front-end engineer.
            Build a complete, production-quality, single-file responsive website in HTML5 + CSS3 + vanilla JS.
            Category: $category
            User request: $prompt

            Requirements:
            - Include a crisp modern navigation header, hero section with compelling headline and CTA button, structured feature/product cards, social proof or metrics section, and footer.
            - Use modern CSS (flexbox/grid, CSS variables, subtle gradients, clean typography via system-ui/sans-serif, generous spacing, mobile-first viewport meta tag).
            - Ensure interactive buttons or tabs work smoothly with inline JavaScript (e.g., modal/toast feedback or section switching without navigating away).
            - Do NOT include external broken image URLs; use clean SVG illustrations, CSS gradients, and crisp iconography.
            - Ensure high text contrast and zero horizontal overflow on 360px mobile viewports.
        """.trimIndent()

        val response = withTimeout(60_000L) {
            model.generateContent(fullPrompt)
        }
        val jsonString = response.text ?: throw IllegalStateException("Flux AI returned an empty response.")
        val json = JSONObject(jsonString)

        return GeneratedSiteResult(
            title = json.optString("title", "Flux Site").take(100),
            summary = json.optString("summary", "AI-generated responsive site").take(500),
            subdomain = json.optString("subdomain", "flux-site"),
            htmlContent = json.optString("htmlContent").ifBlank {
                throw IllegalStateException("Flux AI did not return valid HTML content.")
            }
        )
    }

    suspend fun refineSite(
        currentTitle: String,
        currentHtml: String,
        instruction: String,
    ): GeneratedSiteResult {
        val fullPrompt = """
            You are Flux AI, an expert web designer. Refine the existing HTML5 website according to the user's update instruction while keeping everything responsive, self-contained, and visually cohesive.

            Current Title: $currentTitle
            Refinement Instruction: $instruction

            Existing HTML (update and improve this):
            ${currentHtml.take(28000)}
        """.trimIndent()

        val response = withTimeout(60_000L) {
            model.generateContent(fullPrompt)
        }
        val jsonString = response.text ?: throw IllegalStateException("Flux AI returned an empty refinement.")
        val json = JSONObject(jsonString)

        return GeneratedSiteResult(
            title = json.optString("title", currentTitle).take(100),
            summary = json.optString("summary", "Refined with Flux AI").take(500),
            subdomain = json.optString("subdomain", "flux-site"),
            htmlContent = json.optString("htmlContent").ifBlank {
                throw IllegalStateException("Flux AI did not return updated HTML content.")
            }
        )
    }
}
