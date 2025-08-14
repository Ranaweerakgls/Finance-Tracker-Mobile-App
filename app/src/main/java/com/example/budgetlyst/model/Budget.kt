package com.example.budgetlyst.model

import java.util.Calendar

data class Budget(
    var amount: Double,
    var month: Int = Calendar.getInstance().get(Calendar.MONTH),
    var year: Int = Calendar.getInstance().get(Calendar.YEAR)
) {
    fun toMap(): Map<String, Any> {
        return mapOf(
            "amount" to amount,
            "month" to month,
            "year" to year
        )
    }

    companion object {
        fun fromMap(map: Map<String, Any>): Budget {
            return Budget(
                amount = (map["amount"] as Number).toDouble(),
                month = (map["month"] as Number).toInt(),
                year = (map["year"] as Number).toInt()
            )
        }
    }
}
