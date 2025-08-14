package com.example.budgetlyst

import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.core.app.ActivityCompat
import com.example.budgetlyst.data.PreferencesManager
import com.example.budgetlyst.data.TransactionRepository
import com.example.budgetlyst.databinding.FragmentHomeBinding
import com.example.budgetlyst.notification.NotificationManager
import com.example.budgetlyst.ui.AddTransactionActivity
import com.example.budgetlyst.ui.MainActivity
import com.example.budgetlyst.ui.adapters.RecentTransactionsAdapter
import com.github.mikephil.charting.data.PieData
import com.github.mikephil.charting.data.PieDataSet
import com.github.mikephil.charting.data.PieEntry
import com.github.mikephil.charting.utils.ColorTemplate
import com.google.android.material.bottomnavigation.BottomNavigationView
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class HomeFragment : Fragment() {
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var notificationManager: NotificationManager
    private lateinit var adapter: RecentTransactionsAdapter

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionRepository = TransactionRepository(requireContext())
        preferencesManager = PreferencesManager(requireContext())
        notificationManager = NotificationManager(requireContext())

        // Request notification permission
        requestNotificationPermission()

        notificationManager.scheduleDailyReminder()

        setupMonthDisplay()
        setupSummaryCards()
        setupCategoryChart()
        setupRecentTransactions()

        binding.btnAddTransaction.setOnClickListener {
            startActivity(Intent(requireContext(), AddTransactionActivity::class.java))
        }

        binding.btnPreviousMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateDashboard()
        }

        binding.btnNextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateDashboard()
        }
    }
    private fun requestNotificationPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ActivityCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    requireActivity(),
                    arrayOf(android.Manifest.permission.POST_NOTIFICATIONS),
                    NOTIFICATION_PERMISSION_REQUEST_CODE
                )
            }
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == NOTIFICATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, you can show notifications
                Toast.makeText(requireContext(), "Notification permission granted", Toast.LENGTH_SHORT).show()
            } else {
                // Permission denied, inform the user
                Toast.makeText(requireContext(), "Notification permission denied. You won't receive budget alerts.", Toast.LENGTH_LONG).show()
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        return binding.root
    }
    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMonthDisplay() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvCurrentMonth.text = dateFormat.format(calendar.time)
    }

    private fun setupSummaryCards() {
        val currency = preferencesManager.getCurrency()
        val totalIncome = transactionRepository.getTotalIncomeForMonth(
            calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR)
        )
        val totalExpenses = transactionRepository.getTotalExpensesForMonth(
            calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR)
        )
        val balance = totalIncome - totalExpenses

        binding.tvIncomeAmount.text = String.format("%s %.2f", currency, totalIncome)
        binding.tvExpenseAmount.text = String.format("%s %.2f", currency, totalExpenses)
        binding.tvBalanceAmount.text = String.format("%s %.2f", currency, balance)

        // Budget progress
        val budget = preferencesManager.getBudget()
        if (budget.month == calendar.get(Calendar.MONTH) &&
            budget.year == calendar.get(Calendar.YEAR) &&
            budget.amount > 0) {

            val percentage = (totalExpenses / budget.amount) * 100
            binding.progressBudget.progress = percentage.toInt().coerceAtMost(100)
            binding.tvBudgetStatus.text = String.format(
                "%.1f%% of %s %.2f", percentage, currency, budget.amount
            )

            if (percentage >= 100) {
                binding.tvBudgetStatus.setTextColor(Color.RED)
            } else if (percentage >= 80) {
                binding.tvBudgetStatus.setTextColor(Color.parseColor("#FFA500")) // Orange
            } else {
                binding.tvBudgetStatus.setTextColor(Color.GREEN)
            }
        } else {
            binding.progressBudget.progress = 0
            binding.tvBudgetStatus.text = getString(R.string.no_budget_set)
            binding.tvBudgetStatus.setTextColor(Color.GRAY)
        }
    }

    private fun setupCategoryChart() {
        val expensesByCategory = transactionRepository.getExpensesByCategory(
            calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR)
        )

        if (expensesByCategory.isEmpty()) {
            binding.pieChart.setNoDataText(getString(R.string.no_expenses_this_month))
            binding.pieChart.invalidate()
            return
        }

        val entries = ArrayList<PieEntry>()
        val colors = ArrayList<Int>()

        expensesByCategory.forEach { (category, amount) ->
            entries.add(PieEntry(amount.toFloat(), category))
            colors.add(ColorTemplate.MATERIAL_COLORS[entries.size % ColorTemplate.MATERIAL_COLORS.size])
        }

        val dataSet = PieDataSet(entries, "Categories")
        dataSet.colors = colors
        dataSet.valueTextSize = 12f
        dataSet.valueTextColor = Color.WHITE

        val pieData = PieData(dataSet)
        binding.pieChart.data = pieData
        binding.pieChart.description.isEnabled = false
        binding.pieChart.centerText = getString(R.string.expenses_by_category)
        binding.pieChart.setCenterTextSize(14f)
        binding.pieChart.legend.textSize = 12f
        binding.pieChart.animateY(1000)
        binding.pieChart.invalidate()
    }

    private fun setupRecentTransactions() {
        val transactions = transactionRepository.getTransactionsForMonth(
            calendar.get(Calendar.MONTH), calendar.get(Calendar.YEAR)
        ).sortedByDescending { it.date }.take(5)

        adapter = RecentTransactionsAdapter(transactions, preferencesManager.getCurrency())
        binding.recyclerRecentTransactions.adapter = adapter

        binding.tvViewAllTransactions.setOnClickListener {
            (activity as? MainActivity)?.let {
                it.loadFragment(TransactionFragment())
                it.findViewById<BottomNavigationView>(R.id.bottom_navigation)
                    .selectedItemId = R.id.nav_transactions
            }
        }
    }

    private fun updateDashboard() {
        setupMonthDisplay()
        setupSummaryCards()
        setupCategoryChart()
        setupRecentTransactions()
    }

    override fun onResume() {
        super.onResume()

        // Debug logging
        val budget = preferencesManager.getBudget()
        val calendar = Calendar.getInstance()
        val currentMonth = calendar.get(Calendar.MONTH)
        val currentYear = calendar.get(Calendar.YEAR)

        if (budget.month == currentMonth && budget.year == currentYear && budget.amount > 0) {
            val totalExpenses = transactionRepository.getTotalExpensesForMonth(currentMonth, currentYear)
            val budgetPercentage = (totalExpenses / budget.amount) * 100

            Log.d("MainActivity", "Budget: $totalExpenses / ${budget.amount} = $budgetPercentage%")
            Log.d("MainActivity", "Notifications enabled: ${preferencesManager.isNotificationEnabled()}")
        }

        updateDashboard()
        // Check budget and notify
        notificationManager.checkBudgetAndNotify()
    }


    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            HomeFragment().apply {
                arguments = Bundle().apply {
                }
            }
        private const val NOTIFICATION_PERMISSION_REQUEST_CODE = 123
    }
}