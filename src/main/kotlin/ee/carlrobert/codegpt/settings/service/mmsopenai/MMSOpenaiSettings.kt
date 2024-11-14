package ee.carlrobert.codegpt.settings.service.mmsopenai

import com.intellij.openapi.components.BaseState
import com.intellij.openapi.components.SimplePersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import ee.carlrobert.codegpt.codecompletions.InfillPromptTemplate

@State(name = "CodeGPT_MMSOpenaiSettings_211", storages = [Storage("CodeGPT_MMSOpenaiSettings_211.xml")])
class MMSOpenaiSettings :
    SimplePersistentStateComponent<MMSOpenaiSettingsState>(MMSOpenaiSettingsState())

class MMSOpenaiSettingsState : BaseState() {
    var host by string("http://blabla:11434")
    var model by string()
    var completionModel by string()
    var codeCompletionsEnabled by property(false)
    var fimTemplate by enum<InfillPromptTemplate>(InfillPromptTemplate.CODE_LLAMA)
    var availableModels by list<String>()
}