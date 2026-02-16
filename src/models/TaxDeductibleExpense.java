package models;

public class TaxDeductibleExpense extends Expense {
        private double taxRate;
        public TaxDeductibleExpense(String name, double amount, String category, String date, double taxRate) {
            super(name, amount, category, date);
            this.taxRate = taxRate;
        }
        public double calculateTax() {
            return getAmount() * taxRate;
        }


        @Override
        public void displayInfo() {

            System.out.print("Tax is: " + calculateTax() + " | ");
            super.displayInfo();
        }
    }
