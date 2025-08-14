package com.example.budgetlyst.data

import android.content.Context
import android.content.SharedPreferences
import com.example.budgetlyst.model.Transaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.Calendar

class TransactionRepository(context: Context) {
    private val sharedPreferences: SharedPreferences = context.getSharedPreferences(
        TRANSACTIONS_PREFS, Context.MODE_PRIVATE
    )
    private val gson = Gson()
    private val context = context.applicationContext

    companion object {
        private const val TRANSACTIONS_PREFS = "transactions_prefs"
        private const val KEY_TRANSACTIONS = "transactions"
        private const val BACKUP_FILENAME = "transactions_backup.json"
    }

    fun saveTransaction(transaction: Transaction) {
        val transactions = getAllTransactions().toMutableList()

        // Check if transaction exists (for updates)
        val existingIndex = transactions.indexOfFirst { it.id == transaction.id }
        if (existingIndex >= 0) {
            transactions[existingIndex] = transaction
        } else {
            transactions.add(transaction)
        }

        saveAllTransactions(transactions)
    }

    fun deleteTransaction(transactionId: String) {
        val transactions = getAllTransactions().toMutableList()
        transactions.removeIf { it.id == transactionId }
        saveAllTransactions(transactions)
    }

    fun getAllTransactions(): List<Transaction> {
        val transactionsJson = sharedPreferences.getString(KEY_TRANSACTIONS, null)
        return if (transactionsJson != null) {
            val type = object : TypeToken<List<Map<String, Any>>>() {}.type
            val transactionsList: List<Map<String, Any>> = gson.fromJson(transactionsJson, type)
            transactionsList.map { Transaction.fromMap(it) }
        } else {
            emptyList()
        }
    }

    private fun saveAllTransactions(transactions: List<Transaction>) {
        val transactionsMapList = transactions.map { it.toMap() }
        val transactionsJson = gson.toJson(transactionsMapList)
        sharedPreferences.edit().putString(KEY_TRANSACTIONS, transactionsJson).apply()
    }

    fun getTransactionsForMonth(month: Int, year: Int): List<Transaction> {
        return getAllTransactions().filter { transaction ->
            val calendar = Calendar.getInstance()
            calendar.time = transaction.date
            calendar.get(Calendar.MONTH) == month && calendar.get(Calendar.YEAR) == year
        }
    }

    fun getExpensesForMonth(month: Int, year: Int): List<Transaction> {
        return getTransactionsForMonth(month, year).filter { it.isExpense }
    }

    fun getIncomeForMonth(month: Int, year: Int): List<Transaction> {
        return getTransactionsForMonth(month, year).filter { !it.isExpense }
    }

    fun getTotalExpensesForMonth(month: Int, year: Int): Double {
        return getExpensesForMonth(month, year).sumOf { it.amount }
    }

    fun getTotalIncomeForMonth(month: Int, year: Int): Double {
        return getIncomeForMonth(month, year).sumOf { it.amount }
    }

    fun getExpensesByCategory(month: Int, year: Int): Map<String, Double> {
        val expenses = getExpensesForMonth(month, year)
        return expenses.groupBy { it.category }
            .mapValues { (_, transactions) -> transactions.sumOf { it.amount } }
    }

    // Save backup to both external URI and internal storage
    fun backupToUri(context: Context, uri: android.net.Uri): Boolean {
        try {
            val transactionsJson = sharedPreferences.getString(KEY_TRANSACTIONS, null)
            if (transactionsJson.isNullOrBlank() || transactionsJson == "[]") {
                android.util.Log.w("TransactionRepository", "No transactions to back up")
                return false
            }

            // Save to external URI
            context.contentResolver.openOutputStream(uri)?.use { outputStream ->
                outputStream.write(transactionsJson.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }

            // Also save to internal storage for restore functionality
            saveToInternalStorage(transactionsJson)

            android.util.Log.d("TransactionRepository", "Backup created at URI: $uri and internal storage")
            return true
        } catch (e: java.io.IOException) {
            android.util.Log.e("TransactionRepository", "Backup to URI failed: ${e.message}", e)
            return false
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Backup to URI failed: ${e.message}", e)
            return false
        }
    }

    // Save to internal storage
    private fun saveToInternalStorage(transactionsJson: String): Boolean {
        return try {
            context.openFileOutput(BACKUP_FILENAME, Context.MODE_PRIVATE).use { outputStream ->
                outputStream.write(transactionsJson.toByteArray(Charsets.UTF_8))
                outputStream.flush()
            }
            android.util.Log.d("TransactionRepository", "Backup saved to internal storage")
            true
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Internal storage backup failed: ${e.message}", e)
            false
        }
    }

    // Restore from internal storage
    fun restoreFromInternalStorage(context: Context): Boolean {
        try {
            // Check if backup file exists
            val file = File(context.filesDir, BACKUP_FILENAME)
            if (!file.exists()) {
                android.util.Log.e("TransactionRepository", "Backup file not found in internal storage")
                return false
            }

            context.openFileInput(BACKUP_FILENAME).use { inputStream ->
                val transactionsJson = inputStream.bufferedReader().use { it.readText() }

                // Validate JSON before committing
                if (transactionsJson.isNullOrBlank() || transactionsJson == "[]") {
                    android.util.Log.e("TransactionRepository", "Backup file is empty or invalid")
                    return false
                }

                // Try to parse the JSON to ensure it's valid
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                try {
                    gson.fromJson<List<Map<String, Any>>>(transactionsJson, type)
                } catch (e: Exception) {
                    android.util.Log.e("TransactionRepository", "Invalid JSON in backup file: ${e.message}")
                    return false
                }

                // Save to SharedPreferences
                sharedPreferences.edit().putString(KEY_TRANSACTIONS, transactionsJson).apply()
                android.util.Log.d("TransactionRepository", "Restore from internal storage successful")
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Restore failed: ${e.message}", e)
            return false
        }
    }

    // Add option to restore from external URI
    fun restoreFromUri(context: Context, uri: android.net.Uri): Boolean {
        try {
            context.contentResolver.openInputStream(uri)?.use { inputStream ->
                val transactionsJson = inputStream.bufferedReader().use { it.readText() }

                // Validate JSON before committing
                if (transactionsJson.isNullOrBlank() || transactionsJson == "[]") {
                    return false
                }

                // Try to parse the JSON to ensure it's valid
                val type = object : TypeToken<List<Map<String, Any>>>() {}.type
                try {
                    gson.fromJson<List<Map<String, Any>>>(transactionsJson, type)
                } catch (e: Exception) {
                    return false
                }

                // Save to SharedPreferences
                sharedPreferences.edit().putString(KEY_TRANSACTIONS, transactionsJson).apply()

                // Also update internal backup
                saveToInternalStorage(transactionsJson)
            }
            return true
        } catch (e: Exception) {
            android.util.Log.e("TransactionRepository", "Restore from URI failed: ${e.message}", e)
            return false
        }
    }
}