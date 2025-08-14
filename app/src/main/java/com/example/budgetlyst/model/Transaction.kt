package com.example.budgetlyst.model

import java.util.Date
import java.util.UUID

data class Transaction(
    val id: String = UUID.randomUUID().toString(),
    var title: String,
    var amount: Double,
    var category: String,
    var date: Date,
    var isExpense: Boolean = true
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "id" to id,
            "title" to title,
            "amount" to amount,
            "category" to category,
            "date" to date.time,
            "isExpense" to isExpense
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Transaction {
            // Handle the case where date might be a Double instead of Long
            val dateValue = when (val rawDate = map["date"]) {
                is Long -> rawDate
                is Double -> rawDate.toLong()
                else -> throw IllegalArgumentException("Date value is neither Long nor Double")
            }

            return Transaction(
                id = map["id"] as String,
                title = map["title"] as String,
                amount = (map["amount"] as Number).toDouble(),
                category = map["category"] as String,
                date = Date(dateValue),
                isExpense = map["isExpense"] as Boolean
            )
        }
    }
}
