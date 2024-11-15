package ee.carlrobert.codegpt.completions

import ee.carlrobert.codegpt.completions.CompletionRequestUtil.EDIT_CODE_SYSTEM_PROMPT
import ee.carlrobert.codegpt.completions.CompletionRequestUtil.GENERATE_METHOD_NAMES_SYSTEM_PROMPT
import ee.carlrobert.codegpt.completions.factory.*
import ee.carlrobert.codegpt.settings.service.ServiceType
import ee.carlrobert.llm.completion.CompletionRequest

interface CompletionRequestFactory {
    fun createChatRequest(params: ChatCompletionParameters): CompletionRequest
    fun createEditCodeRequest(params: EditCodeCompletionParameters): CompletionRequest
    fun createCommitMessageRequest(params: CommitMessageCompletionParameters): CompletionRequest
    fun createLookupRequest(params: LookupCompletionParameters): CompletionRequest

    companion object {
        @JvmStatic
        fun getFactory(serviceType: ServiceType): CompletionRequestFactory {
            return when (serviceType) {
                ServiceType.CODEGPT -> CodeGPTRequestFactory()
                ServiceType.OPENAI -> OpenAIRequestFactory()
                ServiceType.CUSTOM_OPENAI -> CustomOpenAIRequestFactory()
                ServiceType.AZURE -> AzureRequestFactory()
                ServiceType.ANTHROPIC -> ClaudeRequestFactory()
                ServiceType.GOOGLE -> GoogleRequestFactory()
                ServiceType.OLLAMA -> OllamaRequestFactory()
                ServiceType.MMS_OPENAI -> MMSOpenaiRequestFactory()
                ServiceType.LLAMA_CPP -> LlamaRequestFactory()
            }
        }
    }
}

abstract class BaseRequestFactory : CompletionRequestFactory {
    override fun createEditCodeRequest(params: EditCodeCompletionParameters): CompletionRequest {
        val prompt = "Code to modify:\n${params.selectedText}\n\nInstructions: ${params.prompt}"
        return createBasicCompletionRequest(EDIT_CODE_SYSTEM_PROMPT, prompt, 8192, true)
    }

    override fun createCommitMessageRequest(params: CommitMessageCompletionParameters): CompletionRequest {
        return createBasicCompletionRequest(params.systemPrompt, params.gitDiff, 512, true)
    }

    override fun createLookupRequest(params: LookupCompletionParameters): CompletionRequest {
        return createBasicCompletionRequest(GENERATE_METHOD_NAMES_SYSTEM_PROMPT, params.prompt, 512)
    }

    abstract fun createBasicCompletionRequest(
        systemPrompt: String,
        userPrompt: String,
        maxTokens: Int = 4096,
        stream: Boolean = false
    ): CompletionRequest

    protected fun getPromptWithFilesContext(callParameters: ChatCompletionParameters): String {
        return callParameters.referencedFiles?.let {
            if (it.isEmpty()) {
                callParameters.message.prompt
            } else {
                CompletionRequestUtil.getPromptWithContext(
                    it,
                    callParameters.message.prompt
                )
            }
        } ?: return callParameters.message.prompt
    }
}
