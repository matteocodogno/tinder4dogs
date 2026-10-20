package com.ai4dev.tinder4dogs.playdate

import org.slf4j.LoggerFactory
import org.springframework.ai.chat.messages.SystemMessage
import org.springframework.ai.chat.messages.UserMessage
import org.springframework.ai.chat.model.ChatModel
import org.springframework.ai.chat.prompt.Prompt
import org.springframework.ai.model.tool.ToolCallingManager
import org.springframework.ai.openai.OpenAiChatOptions
import org.springframework.ai.support.ToolCallbacks
import org.springframework.beans.factory.ObjectProvider
import org.springframework.beans.factory.annotation.Value
import org.springframework.stereotype.Component

/**
 * The agent loop, written by hand. Used when app.playdate.loop=manual.
 *
 * In Spring AI 2.0 a ChatModel does not run tools: call() returns the model's
 * tool calls and stops. The loop lives in ChatClient, in the ToolCallingAdvisor
 * it registers by itself, and its only cap is spring.ai.tools.limits
 * (150 tool calls in total and 40 per tool by default). Here the same loop is
 * twenty lines: one log line per step, and our own step limit.
 *
 * The options start from the model's own (OpenAiChatOptions, because the
 * gateway is OpenAI-compatible and OpenAiChatModel accepts only those), plus
 * the tools and the owner in the tool context, exactly as ChatClient passes
 * them in the framework path.
 */
@Component
class PlaydateLoop(
    private val chatModels: ObjectProvider<ChatModel>,
    private val toolCallingManagers: ObjectProvider<ToolCallingManager>,
    private val tools: PlaydateTools,
    @Value("\${app.playdate.max-steps:5}") private val maxSteps: Int,
) {

    private val log = LoggerFactory.getLogger(javaClass)

    fun run(system: String, user: String, ownerSubject: String): String? {
        val chatModel = chatModels.ifAvailable ?: return null
        val manager = toolCallingManagers.getIfAvailable { ToolCallingManager.builder().build() }

        val options = (chatModel.options as OpenAiChatOptions).mutate()
            .toolCallbacks(*ToolCallbacks.from(tools))
            .toolContext(mapOf(PlaydateTools.OWNER to ownerSubject))
            .build()

        var prompt = Prompt(listOf(SystemMessage(system), UserMessage(user)), options)
        var response = chatModel.call(prompt)
        var steps = 0

        while (response.hasToolCalls()) {
            steps++
            if (steps > maxSteps) {
                log.warn("loop=manual stopped after {} steps", maxSteps)
                return STOPPED
            }
            log.info("loop=manual step={} tools={}", steps, response.result?.output?.toolCalls?.map { it.name() })
            val result = manager.executeToolCalls(prompt, response)
            prompt = Prompt(result.conversationHistory(), options)
            response = chatModel.call(prompt)
        }

        log.info("loop=manual done after {} tool steps", steps)
        return response.result?.output?.text
    }

    private companion object {
        const val STOPPED = "Stopped: the request needed too many steps."
    }
}
