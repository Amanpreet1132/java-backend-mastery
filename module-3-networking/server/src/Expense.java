public class Expense {
    private int id;
    private String name;
    private double amount;
    private String category;

    public Expense(int id, String name, double amount, String category) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.category = category;
    }

    // Getters (we'll need these to build JSON)
    public int getId() { return id; }
    public String getName() { return name; }
    public double getAmount() { return amount; }
    public String getCategory() { return category; }
}