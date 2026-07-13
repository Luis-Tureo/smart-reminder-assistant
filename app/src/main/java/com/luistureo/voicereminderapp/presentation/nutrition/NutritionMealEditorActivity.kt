package com.luistureo.voicereminderapp.presentation.nutrition

import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import com.google.android.material.button.MaterialButton
import com.google.android.material.checkbox.MaterialCheckBox
import com.google.android.material.textfield.TextInputEditText
import com.luistureo.voicereminderapp.R
import com.luistureo.voicereminderapp.core.nutrition.NutritionPhotoStore
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMeal
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionMealPeriod
import com.luistureo.voicereminderapp.domain.nutrition.model.NutritionReminderType
import com.luistureo.voicereminderapp.presentation.nutrition.state.NutritionMealListItem
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModel
import com.luistureo.voicereminderapp.presentation.nutrition.viewmodel.NutritionViewModelFactory
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.launch
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class NutritionMealEditorActivity : ComponentActivity() {
    private lateinit var viewModel: NutritionViewModel
    private lateinit var periodSpinner: Spinner
    private lateinit var reminderSpinner: Spinner
    private lateinit var nameInput: TextInputEditText
    private lateinit var foodsInput: TextInputEditText
    private lateinit var preparationInput: TextInputEditText
    private lateinit var notesInput: TextInputEditText
    private lateinit var minutesInput: TextInputEditText
    private lateinit var dateButton: MaterialButton
    private lateinit var timeButton: MaterialButton
    private lateinit var customReminderButton: MaterialButton
    private lateinit var additionalGroup: LinearLayout
    private lateinit var photoPreview: ImageView
    private lateinit var deleteButton: MaterialButton

    private val mealId by lazy { intent.getIntExtra(EXTRA_MEAL_ID, 0) }
    private var selectedDate: LocalDate = LocalDate.now()
    private var selectedTime: LocalTime? = null
    private var customReminderAt: Long? = null
    private var photoUri: Uri? = null
    private var originalPhotoUri: Uri? = null
    private var boundExisting = false
    private var photoCommitted = false

    private val photoLauncher = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        uri?.let {
            lifecycleScope.launch {
                runCatching {
                    withContext(Dispatchers.IO) {
                        NutritionPhotoStore.importToLocalStorage(applicationContext, it)
                    }
                }.onSuccess { localUri ->
                    if (photoUri != originalPhotoUri) {
                        NutritionPhotoStore.deleteIfManaged(applicationContext, photoUri)
                    }
                    photoUri = localUri
                    photoPreview.setImageURI(localUri)
                    photoPreview.isVisible = true
                }.onFailure { error ->
                    Toast.makeText(
                        this@NutritionMealEditorActivity,
                        error.message ?: getString(R.string.nutrition_photo_import_failed),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_nutrition_meal_editor)
        selectedDate = intent.getLongExtra(EXTRA_DATE_EPOCH_DAY, LocalDate.now().toEpochDay())
            .let(LocalDate::ofEpochDay)
        viewModel = ViewModelProvider(this, NutritionViewModelFactory(applicationContext))[
            NutritionViewModel::class.java
        ]
        setupViews()
        observeState()
        if (mealId > 0) viewModel.loadMeal(mealId)
    }

    private fun setupViews() {
        findViewById<ImageButton>(R.id.btnBackNutritionMealEditor).setOnClickListener { finish() }
        periodSpinner = findViewById(R.id.spinnerNutritionMealPeriod)
        reminderSpinner = findViewById(R.id.spinnerNutritionMealReminder)
        nameInput = findViewById(R.id.inputNutritionMealName)
        foodsInput = findViewById(R.id.inputNutritionMealFoods)
        preparationInput = findViewById(R.id.inputNutritionMealPreparation)
        notesInput = findViewById(R.id.inputNutritionMealNotes)
        minutesInput = findViewById(R.id.inputNutritionReminderMinutes)
        dateButton = findViewById(R.id.btnNutritionMealDate)
        timeButton = findViewById(R.id.btnNutritionMealTime)
        customReminderButton = findViewById(R.id.btnNutritionCustomReminder)
        additionalGroup = findViewById(R.id.groupNutritionMealAdditional)
        photoPreview = findViewById(R.id.imgNutritionMealPhoto)
        deleteButton = findViewById(R.id.btnDeleteNutritionMeal)
        periodSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            NutritionMealPeriod.entries.map(NutritionUiFormatter::period)
        )
        reminderSpinner.adapter = ArrayAdapter(
            this,
            android.R.layout.simple_spinner_dropdown_item,
            NutritionReminderType.entries.map(NutritionUiFormatter::reminder)
        )
        reminderSpinner.onItemSelectedListener = SimpleItemSelectedListener { updateReminderFields() }
        findViewById<MaterialCheckBox>(R.id.checkNutritionAdditionalOptions)
            .setOnCheckedChangeListener { _, checked -> additionalGroup.isVisible = checked }
        dateButton.setOnClickListener { selectDate() }
        timeButton.setOnClickListener { selectTime() }
        customReminderButton.setOnClickListener { selectCustomReminder() }
        findViewById<MaterialButton>(R.id.btnNutritionChoosePhoto).setOnClickListener {
            photoLauncher.launch(arrayOf("image/*"))
        }
        findViewById<MaterialButton>(R.id.btnNutritionRemovePhoto).setOnClickListener {
            if (photoUri != originalPhotoUri) {
                NutritionPhotoStore.deleteIfManaged(applicationContext, photoUri)
            }
            photoUri = null
            photoPreview.setImageDrawable(null)
            photoPreview.isVisible = false
        }
        findViewById<MaterialButton>(R.id.btnSaveNutritionMeal).setOnClickListener { save() }
        deleteButton.isVisible = mealId > 0
        deleteButton.setOnClickListener { confirmDelete() }
        refreshDateAndTime()
        updateReminderFields()
    }

    private fun bindMeal(item: com.luistureo.voicereminderapp.domain.nutrition.model.DatedNutritionMeal) {
        if (boundExisting) return
        boundExisting = true
        selectedDate = item.date
        val meal = item.meal
        nameInput.setText(meal.name)
        periodSpinner.setSelection(meal.period.ordinal)
        selectedTime = meal.time
        foodsInput.setText(meal.foodsOrDishes)
        preparationInput.setText(meal.preparationNote)
        notesInput.setText(meal.personalNotes)
        reminderSpinner.setSelection(meal.reminderType.ordinal)
        minutesInput.setText(meal.reminderMinutesBefore?.toString().orEmpty())
        customReminderAt = meal.customReminderAtEpochMillis
        photoUri = meal.photoUri?.let(Uri::parse)
        originalPhotoUri = photoUri
        photoUri?.let {
            photoPreview.setImageURI(it)
            photoPreview.isVisible = true
        }
        val hasAdditional = listOf(
            meal.time,
            meal.foodsOrDishes,
            meal.preparationNote,
            meal.photoUri,
            meal.personalNotes
        ).any { it != null } || meal.reminderType != NutritionReminderType.NONE
        findViewById<MaterialCheckBox>(R.id.checkNutritionAdditionalOptions).isChecked = hasAdditional
        refreshDateAndTime()
        updateReminderFields()
    }

    private fun save() {
        val existing = viewModel.uiState.value.selectedMeal?.meal
        val reminderType = NutritionReminderType.entries[reminderSpinner.selectedItemPosition]
        val meal = (existing ?: NutritionMeal(
            name = "",
            period = NutritionMealPeriod.BREAKFAST
        )).copy(
            name = nameInput.text?.toString().orEmpty(),
            period = NutritionMealPeriod.entries[periodSpinner.selectedItemPosition],
            time = selectedTime,
            foodsOrDishes = foodsInput.text?.toString(),
            preparationNote = preparationInput.text?.toString(),
            photoUri = photoUri?.toString(),
            reminderType = reminderType,
            reminderMinutesBefore = minutesInput.text?.toString()?.toIntOrNull(),
            customReminderAtEpochMillis = customReminderAt,
            personalNotes = notesInput.text?.toString(),
            updatedAtEpochMillis = System.currentTimeMillis()
        )
        viewModel.saveMeal(selectedDate, meal) {
            if (originalPhotoUri != photoUri) {
                NutritionPhotoStore.deleteIfManaged(applicationContext, originalPhotoUri)
            }
            photoCommitted = true
            finish()
        }
    }

    private fun confirmDelete() {
        val item = viewModel.uiState.value.selectedMeal ?: return
        AlertDialog.Builder(this)
            .setTitle(R.string.nutrition_delete_meal_title)
            .setMessage(R.string.nutrition_delete_meal_message)
            .setNegativeButton(R.string.nutrition_cancel, null)
            .setPositiveButton(R.string.nutrition_delete) { _, _ ->
                viewModel.deleteMeal(NutritionMealListItem(item.date, item.meal)) {
                    NutritionPhotoStore.deleteIfManaged(applicationContext, originalPhotoUri)
                    photoCommitted = true
                    finish()
                }
            }.show()
    }

    private fun selectDate() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                selectedDate = LocalDate.of(year, month + 1, day)
                refreshDateAndTime()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun selectTime() {
        val initial = selectedTime ?: LocalTime.of(12, 0)
        TimePickerDialog(this, { _, hour, minute ->
            selectedTime = LocalTime.of(hour, minute)
            refreshDateAndTime()
        }, initial.hour, initial.minute, true).show()
    }

    private fun selectCustomReminder() {
        val current = customReminderAt?.let {
            Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
        } ?: ZonedDateTime.now().plusHours(1)
        DatePickerDialog(this, { _, year, month, day ->
            TimePickerDialog(this, { _, hour, minute ->
                customReminderAt = ZonedDateTime.of(
                    LocalDate.of(year, month + 1, day),
                    LocalTime.of(hour, minute),
                    ZoneId.systemDefault()
                ).toInstant().toEpochMilli()
                refreshDateAndTime()
            }, current.hour, current.minute, true).show()
        }, current.year, current.monthValue - 1, current.dayOfMonth).show()
    }

    private fun updateReminderFields() {
        val type = NutritionReminderType.entries.getOrElse(reminderSpinner.selectedItemPosition) {
            NutritionReminderType.NONE
        }
        minutesInput.isVisible = type == NutritionReminderType.MINUTES_BEFORE
        customReminderButton.isVisible = type == NutritionReminderType.CUSTOM
    }

    private fun refreshDateAndTime() {
        dateButton.text = NutritionUiFormatter.date(selectedDate)
        timeButton.text = selectedTime?.toString() ?: getString(R.string.nutrition_no_time)
        customReminderButton.text = customReminderAt?.let {
            val value = Instant.ofEpochMilli(it).atZone(ZoneId.systemDefault())
            getString(
                R.string.nutrition_custom_reminder_value,
                NutritionUiFormatter.date(value.toLocalDate()),
                value.toLocalTime().withSecond(0).withNano(0).toString()
            )
        } ?: getString(R.string.nutrition_select_custom_reminder)
    }

    private fun observeState() {
        lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.uiState.collect { state ->
                    state.selectedMeal?.let(::bindMeal)
                    state.message?.let {
                        Toast.makeText(this@NutritionMealEditorActivity, it, Toast.LENGTH_SHORT).show()
                        viewModel.consumeMessage()
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        if (!photoCommitted && !isChangingConfigurations && photoUri != originalPhotoUri) {
            NutritionPhotoStore.deleteIfManaged(applicationContext, photoUri)
        }
        super.onDestroy()
    }

    companion object {
        const val EXTRA_MEAL_ID = "extra_nutrition_meal_id"
        const val EXTRA_DATE_EPOCH_DAY = "extra_nutrition_date_epoch_day"
    }
}
