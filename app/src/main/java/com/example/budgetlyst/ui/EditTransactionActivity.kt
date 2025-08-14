package com.example.budgetlyst.ui

import android.app.DatePickerDialog
import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import com.example.budgetlyst.R
import com.example.budgetlyst.data.TransactionRepository
import com.example.budgetlyst.databinding.ActivityAddTransactionBinding
import com.example.budgetlyst.model.Category
import com.example.budgetlyst.model.Transaction
import com.example.budgetlyst.notification.NotificationManager
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class EditTransactionActivity : AppCompatActivity() {
    private lateinit var binding: ActivityAddTransactionBinding
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var notificationManager: NotificationManager
    private val calendar = Calendar.getInstance()
    private val dateFormatter = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault())

    private lateinit var transaction: Transaction

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAddTransactionBinding.inflate(layoutInflater)
        setContentView(binding.root)

        transactionRepository = TransactionRepository(this)
        notificationManager = NotificationManager(this)

        // Get transaction data from intent
        val id = intent.getStringExtra("transaction_id") ?: ""
        val title = intent.getStringExtra("transaction_title") ?: ""
        val amount = intent.getDoubleExtra("transaction_amount", 0.0)
        val category = intent.getStringExtra("transaction_category") ?: ""
        val date = Date(intent.getLongExtra("transaction_date", System.currentTimeMillis()))
        val isExpense = intent.getBooleanExtra("transaction_is_expense", true)

        transaction = Transaction(id, title, amount, category, date, isExpense)
        calendar.time = transaction.date

        setupCategorySpinner()
        setupDatePicker()
        setupButtons()
        populateFields()
    }

    private fun setupCategorySpinner() {
        val categories = Category.DEFAULT_CATEGORIES.toTypedArray()
        val adapter = ArrayAdapter(
            this, android.R.layout.simple_spinner_item, categories
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCategory.adapter = adapter
    }

    private fun setupDatePicker() {
        binding.etDate.setText(dateFormatter.format(calendar.time))

        binding.etDate.setOnClickListener {
            DatePickerDialog(
                this,
                { _, year, month, dayOfMonth ->
                    calendar.set(Calendar.YEAR, year)
                    calendar.set(Calendar.MONTH, month)
                    calendar.set(Calendar.DAY_OF_MONTH, dayOfMonth)
                    binding.etDate.setText(dateFormatter.format(calendar.time))
                },
                calendar.get(Calendar.YEAR),
                calendar.get(Calendar.MONTH),
                calendar.get(Calendar.DAY_OF_MONTH)
            ).show()
        }
    }

    private fun populateFields() {
        binding.dialogTitle.text = getString(R.string.edit_transaction)
        binding.etTitle.setText(transaction.title)
        binding.etAmount.setText(transaction.amount.toString())

        val categoryPosition = Category.DEFAULT_CATEGORIES.indexOf(transaction.category)
        if (categoryPosition >= 0) {
            binding.spinnerCategory.setSelection(categoryPosition)
        }

        if (transaction.isExpense) {
            binding.radioExpense.isChecked = true
        } else {
            binding.radioIncome.isChecked = true
        }
    }

    private fun setupButtons() {
        binding.btnSave.setOnClickListener {
            if (validateInputs()) {
                updateTransaction()
            }
        }

        binding.btnCancel.setOnClickListener {
            finish()
        }
    }

    private fun validateInputs(): Boolean {
        var isValid = true

        if (binding.etTitle.text.toString().trim().isEmpty()) {
            binding.etTitle.error = getString(R.string.field_required)
            isValid = false
        }

        if (binding.etAmount.text.toString().trim().isEmpty()) {
            binding.etAmount.error = getString(R.string.field_required)
            isValid = false
        }

        return isValid
    }

    private fun updateTransaction() {
        try {
            transaction.title = binding.etTitle.text.toString().trim()
            transaction.amount = binding.etAmount.text.toString().toDouble()
            transaction.category = binding.spinnerCategory.selectedItem.toString()
            transaction.date = calendar.time
            transaction.isExpense = binding.radioExpense.isChecked

            transactionRepository.saveTransaction(transaction)
            notificationManager.checkBudgetAndNotify()

            Toast.makeText(
                this,
                getString(R.string.transaction_updated),
                Toast.LENGTH_SHORT
            ).show()

            finish()
        } catch (e: Exception) {
            Toast.makeText(
                this,
                getString(R.string.error_updating_transaction),
                Toast.LENGTH_SHORT
            ).show()
        }
    }
}