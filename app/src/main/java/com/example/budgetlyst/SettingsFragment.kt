package com.example.budgetlyst

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity.RESULT_OK
import androidx.appcompat.app.AppCompatDelegate
import com.example.budgetlyst.data.PreferencesManager
import com.example.budgetlyst.data.TransactionRepository
import com.example.budgetlyst.databinding.FragmentSettingsBinding
import com.example.budgetlyst.notification.NotificationManager

class SettingsFragment : Fragment() {
    private var _binding: FragmentSettingsBinding? = null
    private val binding get() = _binding!!
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var notificationManager: NotificationManager


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentSettingsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        preferencesManager = PreferencesManager(requireContext())
        transactionRepository = TransactionRepository(requireContext())
        notificationManager = NotificationManager(requireContext())

        setupCurrencySpinner()
        setupNotificationSettings()
        setupBackupButtons()
        setupThemeSwitch()

    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
    private fun setupCurrencySpinner() {
        val currencies = arrayOf("USD", "EUR", "GBP", "JPY", "CAD", "AUD", "INR", "CNY")
        val adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, currencies
        )
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerCurrency.adapter = adapter

        val currentCurrency = preferencesManager.getCurrency()
        val position = currencies.indexOf(currentCurrency)
        if (position >= 0) {
            binding.spinnerCurrency.setSelection(position)
        }

        binding.spinnerCurrency.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val selectedCurrency = currencies[position]
                if (selectedCurrency != currentCurrency) {
                    preferencesManager.setCurrency(selectedCurrency)
                    Toast.makeText(
                        requireContext(),
                        getString(R.string.currency_updated),
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupNotificationSettings() {
        binding.switchBudgetAlerts.isChecked = preferencesManager.isNotificationEnabled()
        binding.switchDailyReminders.isChecked = preferencesManager.isReminderEnabled()

        binding.switchBudgetAlerts.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setNotificationEnabled(isChecked)
        }

        binding.switchDailyReminders.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setReminderEnabled(isChecked)
            if (isChecked) {
                notificationManager.scheduleDailyReminder()
            } else {
                notificationManager.cancelDailyReminder()
            }
        }
    }

    private fun setupThemeSwitch() {
        val isDarkMode = preferencesManager.isDarkModeEnabled()
        binding.switchDarkMode.isChecked = isDarkMode

        binding.switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            preferencesManager.setDarkModeEnabled(isChecked)

            val mode = if (isChecked)
                AppCompatDelegate.MODE_NIGHT_YES
            else
                AppCompatDelegate.MODE_NIGHT_NO

            AppCompatDelegate.setDefaultNightMode(mode)
        }
    }


    private fun setupBackupButtons() {
        binding.btnBackupData.setOnClickListener {
            // Check if there are transactions to backup
            if (transactionRepository.getAllTransactions().isEmpty()) {
                Toast.makeText(requireContext(), "No transactions to back up", Toast.LENGTH_SHORT).show()
                return@setOnClickListener
            }

            val intent = Intent(Intent.ACTION_CREATE_DOCUMENT).apply {
                addCategory(Intent.CATEGORY_OPENABLE)
                type = "application/json"
                putExtra(Intent.EXTRA_TITLE, "transactions_backup.json")
            }
            startActivityForResult(intent, REQUEST_BACKUP)
        }

        binding.btnRestoreData.setOnClickListener {
            // Show options dialog
            AlertDialog.Builder(requireContext())
                .setTitle("Restore Data")
                .setMessage("Choose restore source")
                .setPositiveButton("From Last Backup") { _, _ ->
                    restoreFromLastBackup()
                }
                .setNegativeButton("From File") { _, _ ->
                    val intent = Intent(Intent.ACTION_OPEN_DOCUMENT).apply {
                        addCategory(Intent.CATEGORY_OPENABLE)
                        type = "application/json"
                    }
                    startActivityForResult(intent, REQUEST_RESTORE)
                }
                .setNeutralButton("Cancel", null)
                .show()
        }
    }

    private fun restoreFromLastBackup() {
        if (transactionRepository.restoreFromInternalStorage(requireContext())) {
            Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(requireContext(), "No backup found to restore", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (resultCode != RESULT_OK || data?.data == null) return

        when (requestCode) {
            REQUEST_BACKUP -> {
                data.data?.let { uri ->
                    if (transactionRepository.backupToUri(requireContext(), uri)) {
                        Toast.makeText(requireContext(), getString(R.string.backup_success), Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(requireContext(), getString(R.string.backup_failed), Toast.LENGTH_SHORT).show()
                    }
                }
            }
            REQUEST_RESTORE -> {
                data.data?.let { uri ->
                    AlertDialog.Builder(requireContext())
                        .setTitle("Warning")
                        .setMessage("This will replace all existing transactions. Continue?")
                        .setPositiveButton("Yes") { _, _ ->
                            if (transactionRepository.restoreFromUri(requireContext(), uri)) {
                                Toast.makeText(requireContext(), getString(R.string.restore_success), Toast.LENGTH_SHORT).show()
                            } else {
                                Toast.makeText(requireContext(), getString(R.string.restore_failed), Toast.LENGTH_SHORT).show()
                            }
                        }
                        .setNegativeButton("No", null)
                        .show()
                }
            }
        }
    }

    companion object {
        private const val REQUEST_BACKUP = 100
        private const val REQUEST_RESTORE = 101
    }
}