package com.example.budgetlyst.model

data class Category(
    val name: String,
    val iconResId: Int
) {
    companion object {
        val DEFAULT_CATEGORIES = listOf(
            "Food", "Transport", "Bills", "Entertainment",
            "Shopping", "Health", "Education", "Housing", "Other"
        )
    }
}
