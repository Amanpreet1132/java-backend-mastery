package main;

import models.*;
import services.*;

public class Main {
    public static void main(String[] args) {
        // We use the "new" keyword to build an object from our blueprint
        Expense e1 = new Expense("Starbucks Coffee", 10, "Food", "2023-10-27");
        Expense e2 = new Expense("Movie Ticket", 12.00, "Entertainment", "2023-10-27");
        Expense e3 = new Expense("Gym Membership",30, "health", "2023-10-27");
        RecurringExpense sub = new RecurringExpense(15.99, "Netflix", "Entertainment", "2023-10-27", "Monthly");
        TaxDeductibleExpense e4 = new TaxDeductibleExpense("Business Laptop", 350, "Device" , "2023-10-28", .15 );



        ExpenseDatabase.getInstance().addExpense(e1);
        ExpenseDatabase.getInstance().addExpense(e2);
        ExpenseDatabase.getInstance().addExpense(e3);
        ExpenseDatabase.getInstance().addExpense(sub);
        ExpenseDatabase.getInstance().addExpense(e4);

        // 1. Calculate Total Sum using a Stream
        double streamTotal = ExpenseDatabase.getInstance().calculateTotalExpenses();
        System.out.println("Stream Total: $" + streamTotal);

        System.out.println("Stream Total: $" + streamTotal);

        // 2. Filter: Find only expenses > 20
        System.out.println("--- Expensive Items (Stream) ---");
        ExpenseDatabase.getInstance().getExpenses().stream()
                .filter(e -> e.getAmount() > 20)
                .forEach(e -> e.displayInfo());

        ExpenseDatabase.getInstance().getExpenses().stream()
                .filter(e -> e.getCategory().equalsIgnoreCase("Entertainment"))
                .forEach(e -> e.displayInfo());

        long count = ExpenseDatabase.getInstance().getExpenses().stream().count();
        System.out.println("Total expenses count is : " + count);

        // SIMULATION: User types "100abc" instead of "100"
        String userInput = "100fbgfhsghabc";
        createExpenseFromInput(userInput);



// NEW: Attempt to set a negative amount – this will throw an exception
        try {
            e1.setAmount(-50.0);
        } catch (IllegalArgumentException e) {
            System.out.println("Security Alert: Attempted negative transaction.");
            System.out.println(e.getMessage());

        }

        System.out.println("App finished successfully."); // This will never print if we crash!




    }

    public static void createExpenseFromInput(String input) {
        try {
            // 1. Attempt the dangerous code
            int amount = Integer.parseInt(input);
            System.out.println("models.Expense created with amount: " + amount);

        } catch (NumberFormatException e) {
            // 2. Catch the specific error (bad number format)
            System.out.println("ERROR: Invalid number format! User entered: " + input);
            // You could handle it by setting a default value, logging, etc.

        } catch (Exception e) {
            // 3. (Optional) Catch ANY other unexpected error
            System.out.println("Something else went wrong: " + e.getMessage());
        }
    }
}