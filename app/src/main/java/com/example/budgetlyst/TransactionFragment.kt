package com.example.budgetlyst

import android.content.Intent
import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import com.example.budgetlyst.data.PreferencesManager
import com.example.budgetlyst.data.TransactionRepository
import com.example.budgetlyst.databinding.FragmentTransactionBinding
import com.example.budgetlyst.model.Transaction
import com.example.budgetlyst.ui.AddTransactionActivity
import com.example.budgetlyst.ui.DeleteTransactionActivity
import com.example.budgetlyst.ui.EditTransactionActivity
import com.example.budgetlyst.ui.adapters.TransactionsAdapter
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

class TransactionFragment : Fragment(), TransactionsAdapter.OnTransactionClickListener {
    private var _binding: FragmentTransactionBinding? = null
    private val binding get() = _binding!!
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var preferencesManager: PreferencesManager
    private lateinit var adapter: TransactionsAdapter
    private val calendar = Calendar.getInstance()
    private var currentFilter = "all"


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentTransactionBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        transactionRepository = TransactionRepository(requireContext())
        preferencesManager = PreferencesManager(requireContext())

        setupMonthSelector()
        setupFilterSpinner()
        setupTransactionsList()

        binding.fabAddTransaction.setOnClickListener {
            startActivity(Intent(requireContext(), AddTransactionActivity::class.java))
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun setupMonthSelector() {
        val dateFormat = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        binding.tvCurrentMonth.text = dateFormat.format(calendar.time)

        binding.btnPreviousMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, -1)
            updateTransactionsList()
            binding.tvCurrentMonth.text = dateFormat.format(calendar.time)
        }

        binding.btnNextMonth.setOnClickListener {
            calendar.add(Calendar.MONTH, 1)
            updateTransactionsList()
            binding.tvCurrentMonth.text = dateFormat.format(calendar.time)
        }
    }

    private fun setupFilterSpinner() {
        val filters = arrayOf("All", "Expenses", "Income")
        val spinnerAdapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_item, filters
        )
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        binding.spinnerFilter.adapter = spinnerAdapter

        binding.spinnerFilter.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                currentFilter = filters[position].lowercase()
                updateTransactionsList()
            }

            override fun onNothingSelected(parent: AdapterView<*>?) {}
        }
    }

    private fun setupTransactionsList() {
        adapter = TransactionsAdapter(getFilteredTransactions(), preferencesManager.getCurrency(), this)
        binding.recyclerTransactions.adapter = adapter
    }

    private fun getFilteredTransactions(): List<Transaction> {
        val month = calendar.get(Calendar.MONTH)
        val year = calendar.get(Calendar.YEAR)

        return when (currentFilter) {
            "expenses" -> transactionRepository.getExpensesForMonth(month, year)
            "income" -> transactionRepository.getIncomeForMonth(month, year)
            else -> transactionRepository.getTransactionsForMonth(month, year)
        }.sortedByDescending { it.date }
    }

    private fun updateTransactionsList() {
        adapter.updateTransactions(getFilteredTransactions())
    }

    override fun onTransactionClick(transaction: Transaction) {
        val intent = Intent(requireContext(), EditTransactionActivity::class.java).apply {
            putExtra("transaction_id", transaction.id)
            putExtra("transaction_title", transaction.title)
            putExtra("transaction_amount", transaction.amount)
            putExtra("transaction_category", transaction.category)
            putExtra("transaction_date", transaction.date.time)
            putExtra("transaction_is_expense", transaction.isExpense)
        }
        startActivity(intent)
    }

    override fun onTransactionLongClick(transaction: Transaction) {
        val intent = Intent(requireContext(), DeleteTransactionActivity::class.java).apply {
            putExtra("transaction_id", transaction.id)
            putExtra("transaction_title", transaction.title)
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updateTransactionsList()
    }
    companion object {
        /**
         * Use this factory method to create a new instance of
         * this fragment using the provided parameters.
         *
         * @param param1 Parameter 1.
         * @param param2 Parameter 2.
         * @return A new instance of fragment TransactionFragment.
         */
        // TODO: Rename and change types and number of parameters
        @JvmStatic
        fun newInstance(param1: String, param2: String) =
            TransactionFragment().apply {
                arguments = Bundle().apply {
                }
            }
    }
}