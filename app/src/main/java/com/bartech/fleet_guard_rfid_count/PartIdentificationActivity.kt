package com.bartech.fleet_guard_rfid_count

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TableLayout
import android.widget.TableRow
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.material.button.MaterialButton
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textfield.MaterialAutoCompleteTextView
import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.annotations.SerializedName
import com.google.gson.reflect.TypeToken
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

// --- Data Classes ---

data class partIdentificationGetSrNoClassResponse(
    @SerializedName("SrNo")
    val srNo: String
)

data class partIdentificationSubmitClassResponse(
    @SerializedName("Status")
    val status: String,
    @SerializedName("Message")
    val message: String,
    @SerializedName("SrNo")
    val srNo: String
)

data class partIdentificationClassResponse(
    @SerializedName("Status")
    val status: String,
    @SerializedName("Message")
    val message: String,
)

data class partIdentificationValidTagClass(
    val status: String,
    val tag: String
)

data class partIdentificationInvalidTagClass(
    val status: String,
    val tag: String
)

data class partIdentificationPartNamesClassResponse(
    @SerializedName("SrNo")
    val srNo: String,
    @SerializedName("Part Code")
    val partCode: String,
    @SerializedName("Short Name")
    val shortName: String,
    @SerializedName("Part Name")
    val partName: String,
    @SerializedName("Max. Sterilize Cycle")
    val maxSterilizeCount: String,
    @SerializedName("Created_By")
    val createdBy: String,
    @SerializedName("Created_Date")
    val createdDate: String,
    @SerializedName("Status")
    val status: String,
    @SerializedName("Unique SrNo")
    val uniqueSrNo: String
)

class PartIdentificationActivity : AppCompatActivity() {

    // --- View Declarations ---
    private lateinit var backBtn: ImageView
    private lateinit var refreshBtn: ImageView
    private lateinit var partNamesInput: MaterialAutoCompleteTextView
    private lateinit var resultCard: MaterialCardView
    private lateinit var resultText: TextView
    private lateinit var partDetailsCard: MaterialCardView
    private lateinit var tvPartCodeValue: TextView
    private lateinit var tvPartNameValue: TextView
    private lateinit var tvMaxWashValue: TextView
    private lateinit var tvShortNameValue: TextView
    private lateinit var rfidButtonCard: MaterialCardView
    private lateinit var rfidStartBtn: MaterialButton
    private lateinit var rfidStopBtn: MaterialButton
    private lateinit var validTagTableCard: MaterialCardView
    private lateinit var validTagCountTextView: TextView
    private lateinit var validTagsTableLayout: TableLayout
    private lateinit var validTagsPaginationContainer: LinearLayout
    private lateinit var validTagsPreviousIcon: ImageView
    private lateinit var validTagsPageNumbers: TextView
    private lateinit var validTagsNextIcon: ImageView
    private lateinit var validTagsRowsPerPageSpinner: Spinner
    private lateinit var invalidTagTableCard: MaterialCardView
    private lateinit var invalidTagCountTextView: TextView
    private lateinit var invalidTagsTableLayout: TableLayout
    private lateinit var invalidTagsPaginationContainer: LinearLayout
    private lateinit var invalidTagsPreviousIcon: ImageView
    private lateinit var invalidTagsPageNumbers: TextView
    private lateinit var invalidTagsNextIcon: ImageView
    private lateinit var invalidTagsRowsPerPageSpinner: Spinner
    private lateinit var buttonLayout: LinearLayout
    private lateinit var submitBtn: MaterialButton

    private lateinit var partSerialNo: String

    // --- Pagination State ---
    private var validTagsCurrentPage = 0
    private var validTagsRowsPerPage = 5
    private var invalidTagsCurrentPage = 0
    private var invalidTagsRowsPerPage = 5

    // --- RFID/Validation State ---
    private var rfidInitialized = false
    private val tagValidationQueue = mutableListOf<String>()
    private var isTagProcessing = false
    private var currentProcessingTag: String? = null

    // --- Submission State ---
    private val tagSubmissionQueue = mutableListOf<partIdentificationValidTagClass>()
    private var isSubmissionProcessing = false
    private var totalTagsToSubmit = 0
    private var successfulSubmissions = 0
    private var failedSubmissions = 0

    // --- Data Management ---
    private var loadingDialog: Dialog? = null
    private val apiManager by lazy { ApiManager(this, Prefs(this)) }
    private val gson by lazy { Gson() }

    // Removed generic 'scannedTags' as its function is replaced by the queue and tables.
    private val partNamesData = mutableListOf<partIdentificationPartNamesClassResponse>()
    private val validTagTableData = mutableListOf<partIdentificationValidTagClass>()
    private val invalidTagTableData = mutableListOf<partIdentificationInvalidTagClass>()

    private var userId: String? = ""
    private var selectedPart: String? = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_part_identification)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }

        // --- View Initialization ---
        backBtn = findViewById(R.id.backBtn)
        refreshBtn = findViewById(R.id.refreshBtn)
        partNamesInput = findViewById(R.id.partNamesInput)
        resultCard = findViewById(R.id.resultCard)
        resultText = findViewById(R.id.resultText)
        partDetailsCard = findViewById(R.id.partDetailsCard)
        tvPartCodeValue = findViewById(R.id.tvPartCodeValue)
        tvPartNameValue = findViewById(R.id.tvPartNameValue)
        tvMaxWashValue = findViewById(R.id.tvMaxWashValue)
        tvShortNameValue = findViewById(R.id.tvShortNameValue)
        rfidButtonCard = findViewById(R.id.rfidButtonCard)
        rfidStartBtn = findViewById(R.id.rfidStartBtn)
        rfidStopBtn = findViewById(R.id.rfidStopBtn)
        validTagTableCard = findViewById(R.id.validTagTableCard)
        validTagCountTextView = findViewById(R.id.validTagCountTextView)
        validTagsTableLayout = findViewById(R.id.validTagsTableLayout)
        validTagsPaginationContainer = findViewById(R.id.validTagsPaginationContainer)
        validTagsPreviousIcon = findViewById(R.id.validTagsPreviousIcon)
        validTagsPageNumbers = findViewById(R.id.validTagsPageNumbers)
        validTagsNextIcon = findViewById(R.id.validTagsNextIcon)
        validTagsRowsPerPageSpinner = findViewById(R.id.validTagsRowsPerPageSpinner)
        invalidTagTableCard = findViewById(R.id.invalidTagTableCard)
        invalidTagCountTextView = findViewById(R.id.invalidTagCountTextView)
        invalidTagsTableLayout = findViewById(R.id.invalidTagsTableLayout)
        invalidTagsPaginationContainer = findViewById(R.id.invalidTagsPaginationContainer)
        invalidTagsPreviousIcon = findViewById(R.id.invalidTagsPreviousIcon)
        invalidTagsPageNumbers = findViewById(R.id.invalidTagsPageNumbers)
        invalidTagsNextIcon = findViewById(R.id.invalidTagsNextIcon)
        invalidTagsRowsPerPageSpinner = findViewById(R.id.invalidTagsRowsPerPageSpinner)
        buttonLayout = findViewById(R.id.buttonLayout)
        submitBtn = findViewById(R.id.submitBtn)

        userId = Prefs(this).getUserId() ?: "admin"

        // Initial RFID UI State: Disable buttons and hide card until ready
        rfidButtonCard.visibility = View.GONE
        rfidStartBtn.isEnabled = false
        rfidStopBtn.isEnabled = false
        rfidStartBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))
        rfidStopBtn.setBackgroundColor(ContextCompat.getColor(this, android.R.color.darker_gray))

        showLoadingDialog()

        // --- RFID Initialization (Asynchronous) ---
        RfidController.init(this, object : RfidListener {

            override fun onReady() {
                runOnUiThread {
                    hideLoadingDialog()
                    rfidInitialized = true
                    val api = RfidController.getApiVersion()

                    Toast.makeText(this@PartIdentificationActivity, "RFID Ready: API $api", Toast.LENGTH_SHORT).show()

                    // Set initial ready colors/state
                    rfidStartBtn.isEnabled = true
                    rfidStartBtn.setBackgroundColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.holo_green))
                    rfidStopBtn.isEnabled = false
                    rfidStopBtn.setBackgroundColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.gray_600))
                }
            }

            override fun onError(error: String) {
                runOnUiThread {
                    hideLoadingDialog()
                    rfidInitialized = false
                    Toast.makeText(this@PartIdentificationActivity, "RFID Error: $error", Toast.LENGTH_LONG).show()
                    rfidButtonCard.visibility = View.GONE
                }
            }

            override fun onTagRead(epc: String) {
                runOnUiThread {
                    handleNewTag(epc) // Sends tag to the validation queue
                }
            }

            override fun onScanStart() {
                runOnUiThread {
                    // START SCANNING: Disable Start, Enable Stop
                    rfidStartBtn.isEnabled = false
                    rfidStartBtn.setBackgroundColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.gray_600)) // Disabled Color

                    rfidStopBtn.isEnabled = true
                    rfidStopBtn.setBackgroundColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.holo_red)) // Enabled/Active Stop Color

                    Toast.makeText(this@PartIdentificationActivity, "Scanning Started.", Toast.LENGTH_SHORT).show()
                }
            }

            override fun onScanStop() {
                runOnUiThread {
                    // STOP SCANNING: Enable Start, Disable Stop
                    rfidStartBtn.isEnabled = true
                    rfidStartBtn.setBackgroundColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.holo_green)) // Enabled Color

                    rfidStopBtn.isEnabled = false
                    rfidStopBtn.setBackgroundColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.gray_600)) // Disabled Color

                    Toast.makeText(this@PartIdentificationActivity, "Scanning Stopped.", Toast.LENGTH_SHORT).show()
                }
            }
        })

        // --- Data Fetching and Listeners ---
        fetchPartIdentification()

        backBtn.setOnClickListener {
            if (rfidStopBtn.isEnabled) {
                stopRfidScanning()
            }
            onBackPressed()
        }

        refreshBtn.setOnClickListener {
            refreshPage()
        }

        rfidStartBtn.setOnClickListener {
            startRfidScanning()
        }

        rfidStopBtn.setOnClickListener {
            stopRfidScanning()
        }

        submitBtn.setOnClickListener {
            startSubmissionProcess()
        }

        setupValidTagsPagination()
        setupInvalidTagsPagination()
    }


    private fun handleNewTag(epc: String) {
        if (epc.isBlank()) return

        // 1. Check if tag is already processed (in either table)
        val alreadyProcessed = validTagTableData.any { it.tag == epc } || invalidTagTableData.any { it.tag == epc }

        if (!alreadyProcessed) {
            // 2. Add to queue only if it's not currently being processed or already in the queue
            if (!tagValidationQueue.contains(epc) && epc != currentProcessingTag) {
                tagValidationQueue.add(epc)
                Log.d("RFID_QUEUE", "Added tag to queue: $epc. Queue size: ${tagValidationQueue.size}")
                // 3. Kick off processing if nothing is currently running
                processNextTagInQueue()
            }
        }
    }

    private fun processNextTagInQueue() {
        if (isTagProcessing) return // Already processing a tag
        if (tagValidationQueue.isEmpty()) return // Nothing left to process

        isTagProcessing = true
        currentProcessingTag = tagValidationQueue.removeAt(0)
        Log.d("RFID_QUEUE", "Starting processing for: $currentProcessingTag")

        // Start the API validation
        currentProcessingTag?.let {
            validateTagApi(it)
        }
    }

    private fun validateTagApi(scannedTagValue: String) {

        if (Prefs(this).getIpAddress().isNullOrEmpty() || Prefs(this).getPortNumber().isNullOrEmpty()) {
            Toast.makeText(this, "Please do the Network Configuration first.", Toast.LENGTH_SHORT).show()
            validationFinished(false) // Release lock and move on
            return
        }

        val endpoint = "api/hht/tag-check"
        val token = Prefs(this).getToken() ?: ""

        val jsonObject = JsonObject().apply {
            addProperty("V_TagValue", scannedTagValue)
        }
        Log.d("Submitted Tag", scannedTagValue)


        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { showLoadingDialog() }

            try {
                val response = apiManager.callApi(endpoint, "POST", jsonObject.toString(),token)
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    if (response.isSuccessful && responseBody != null) {
                        // Pass the EPC back for use in the response handler
                        handleScannedCodeValidationSuccessfulResponse(scannedTagValue, responseBody)
                    } else {
                        val errorMessage = response.message
                        if (response.code != 440 && response.code != 401) {
                            Toast.makeText(this@PartIdentificationActivity, "Error ${response.code}: $errorMessage", Toast.LENGTH_LONG).show()
                        } else {
                            showErrorResult(errorMessage)
                        }
                        validationFinished(false) // Release lock on API failure
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    showErrorResult("Network Error: ${e.message}")
                    validationFinished(false) // Release lock on exception
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    showErrorResult("Configuration Error: ${e.message}")
                    validationFinished(false) // Release lock on exception
                }
            }
        }
    }

    private fun handleScannedCodeValidationSuccessfulResponse(epc: String, responseBody: String) {
        Log.d("Validation Response Body", responseBody.toString())
        val listType = object : TypeToken<List<partIdentificationClassResponse>>() {}.type
        val validationList: List<partIdentificationClassResponse> = try {
            gson.fromJson(responseBody, listType)
        } catch (e: Exception) {
            Log.e("STP_PARSE_ERROR", "Failed to parse response body as List:", e)
            showErrorResult("API Data Error: Cannot read response structure for $epc.")
            validationFinished(false)
            return
        }

        val details = validationList.firstOrNull() ?: run {
            showErrorResult("API returned an empty validation response for $epc.")
            validationFinished(false)
            return
        }

        // Handle the response based on the status field
        when (details.status) {
            "T" -> {
                Log.d("RFID_VALIDATION", "Tag valid: $epc")
                addValidTagToTable(partIdentificationValidTagClass("Valid", epc))
            }
            "F" -> {
                Log.d("RFID_VALIDATION", "Tag invalid: $epc")
                // Use the message from the API as the status/reason for the invalid tag
                addInvalidTagToTable(partIdentificationInvalidTagClass("Invalid", epc))
            }
            else -> {
                showErrorResult("Unknown status: ${details.status} for $epc")
            }
        }

        validationFinished(true)
    }

    private fun validationFinished(success: Boolean) {
        isTagProcessing = false
        currentProcessingTag = null
        updateTableButtonUIState()
        processNextTagInQueue()
    }

    private fun updateTableButtonUIState() {
        // Show/hide button layout based on valid tags presence
        buttonLayout.visibility = if (validTagTableData.isNotEmpty()) View.VISIBLE else View.GONE

        // Show/hide table cards based on data presence
        validTagTableCard.visibility = if (validTagTableData.isNotEmpty()) View.VISIBLE else View.GONE
        invalidTagTableCard.visibility = if (invalidTagTableData.isNotEmpty()) View.VISIBLE else View.GONE

        // Show/hide pagination containers based on table data
        validTagsPaginationContainer.visibility = if (validTagTableData.isNotEmpty()) View.VISIBLE else View.GONE
        invalidTagsPaginationContainer.visibility = if (invalidTagTableData.isNotEmpty()) View.VISIBLE else View.GONE

        refreshValidTagsTableUI()
        refreshInvalidTagsTableUI()
    }

    private fun addValidTagToTable(data: partIdentificationValidTagClass) {
        validTagTableData.add(data)
        refreshValidTagsTableUI()
    }

    private fun addInvalidTagToTable(data: partIdentificationInvalidTagClass) {
        invalidTagTableData.add(data)
        refreshInvalidTagsTableUI()
    }

    private fun setupValidTagsPagination() {
        validTagsPaginationContainer.visibility = View.GONE

        val rowOptionsText = arrayOf("5 records", "10 records", "20 records", "30 records", "50 records")
        val rowValues = arrayOf(5, 10, 20, 30, 50) // Actual values

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rowOptionsText)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        validTagsRowsPerPageSpinner.adapter = adapter
        validTagsRowsPerPageSpinner.setSelection(0)

        validTagsRowsPerPageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                validTagsRowsPerPage = rowValues[position]
                validTagsCurrentPage = 0
                refreshValidTagsTableUI()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        validTagsPreviousIcon.setOnClickListener {
            if (validTagsCurrentPage > 0) {
                validTagsCurrentPage--
                refreshValidTagsTableUI()
            }
        }

        validTagsNextIcon.setOnClickListener {
            val totalPages = (validTagTableData.size + validTagsRowsPerPage - 1) / validTagsRowsPerPage
            if (validTagsCurrentPage < totalPages - 1) {
                validTagsCurrentPage++
                refreshValidTagsTableUI()
            }
        }
    }

    private fun refreshValidTagsTableUI() {
        // Clear all rows from the table, but keep the header row (index 0)
        if (validTagsTableLayout.childCount > 1) {
            validTagsTableLayout.removeViews(1, validTagsTableLayout.childCount - 1)
        }

        val start = validTagsCurrentPage * validTagsRowsPerPage
        val end = minOf(start + validTagsRowsPerPage, validTagTableData.size)

        for (i in start until end) {
            val item = validTagTableData[i]
            val newRow = TableRow(this)
            newRow.layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            )

            // Sr. No. column
            newRow.addView(createCellTextView((i + 1).toString()))

            // Tag/EPC column
            newRow.addView(createCellTextView(item.tag))

            // Status column (e.g., "Valid")
            newRow.addView(createCellTextView(item.status))

            validTagsTableLayout.addView(newRow)
        }

        validTagCountTextView.text = "Valid Tags Count: ${validTagTableData.size.toString()}"

        val totalPages = if (validTagTableData.isEmpty()) 0 else (validTagTableData.size + validTagsRowsPerPage - 1) / validTagsRowsPerPage
        validTagsPageNumbers.text = "${validTagsCurrentPage + 1} / $totalPages"

        validTagsPreviousIcon.alpha = if (validTagsCurrentPage > 0) 1.0f else 0.5f
        validTagsNextIcon.alpha = if (validTagsCurrentPage < totalPages - 1) 1.0f else 0.5f
    }

    private fun setupInvalidTagsPagination() {
        invalidTagsPaginationContainer.visibility = View.GONE

        val rowOptionsText = arrayOf("5 records", "10 records", "20 records", "30 records", "50 records")
        val rowValues = arrayOf(5, 10, 20, 30, 50) // Actual values

        val adapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, rowOptionsText)
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        invalidTagsRowsPerPageSpinner.adapter = adapter
        invalidTagsRowsPerPageSpinner.setSelection(0)

        invalidTagsRowsPerPageSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                invalidTagsRowsPerPage = rowValues[position]
                invalidTagsCurrentPage = 0
                refreshInvalidTagsTableUI()
            }
            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }

        invalidTagsPreviousIcon.setOnClickListener {
            if (invalidTagsCurrentPage > 0) {
                invalidTagsCurrentPage--
                refreshInvalidTagsTableUI()
            }
        }

        invalidTagsNextIcon.setOnClickListener {
            val totalPages = if (invalidTagTableData.isEmpty()) 0 else (invalidTagTableData.size + invalidTagsRowsPerPage - 1) / invalidTagsRowsPerPage
            if (invalidTagsCurrentPage < totalPages - 1) {
                invalidTagsCurrentPage++
                refreshInvalidTagsTableUI()
            }
        }
    }

    private fun refreshInvalidTagsTableUI() {
        // Clear all rows from the table, but keep the header row (index 0)
        if (invalidTagsTableLayout.childCount > 1) {
            invalidTagsTableLayout.removeViews(1, invalidTagsTableLayout.childCount - 1)
        }

        val start = invalidTagsCurrentPage * invalidTagsRowsPerPage
        val end = minOf(start + invalidTagsRowsPerPage, invalidTagTableData.size)

        for (i in start until end) {
            val item = invalidTagTableData[i]
            val newRow = TableRow(this)
            newRow.layoutParams = TableRow.LayoutParams(
                TableRow.LayoutParams.MATCH_PARENT,
                TableRow.LayoutParams.WRAP_CONTENT
            )

            // Sr. No. column
            newRow.addView(createCellTextView((i + 1).toString()))

            // Tag/EPC column
            newRow.addView(createCellTextView(item.tag))

            // Status/Reason column
            newRow.addView(createCellTextView(item.status))

            invalidTagsTableLayout.addView(newRow)
        }

        invalidTagCountTextView.text = "Invalid Tags Count: ${invalidTagTableData.size.toString()}"

        val totalPages = if (invalidTagTableData.isEmpty()) 0 else (invalidTagTableData.size + invalidTagsRowsPerPage - 1) / invalidTagsRowsPerPage
        invalidTagsPageNumbers.text = "${invalidTagsCurrentPage + 1} / $totalPages"

        invalidTagsPreviousIcon.alpha = if (invalidTagsCurrentPage > 0) 1.0f else 0.5f
        invalidTagsNextIcon.alpha = if (invalidTagsCurrentPage < totalPages - 1) 1.0f else 0.5f
    }

    private fun createCellTextView(text: String?): TextView {
        return TextView(this).apply {
            this.text = text ?: ""
            textAlignment = View.TEXT_ALIGNMENT_CENTER
            setPadding(8, 20, 8, 20)
            setTextColor(ContextCompat.getColor(this@PartIdentificationActivity, R.color.text_primary))
            setBackgroundResource(R.drawable.table_cell_border)
        }
    }

    private fun refreshPage() {
        resetActivityState()
        resultCard.visibility = View.GONE
        selectedPart = ""
    }

    private fun fetchPartIdentification() {

        partNamesData.clear()
        partNamesInput.setText("")
        selectedPart = ""

        if (Prefs(this).getIpAddress().isNullOrEmpty() || Prefs(this).getPortNumber().isNullOrEmpty()) {
            Toast.makeText(this, "Please do the Network Configuration first.", Toast.LENGTH_SHORT).show()
            return
        }

        val endpoint = "api/hht/part-master"
        val token = Prefs(this).getToken() ?: ""
        val jsonObject = JsonObject().apply {}

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { showLoadingDialog() }

            try {
                val response = apiManager.callApi(endpoint, "POST", jsonObject.toString(),token)
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    if (response.isSuccessful && responseBody != null) {
                        handlePartNamesResponse(responseBody)
                    } else {
                        val errorMessage = response.message
                        if (response.code != 440 && response.code != 401) {
                            Toast.makeText(this@PartIdentificationActivity, "Error ${response.code}: $errorMessage", Toast.LENGTH_LONG).show()
                        } else {
                            showErrorResult(errorMessage)
                        }
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    showErrorResult("Network Error: ${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    showErrorResult("Configuration Error: ${e.message}")
                }
            }
        }
    }

    private fun handlePartNamesResponse(responseBody: String) {
        try {
            // Use TypeToken to safely parse the JSON array into a List
            val listType = object : TypeToken<List<partIdentificationPartNamesClassResponse>>() {}.type
            val responseList: List<partIdentificationPartNamesClassResponse> = gson.fromJson(responseBody, listType)

            // Clear existing data and prepare the new list
            partNamesData.clear()

            // Filter out any blank or null Part Names
            val validCodes = responseList.filter {
                it.partName.isNotBlank()
            }

            val codes = if (validCodes.isNotEmpty()) {
                partNamesData.addAll(validCodes)
                // Map to an array of Part Names for the AutoCompleteTextView
                validCodes.map { it.partName }.toTypedArray()
            } else {
                emptyArray()
            }

            // --- Create and set the adapter ---
            val adapter = ArrayAdapter(
                this,
                android.R.layout.simple_dropdown_item_1line,
                codes
            )
            partNamesInput.setAdapter(adapter)

            // Set the item selected listener
            partNamesInput.setOnItemClickListener { parent, view, position, id ->
                val selectedPartName = parent.getItemAtPosition(position) as String

                // 1. Find the corresponding data object from the cached list
                val selectedDetails = partNamesData.find { it.partName == selectedPartName }

                if (selectedDetails != null) {
                    // 2. Set the details in the TextViews
                    tvPartCodeValue.text = selectedDetails.partCode
                    tvPartNameValue.text = selectedDetails.partName
                    // Assuming Max. Sterilize Cycle is correctly handled as a String
                    tvMaxWashValue.text = selectedDetails.maxSterilizeCount
                    tvShortNameValue.text = selectedDetails.shortName

                    // 3. Update UI visibility
                    selectedPart = selectedPartName
                    partDetailsCard.visibility = View.VISIBLE
                    rfidButtonCard.visibility = if (rfidInitialized) View.VISIBLE else View.GONE
                    fetchPartSerialNo(selectedDetails.shortName)
                    resultCard.visibility = View.GONE
                } else {
                    // Fallback, though this should not typically happen
                    selectedPart = ""
                    partDetailsCard.visibility = View.GONE
                    showErrorResult("Error: Could not find details for the selected part.")
                }
            }

            // If there are no codes, show a message.
            if (codes.isEmpty()) {
                showSuccessResult("No Part Names found.")
            }

        } catch (e: Exception) {
            showErrorResult("Failed to parse API response. Received data was not a list.")
        }
    }

    private fun fetchPartSerialNo(shortCode: String) {
        if (Prefs(this).getIpAddress().isNullOrEmpty() || Prefs(this).getPortNumber().isNullOrEmpty()) {
            Toast.makeText(this, "Please do the Network Configuration first.", Toast.LENGTH_SHORT).show()
            return
        }

        val endpoint = "api/hht/select-serial-no"
        val token = Prefs(this).getToken() ?: ""

        val jsonObject = JsonObject().apply {
            addProperty("ShortCode", shortCode)
        }

        lifecycleScope.launch(Dispatchers.IO) {
            withContext(Dispatchers.Main) { showLoadingDialog() }
            try {
                // Note: Loading dialog is managed by startSubmissionProcess/finalizeSubmission
                val response = apiManager.callApi(endpoint, "POST", jsonObject.toString(),token)
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    hideLoadingDialog()
                    if (response.isSuccessful && responseBody != null) {
                        handleFetchPartSerialNoSuccessfulResponse(responseBody)
                    } else {
                        val errorMessage = response.message
                        showErrorResult("API Error ${response.code}: $errorMessage")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    showErrorResult("Network Error: ${e.message}")
                    hideLoadingDialog()
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    showErrorResult("Configuration Error: ${e.message}")
                    hideLoadingDialog()
                }
            }
        }
    }

    private fun handleFetchPartSerialNoSuccessfulResponse(responseBody: String) {
        val listType = object : TypeToken<List<partIdentificationGetSrNoClassResponse>>() {}.type
        val validationList: List<partIdentificationGetSrNoClassResponse> = try {
            gson.fromJson(responseBody, listType)
        } catch (e: Exception) {
            Log.e("STP_PARSE_ERROR", "Failed to parse submission response body as List:", e)
            showErrorResult("API Data Error: Cannot read response structure.")
            return
        }

        val details = validationList.firstOrNull() ?: run {
            showErrorResult("API returned an empty submission response.")
            return
        }

        partSerialNo = details.srNo.toString()

    }

    private fun startSubmissionProcess() {
        if (validTagTableData.isEmpty()) {
            showErrorResult("No valid tags to submit.")
            return
        }

        // 1. Prepare the queue
        tagSubmissionQueue.clear()
        tagSubmissionQueue.addAll(validTagTableData)
        totalTagsToSubmit = tagSubmissionQueue.size
        successfulSubmissions = 0
        failedSubmissions = 0
        isSubmissionProcessing = false

        // 2. Disable submit button and start loading dialog
        submitBtn.isEnabled = false
        showLoadingDialog()

        // 3. Start the sequential processing
        processNextTagForSubmission()
    }

    private fun processNextTagForSubmission() {
        if (isSubmissionProcessing) return

        if (tagSubmissionQueue.isEmpty()) {
            // All tags processed, finalize the activity state
            hideLoadingDialog()
            finalizeSubmission()
            return
        }

        isSubmissionProcessing = true
        // Get the next tag for submission
        val tagToSubmit = tagSubmissionQueue.removeAt(0)

        // Show progress in the result card
        val remaining = tagSubmissionQueue.size + 1
        showSuccessResult("Submitting tags: $remaining remaining. Success: $successfulSubmissions / Failed: $failedSubmissions")


        // Execute the API call
        submitTagApi(tagToSubmit)
    }

    private fun submitTagApi(tagData: partIdentificationValidTagClass) {
        if (Prefs(this).getIpAddress().isNullOrEmpty() || Prefs(this).getPortNumber().isNullOrEmpty()) {
            Toast.makeText(this, "Please do the Network Configuration first.", Toast.LENGTH_SHORT).show()
            submissionFinished(false, "Network config missing.")
            return
        }

        val endpoint = "api/hht/part-identification-insert"
        val token = Prefs(this).getToken() ?: ""

        val partUniqueNameValue = tvShortNameValue.text.toString() + partSerialNo


        val jsonObject = JsonObject().apply {
            addProperty("PartCode", tvPartCodeValue.text.toString())
            addProperty("PartName", tvPartNameValue.text.toString())
            addProperty("Part_Unique_No", partUniqueNameValue.toString())
            addProperty("TagValue", tagData.tag)
            addProperty("MaxWash", tvMaxWashValue.text.toString().toInt())
            addProperty("Created_By", userId)
            addProperty("ShortName", tvShortNameValue.text.toString())
        }

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                // Note: Loading dialog is managed by startSubmissionProcess/finalizeSubmission
                val response = apiManager.callApi(endpoint, "POST", jsonObject.toString(),token)
                val responseBody = response.body?.string()

                withContext(Dispatchers.Main) {
                    if (response.isSuccessful && responseBody != null) {
                        handleSubmitTagSuccessfulResponse(responseBody)
                    } else {
                        val errorMessage = response.message
                        submissionFinished(false, "API Error ${response.code}: $errorMessage")
                    }
                }
            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    submissionFinished(false, "Network Error: ${e.message}")
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    submissionFinished(false, "Configuration Error: ${e.message}")
                }
            }
        }
    }

    private fun handleSubmitTagSuccessfulResponse(responseBody: String) {
        val listType = object : TypeToken<List<partIdentificationSubmitClassResponse>>() {}.type
        val validationList: List<partIdentificationSubmitClassResponse> = try {
            gson.fromJson(responseBody, listType)
        } catch (e: Exception) {
            Log.e("STP_PARSE_ERROR", "Failed to parse submission response body as List:", e)
            submissionFinished(false, "API Data Error: Cannot read response structure.")
            return
        }

        val details = validationList.firstOrNull() ?: run {
            submissionFinished(false, "API returned an empty submission response.")
            return
        }

        when (details.status) {
            "T" -> {
                partSerialNo = details.srNo.toString()
                submissionFinished(true, details.message)
            }
            "F" -> {
                submissionFinished(false, details.message)
            }
            else -> {
                submissionFinished(false, "Unknown status: ${details.status}")
            }
        }
    }

    private fun submissionFinished(success: Boolean, message: String) {
        isSubmissionProcessing = false

        if (success) {
            successfulSubmissions++
            Log.d("TAG_SUBMISSION", "Success: $message")
        } else {
            failedSubmissions++
            Log.e("TAG_SUBMISSION", "Failure: $message")
        }

        // Process the next tag immediately
        processNextTagForSubmission()
    }

    private fun finalizeSubmission() {
        submitBtn.isEnabled = true

        val message = if (failedSubmissions == 0) {
            "Successfully submitted all $totalTagsToSubmit valid tags."
        } else {
            "Submission complete: $successfulSubmissions successful, $failedSubmissions failed. Check logs for details."
        }

        // Final result message
        if (failedSubmissions == 0) {
            showSuccessResult(message)
        } else {
            showErrorResult(message)
        }

        // Reset everything else and clear data/tables
        resetActivityState()
    }

    private fun resetActivityState() {
        validTagTableData.clear()
        invalidTagTableData.clear()
        tagValidationQueue.clear()

        partNamesInput.setText("")
        selectedPart = ""
        validTagsCurrentPage = 0
        invalidTagsCurrentPage = 0

        rfidButtonCard.visibility = View.GONE
        partDetailsCard.visibility = View.GONE

        updateTableButtonUIState()
        fetchPartIdentification()
        if (rfidStopBtn.isEnabled) {
            stopRfidScanning()
        }
    }

    private fun startRfidScanning() {
        if (!rfidInitialized) {
            showErrorResult("RFID not ready. Please wait for initialization.")
            return
        }

        RfidController.setScanMode(RfidScanMode.CONTINUOUS)
        RfidController.startScan()
    }


    private fun stopRfidScanning() {
        RfidController.stopScan()
    }


    private fun showSuccessResult(message: String) {
        resultCard.visibility = View.VISIBLE
        resultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.success))
        resultText.text = message
    }

    private fun showErrorResult(message: String) {
        resultCard.visibility = View.VISIBLE
        resultCard.setCardBackgroundColor(ContextCompat.getColor(this, R.color.error))
        resultText.text = message
    }

    private fun showLoadingDialog() {
        if (loadingDialog == null) {
            loadingDialog = Dialog(this).apply {
                setCancelable(false)
                // Assuming R.layout.loading_dialog exists
                setContentView(LayoutInflater.from(context).inflate(R.layout.loading_dialog, null))
                window?.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
                window?.setBackgroundDrawableResource(android.R.color.transparent)
            }
        }
        loadingDialog?.show()
    }

    private fun hideLoadingDialog() {
        loadingDialog?.dismiss()
        loadingDialog = null
    }

    override fun onDestroy() {
        if (rfidStopBtn.isEnabled) {
            stopRfidScanning()
        }
        RfidController.release()
        super.onDestroy()
    }
}