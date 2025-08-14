package com.example.budgetlyst

import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.example.budgetlyst.data.PreferencesManager
import com.example.budgetlyst.data.TransactionRepository
import com.example.budgetlyst.databinding.FragmentBudgetBinding
import com.example.budgetlyst.ui.SetBudgetActivity
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class BudgetFragment : Fragment() {
    private var _binding: FragmentBudgetBinding? = null
    private val binding get() = _binding!!
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var preferencesManager: PreferencesManager

    private val calendar = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentBudgetBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionRepository = TransactionRepository(requireContext())
        preferencesManager = PreferencesManager(requireContext())

        setupMonthSelector()
        setupBudgetDisplay()
        setupBudgetChart()

        binding.btnSetBudget.setOnClickListener {
            val intent = Intent(requireContext(), SetBudgetActivity::class.java).apply {
                putExtra("month", calendar.get(Calendar.MONTH))
                putExtra("year", calendar.get(Calendar.YEAR))
            }
            startActivity(intent)
        }
    }

    private fun setupMonthSelector() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvCurrentMonth.text = dateFormat.format(calendar.time)

        binding.btnPreviousMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateBudgetDisplay()
            binding.tvCurrentMonth.text = dateFormat.format(calendar.time)
        }

        binding.btnNextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateBudgetDisplay()
            binding.tvCurrentMonth.text = dateFormat.format(calendar.time)
        }
    }

    private fun setupBudgetDisplay() {
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        val budget = preferencesManager.getBudget()
        val currency = preferencesManager.getCurrency()

        if (budget.month == month && budget.year == year && budget.amount > 0) {
            binding.tvNoBudget.visibility = android.view.View.GONE
            binding.cardBudgetInfo.visibility = android.view.View.VISIBLE

            val totalExpenses = transactionRepository.getTotalExpensesForMonth(month, year)
            val remaining = budget.amount - totalExpenses
            val percentage = (totalExpenses / budget.amount) * 100

            binding.tvBudgetAmount.text = String.format("%s %.2f", currency, budget.amount)
            binding.tvExpensesAmount.text = String.format("%s %.2f", currency, totalExpenses)
            binding.tvRemainingAmount.text = String.format("%s %.2f", currency, remaining)

            binding.progressBudget.progress = percentage.toInt().coerceAtMost(100)
            binding.tvBudgetPercentage.text = String.format("%.1f%%", percentage)

            if (percentage >= 100) {
                binding.tvBudgetStatus.text = getString(R.string.budget_exceeded)
                binding.tvBudgetStatus.setTextColor(Color.RED)
                binding.tvRemainingAmount.setTextColor(Color.RED)
            } else if (percentage >= 80) {
                binding.tvBudgetStatus.text = getString(R.string.budget_warning)
                binding.tvBudgetStatus.setTextColor(Color.parseColor("#FFA500")) // Orange
                binding.tvRemainingAmount.setTextColor(Color.parseColor("#FFA500"))
            } else {
                binding.tvBudgetStatus.text = getString(R.string.budget_good)
                binding.tvBudgetStatus.setTextColor(Color.GREEN)
                binding.tvRemainingAmount.setTextColor(Color.GREEN)
            }
        } else {
            binding.tvNoBudget.visibility = android.view.View.VISIBLE
            binding.cardBudgetInfo.visibility = android.view.View.GONE
        }
    }

    private fun setupBudgetChart() {
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)
        val expensesByCategory = transactionRepository.getExpensesByCategory(month, year)

        if (expensesByCategory.isEmpty()) {
            binding.barChart.setNoDataText(getString(R.string.no_expenses_this_month))
            binding.barChart.invalidate()
            return
        }

        val entries = ArrayList<BarEntry>()
        val labels = ArrayList<String>()

        expensesByCategory.entries.forEachIndexed { index, entry ->
            entries.add(BarEntry(index.toFloat(), entry.value.toFloat()))
            labels.add(entry.key)
        }

        val dataSet = BarDataSet(entries, "Expenses by Category")
        dataSet.colors = listOf(
            Color.parseColor("#4CAF50"),
            Color.parseColor("#2196F3"),
            Color.parseColor("#FFC107"),
            Color.parseColor("#E91E63"),
            Color.parseColor("#9C27B0"),
            Color.parseColor("#FF5722"),
            Color.parseColor("#795548"),
            Color.parseColor("#607D8B")
        )
        dataSet.valueTextSize = 12f

        val barData = BarData(dataSet)
        binding.barChart.data = barData
        binding.barChart.description.isEnabled = false
        binding.barChart.xAxis.valueFormatter = IndexAxisValueFormatter(labels)
        binding.barChart.xAxis.granularity = 1f
        binding.barChart.xAxis.labelRotationAngle = 45f
        binding.barChart.animateY(1000)
        binding.barChart.invalidate()
    }

    private fun updateBudgetDisplay() {
        setupBudgetDisplay()
        setupBudgetChart()
    }

    override fun onResume() {
        super.onResume()
        updateBudgetDisplay()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            BudgetFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}