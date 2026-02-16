package services;

import models.Expense;
import java.util.ArrayList;
import java.util.List;

public class ExpenseDatabase {

    // 1. The static instance (The "One and Only" copy)
    private static ExpenseDatabase instance;

    // 2. The actual data
    private List<Expense> expenses;

    // 3. PRIVATE Constructor: No one can say "new ExpenseDatabase()" except this class!
    private ExpenseDatabase() {
        expenses = new ArrayList<>();
    }

    // 4. Public access method (The "Doorway")
    public static ExpenseDatabase getInstance() {
        if (instance == null) {
            instance = new ExpenseDatabase();
        }
        return instance;
    }

    // Business Methods
    public void addExpense(Expense e) {
        expenses.add(e);
    }

    public List<Expense> getExpenses() {
        return expenses;
    }

    public double calculateTotalExpenses() {
        return expenses.stream()
                .mapToDouble(Expense::getAmount)
                .sum();
    }
}