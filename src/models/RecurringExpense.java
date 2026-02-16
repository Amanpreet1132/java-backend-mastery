package models;

import interfaces.*;

public class RecurringExpense extends Expense implements Discountable {

        private String frequency; // e.g., "Monthly" or "Yearly"

        public RecurringExpense(double amount, String name, String category, String date, String frequency) {
            // "super" calls the Parent (models.Expense) constructor
            super(name, amount, category, date);
            this.frequency = frequency;
        }

        // This "Overrides" the parent method to add more detail
        @Override
        public void displayInfo() {
            System.out.print("[RECURRING - " + frequency + "] ");
            super.displayInfo(); // Calls the original displayInfo from models.Expense
        }

        @Override
        public void applyDiscount(double percentage){
            setAmount((getAmount() - ((percentage * getAmount()) / 100)));
        }
    }

