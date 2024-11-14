package ee.carlrobert.codegpt.settings.service.mmsopenai


import com.intellij.openapi.components.service
import com.intellij.openapi.options.Configurable
import ee.carlrobert.codegpt.settings.GeneralSettings
import ee.carlrobert.codegpt.settings.service.ServiceType
import javax.swing.JComponent

class MMSOpenaiSettingsConfigurable : Configurable {

    private lateinit var component: MMSOpenaiSettingsForm

    override fun getDisplayName(): String {
        return "CodeGPT: MMS Openai Service"
    }

    override fun createComponent(): JComponent {
        component = MMSOpenaiSettingsForm()
        return component.getForm()
    }

    override fun isModified(): Boolean {
        return component.isModified()
    }

    override fun apply() {
        component.applyChanges()
        service<GeneralSettings>().state.selectedService = ServiceType.MMS_OPENAI
    }

    override fun reset() {
        component.resetForm()
    }
}