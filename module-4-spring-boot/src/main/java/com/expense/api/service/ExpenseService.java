package com.expense.api.service;

import com.expense.api.model.Expense;
import org.springframework.stereotype.Service;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

@Service
public class ExpenseService {
    private List<Expense> expenses = new ArrayList<>();
    private AtomicInteger nextId = new AtomicInteger(1);

    public ExpenseService() {
        // Sample data
        expenses.add(new Expense(nextId.getAndIncrement(), "Coffee", 5.50, "Food"));
        expenses.add(new Expense(nextId.getAndIncrement(), "Movie", 12.00, "Entertainment"));
        expenses.add(new Expense(nextId.getAndIncrement(), "Uber", 24.50, "Transport"));
    }

    public List<Expense> getAllExpenses() {
        return expenses;
                }

    public Expense getExpenseById(int id) {
        return expenses.stream()
                .filter(e -> e.getId() == id)
                .findFirst()
                .orElse(null);
    }

    public Expense addExpense(Expense expense) {
        expense.setId(nextId.getAndIncrement());
        expenses.add(expense);
        return expense;
    }

    public Expense updateExpense(int id, Expense updatedExpense) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId() == id) {
                updatedExpense.setId(id);
                expenses.set(i, updatedExpense);
                return updatedExpense;
            }
        }
        return null;
    }

    public Expense deleteExpense(int id) {
        for (int i = 0; i < expenses.size(); i++) {
            if (expenses.get(i).getId() == id) {
                return expenses.remove(i);
            }
        }
        return null;
    }
}