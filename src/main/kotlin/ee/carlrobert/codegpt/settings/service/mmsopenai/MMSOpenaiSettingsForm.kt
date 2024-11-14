package ee.carlrobert.codegpt.settings.service.mmsopenai

import com.intellij.notification.NotificationType
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.components.service
import com.intellij.openapi.diagnostic.thisLogger
import com.intellij.openapi.observable.util.whenTextChangedFromUi
import com.intellij.openapi.ui.ComboBox
import com.intellij.openapi.ui.MessageType
import com.intellij.ui.TitledSeparator
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.ui.FormBuilder
import ee.carlrobert.codegpt.CodeGPTBundle
import ee.carlrobert.codegpt.credentials.CredentialsStore.CredentialKey.MMS_OPENAI_KEY
import ee.carlrobert.codegpt.credentials.CredentialsStore.getCredential
import ee.carlrobert.codegpt.credentials.CredentialsStore.setCredential
import ee.carlrobert.codegpt.settings.service.CodeCompletionConfigurationForm
import ee.carlrobert.codegpt.ui.OverlayUtil
import ee.carlrobert.codegpt.ui.UIUtil
import ee.carlrobert.codegpt.ui.URLTextField
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import okhttp3.OkHttpClient
import okhttp3.Request
import java.awt.BorderLayout
import java.lang.String.format
import java.net.ConnectException
import java.util.concurrent.TimeoutException
import javax.swing.ComboBoxModel
import javax.swing.DefaultComboBoxModel
import javax.swing.JButton
import javax.swing.JPanel
import org.json.JSONObject


class MMSOpenaiSettingsForm {

    private val refreshModelsButton =
        JButton(CodeGPTBundle.get("settingsConfigurable.service.mmsopenai.models.refresh"))
    private val hostField: JBTextField
    private val modelComboBox: ComboBox<String>
    private val completionModelComboBox: ComboBox<String>
    private val codeCompletionConfigurationForm: CodeCompletionConfigurationForm
    private val apiKeyField: JBPasswordField

    companion object {
        private val logger = thisLogger()
    }

    init {
        val settings = service<MMSOpenaiSettings>().state
        codeCompletionConfigurationForm = CodeCompletionConfigurationForm(
            settings.codeCompletionsEnabled,
            settings.fimTemplate
        )
        val emptyModelsComboBoxModel =
            DefaultComboBoxModel(arrayOf("Hit refresh to see models for this host"))
        modelComboBox = ComboBox(emptyModelsComboBoxModel).apply {
            isEnabled = false
        }
        completionModelComboBox = ComboBox(emptyModelsComboBoxModel).apply {
            isEnabled = false
        }
        hostField = URLTextField().apply {
            text = settings.host
            whenTextChangedFromUi {
                modelComboBox.model = emptyModelsComboBoxModel
                modelComboBox.isEnabled = false
                completionModelComboBox.model = emptyModelsComboBoxModel
                completionModelComboBox.isEnabled = false
            }
        }



        refreshModelsButton.addActionListener {
            refreshModels(getModel() ?: settings.model, getCompletionModel()?:settings.completionModel)
        }
        apiKeyField = JBPasswordField().apply {
            columns = 30
            text = runBlocking(Dispatchers.IO) {
                getCredential(MMS_OPENAI_KEY)
            }
        }
        refreshModels(settings.model, settings.completionModel)
    }

    fun getForm(): JPanel = FormBuilder.createFormBuilder()
        .addComponent(TitledSeparator(CodeGPTBundle.get("shared.configuration")))
        .addComponent(
            FormBuilder.createFormBuilder()
                .setFormLeftIndent(16)
                .addLabeledComponent(
                    CodeGPTBundle.get("settingsConfigurable.shared.baseHost.label"),
                    hostField
                )
                .addLabeledComponent(
                    CodeGPTBundle.get("settingsConfigurable.shared.model.label"),
                    JPanel(BorderLayout(8, 0)).apply {
                        add(modelComboBox, BorderLayout.CENTER)
                        add(refreshModelsButton, BorderLayout.EAST)
                    }
                )
                .addComponent(TitledSeparator(CodeGPTBundle.get("settingsConfigurable.shared.authentication.title")))
                .setFormLeftIndent(32)
                .addLabeledComponent(
                    CodeGPTBundle.get("settingsConfigurable.shared.apiKey.label"),
                    apiKeyField
                )
                .addComponentToRightColumn(UIUtil.createComment("settingsConfigurable.shared.apiKey.comment"))
                .panel
        )
        .addComponent(TitledSeparator(CodeGPTBundle.get("shared.codeCompletions")))
        .addComponent(UIUtil.withEmptyLeftBorder(codeCompletionConfigurationForm.getForm()))
        .addLabeledComponent(
            CodeGPTBundle.get("settingsConfigurable.shared.model.label"),
            completionModelComboBox
        )
        .addComponentFillVertically(JPanel(), 0)
        .panel

    fun getModel(): String? {
        return if (modelComboBox.isEnabled) {
            modelComboBox.item
        } else {
            null
        }
    }

    fun getCompletionModel():String?{
        return if (completionModelComboBox.isEnabled) {
            completionModelComboBox.item
        } else {
            null
        }
    }

    fun getApiKey(): String? = String(apiKeyField.password).ifEmpty { null }

    fun resetForm() {
        service<MMSOpenaiSettings>().state.run {
            hostField.text = host
            modelComboBox.item = model ?: ""
            codeCompletionConfigurationForm.isCodeCompletionsEnabled = codeCompletionsEnabled
            codeCompletionConfigurationForm.fimTemplate = fimTemplate
        }
        apiKeyField.text = getCredential(MMS_OPENAI_KEY)
    }

    fun applyChanges() {
        service<MMSOpenaiSettings>().state.run {
            host = hostField.text
            model = modelComboBox.item
            codeCompletionsEnabled = codeCompletionConfigurationForm.isCodeCompletionsEnabled
            fimTemplate = codeCompletionConfigurationForm.fimTemplate!!
            completionModel = completionModelComboBox.item
        }
        setCredential(MMS_OPENAI_KEY, getApiKey())
    }

    fun isModified() = service<MMSOpenaiSettings>().state.run {
        hostField.text != host
                || (modelComboBox.item != model && modelComboBox.isEnabled)
                || (completionModelComboBox.item != completionModel && completionModelComboBox.isEnabled)
                || codeCompletionConfigurationForm.isCodeCompletionsEnabled != codeCompletionsEnabled
                || codeCompletionConfigurationForm.fimTemplate != fimTemplate
                || getApiKey() != getCredential(MMS_OPENAI_KEY)
    }

    private fun refreshModels(currentModel: String?, currentCompletionModel:String?) {
        disableModelComboBoxWithPlaceholder(DefaultComboBoxModel(arrayOf("Loading")))

        ReadAction.nonBlocking<List<String>> {
            try {
                getModelIds()
            } catch (t: Throwable) {
                handleModelLoadingError(t)
                throw t
            }
        }
            .finishOnUiThread(ModalityState.defaultModalityState()) { models ->
                updateModelComboBoxState(models, currentModel,currentCompletionModel)
            }
            .submit(AppExecutorUtil.getAppExecutorService())
    }
    private fun getModelIds(): List<String> {
        val client = OkHttpClient()
        val builder = Request.Builder()
            .url((hostField.text) + "/v1/models")
            .header("Cache-Control", "no-cache")
            .header("Content-Type", "application/json")
        val apiKey = getApiKey()
        if (apiKey != null) {
            builder.header("Authorization", "Bearer $apiKey")
        }

        client.newCall(builder.build()).execute().use { response ->
            if (!response.isSuccessful) {
                throw Exception("Failed to retrieve models: ${response.code}")
            }

            val responseBody = response.body!!.string()
            val jsonObject = JSONObject(responseBody)
            val dataArray = jsonObject.getJSONArray("data")

            return (0 until dataArray.length()).map { index ->
                dataArray.getJSONObject(index).getString("id")
            }
        }
    }
    private fun updateModelComboBoxState(models: List<String>, currentModel: String?,currentCompletionModel:String?) {
        if (models.isNotEmpty()) {
            modelComboBox.model = DefaultComboBoxModel(models.toTypedArray())
            completionModelComboBox.model = DefaultComboBoxModel(models.toTypedArray())
            modelComboBox.isEnabled = true
            completionModelComboBox.isEnabled = true
            currentModel?.let {
                if (models.contains(currentModel)) {
                    modelComboBox.selectedItem = currentModel
                } else {
                    OverlayUtil.showBalloon(
                        format(
                            CodeGPTBundle.get("validation.error.model.notExists"),
                            currentModel
                        ),
                        MessageType.ERROR,
                        modelComboBox
                    )
                }
                if (models.contains(currentCompletionModel)) {
                    completionModelComboBox.selectedItem = currentCompletionModel
                } else {
                    OverlayUtil.showBalloon(
                        format(
                            CodeGPTBundle.get("validation.error.model.notExists"),
                            currentModel
                        ),
                        MessageType.ERROR,
                        modelComboBox
                    )
                }
            }
        } else {
            modelComboBox.model = DefaultComboBoxModel(arrayOf("No models"))
        }
        val availableModels = ApplicationManager.getApplication()
            .getService(MMSOpenaiSettings::class.java)
            .state.availableModels
        availableModels.removeAll { !models.contains(it) }
        models.forEach { model ->
            if (!availableModels.contains(model)) {
                availableModels.add(model)
            }
        }
        availableModels.sortWith(
            compareBy({ it.split(":").first() }, {
                if (it.contains("latest")) 1 else 0
            })
        )
    }

    private fun handleModelLoadingError(ex: Throwable) {
        //logger.error(ex)
        when (ex) {
            is TimeoutException -> OverlayUtil.showNotification(
                "Connection to MMS Openai server timed out",
                NotificationType.ERROR
            )

            is ConnectException -> OverlayUtil.showNotification(
                "Unable to connect to MMS Openai server",
                NotificationType.ERROR
            )

            else -> OverlayUtil.showNotification(ex.message ?: "Error", NotificationType.ERROR)
        }
        disableModelComboBoxWithPlaceholder(DefaultComboBoxModel(arrayOf("Unable to load models")))
    }

    private fun disableModelComboBoxWithPlaceholder(placeholderModel: ComboBoxModel<String>) {
        modelComboBox.apply {
            model = placeholderModel
            isEnabled = false
        }
        completionModelComboBox.apply {
            model = placeholderModel
            isEnabled = false
        }
    }
}
