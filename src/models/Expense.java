package models;

import interfaces.*;

public class Expense implements Exportable {
    // 1. Attributes (What an expense HAS)

    private String name;
    private double amount;
    private String category;
    private String date;
    // 2. A Constructor (How we CREATE a new expense)

    public Expense(String name, double amount, String category, String date) {
        this.name = name;
        this.amount = amount;
        this.category = category;
        this.date = date;
    }
    public void displayInfo() {
        System.out.println(date + '|' + category + ": " + name + " - $" + amount);
    }

    // GETTER: Allows someone to see the amount, but not change it
    public double getAmount() {
        return amount;
    }
    // SETTER: Allows someone to change the amount, but with a SAFETY CHECK
    public void setAmount(double newAmount) {
        if (newAmount < 0) {                     // Detect negative values
            throw new IllegalArgumentException("models.Expense cannot be negative!");
        }
        this.amount = newAmount;                  // Only set if it's valid (zero or positive)
    }


    // 3. A Method (What an expense can DO - e.g., describe itself)
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }
    @Override
    public String formatForReport() {
        return "REPORT_DATA: " + getName() + "," + getAmount();
    }

}