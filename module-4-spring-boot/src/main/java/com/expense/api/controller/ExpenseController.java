package com.expense.api.controller;

import com.expense.api.model.Expense;
import com.expense.api.service.ExpenseService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/expenses")
public class ExpenseController {

    @Autowired
    private ExpenseService expenseService;

    // GET /expenses?category=Food
    @GetMapping
    public List<Expense> getAllExpenses(@RequestParam(required = false) String category) {
        if (category == null) {
            return expenseService.getAllExpenses();
        }
        // Filter using stream (functional approach)
        return expenseService.getAllExpenses().stream()
                .filter(e -> category.equals(e.getCategory()))
                .toList();
    }

    // GET /expenses/5
    @GetMapping("/{id}")
    public ResponseEntity<Expense> getExpenseById(@PathVariable int id) {
        Expense expense = expenseService.getExpenseById(id);
        if (expense == null) {
            return ResponseEntity.notFound().build(); // returns 404
        }
        return ResponseEntity.ok(expense); // returns 200 with the expense
    }

    // POST /expenses with JSON body
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED) // sets status 201
    public Expense createExpense(@RequestBody Expense expense) {
        return expenseService.addExpense(expense);
    }

    // PUT /expenses/5 with JSON body
    @PutMapping("/{id}")
    public ResponseEntity<Expense> updateExpense(@PathVariable int id, @RequestBody Expense expense) {
        Expense updated = expenseService.updateExpense(id, expense);
        if (updated == null) {
            return ResponseEntity.notFound().build(); // 404 if not found
        }
        return ResponseEntity.ok(updated); // 200 with updated expense
    }

    // DELETE /expenses/5
    @DeleteMapping("/{id}")
    public ResponseEntity<Expense> deleteExpense(@PathVariable int id) {
        Expense deleted = expenseService.deleteExpense(id);
        if (deleted == null) {
            return ResponseEntity.notFound().build(); // 404
        }
        return ResponseEntity.ok(deleted); // 200 with deleted expense
    }
}