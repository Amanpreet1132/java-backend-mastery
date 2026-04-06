public class Expense {
    private int id;
    private String name;
    private double amount;
    private String category;

    // No-arg constructor (useful for some libraries)
    public Expense() {}

    public Expense(int id, String name, double amount, String category) {
        this.id = id;
        this.name = name;
        this.amount = amount;
        this.category = category;
    }

    // Getters
    public int getId() { return id; }
    public String getName() { return name; }
    public double getAmount() { return amount; }
    public String getCategory() { return category; }

    // Setters
    public void setId(int id) { this.id = id; }
    public void setName(String name) { this.name = name; }
    public void setAmount(double amount) { this.amount = amount; }
    public void setCategory(String category) { this.category = category; }
}