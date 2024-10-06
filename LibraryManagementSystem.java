import java.sql.*;
import java.util.Scanner;

public class LibraryManagementSystem {
    private static final String URL = "jdbc:mysql://localhost:3306/library_management";
    private static final String USERNAME = "root";  // Replace with your MySQL username
    private static final String PASSWORD = "";      // Replace with your MySQL password

    private Connection connection;

    public LibraryManagementSystem() throws SQLException {
        connection = DriverManager.getConnection(URL, USERNAME, PASSWORD);
    }

    public void close() throws SQLException {
        if (connection != null) {
            connection.close();
        }
    }

    public void adminMenu() throws SQLException {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.println("\nAdmin Menu:");
            System.out.println("1. Add Book");
            System.out.println("2. Remove Book");
            System.out.println("3. View All Books");
            System.out.println("4. Exit");
            System.out.print("Enter choice: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    addBook(scanner);
                    break;
                case 2:
                    removeBook(scanner);
                    break;
                case 3:
                    viewAllBooks();
                    break;
                case 4:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    public void userMenu(int userId) throws SQLException {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;
        while (running) {
            System.out.println("\nUser Menu:");
            System.out.println("1. Search Book");
            System.out.println("2. Borrow Book");
            System.out.println("3. Return Book");
            System.out.println("4. Exit");
            System.out.print("Enter choice: ");
            int choice = scanner.nextInt();
            scanner.nextLine();

            switch (choice) {
                case 1:
                    searchBook(scanner);
                    break;
                case 2:
                    borrowBook(scanner, userId);
                    break;
                case 3:
                    returnBook(scanner, userId);
                    break;
                case 4:
                    running = false;
                    break;
                default:
                    System.out.println("Invalid choice. Try again.");
            }
        }
    }

    private void addBook(Scanner scanner) throws SQLException {
        System.out.print("Enter book title: ");
        String title = scanner.nextLine();
        System.out.print("Enter book author: ");
        String author = scanner.nextLine();

        String query = "INSERT INTO books (title, author) VALUES (?, ?)";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, title);
            stmt.setString(2, author);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Book added successfully.");
            } else {
                System.out.println("Error adding book.");
            }
        }
    }

    private void removeBook(Scanner scanner) throws SQLException {
        System.out.print("Enter book ID to remove: ");
        int bookId = scanner.nextInt();

        String query = "DELETE FROM books WHERE id = ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, bookId);
            int rowsAffected = stmt.executeUpdate();
            if (rowsAffected > 0) {
                System.out.println("Book removed successfully.");
            } else {
                System.out.println("Error removing book.");
            }
        }
    }

    private void viewAllBooks() throws SQLException {
        String query = "SELECT * FROM books";
        try (Statement stmt = connection.createStatement();
             ResultSet rs = stmt.executeQuery(query)) {
            System.out.println("\nAvailable Books:");
            while (rs.next()) {
                System.out.println("ID: " + rs.getInt("id") +
                                   ", Title: " + rs.getString("title") +
                                   ", Author: " + rs.getString("author") +
                                   ", Available: " + (rs.getBoolean("available") ? "Yes" : "No"));
            }
        }
    }

    private void searchBook(Scanner scanner) throws SQLException {
        System.out.print("Enter book title or author to search: ");
        String searchQuery = scanner.nextLine();

        String query = "SELECT * FROM books WHERE title LIKE ? OR author LIKE ?";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setString(1, "%" + searchQuery + "%");
            stmt.setString(2, "%" + searchQuery + "%");

            try (ResultSet rs = stmt.executeQuery()) {
                System.out.println("\nSearch Results:");
                while (rs.next()) {
                    System.out.println("ID: " + rs.getInt("id") +
                                       ", Title: " + rs.getString("title") +
                                       ", Author: " + rs.getString("author") +
                                       ", Available: " + (rs.getBoolean("available") ? "Yes" : "No"));
                }
            }
        }
    }

    private void borrowBook(Scanner scanner, int userId) throws SQLException {
        System.out.print("Enter the book ID to borrow: ");
        int bookId = scanner.nextInt();
        scanner.nextLine();

        String checkAvailabilityQuery = "SELECT available FROM books WHERE id = ?";
        try (PreparedStatement checkStmt = connection.prepareStatement(checkAvailabilityQuery)) {
            checkStmt.setInt(1, bookId);

            try (ResultSet rs = checkStmt.executeQuery()) {
                if (rs.next() && rs.getBoolean("available")) {
                    String borrowQuery = "INSERT INTO borrow_records (user_id, book_id, borrow_date) VALUES (?, ?, CURDATE())";
                    String updateBookQuery = "UPDATE books SET available = FALSE WHERE id = ?";

                    try (PreparedStatement borrowStmt = connection.prepareStatement(borrowQuery);
                         PreparedStatement updateStmt = connection.prepareStatement(updateBookQuery)) {
                        borrowStmt.setInt(1, userId);
                        borrowStmt.setInt(2, bookId);
                        borrowStmt.executeUpdate();

                        updateStmt.setInt(1, bookId);
                        updateStmt.executeUpdate();

                        System.out.println("Book borrowed successfully.");
                    }
                } else {
                    System.out.println("Book is not available.");
                }
            }
        }
    }

    private void returnBook(Scanner scanner, int userId) throws SQLException {
        System.out.print("Enter the book ID to return: ");
        int bookId = scanner.nextInt();
        scanner.nextLine();

        String query = "SELECT borrow_date FROM borrow_records WHERE user_id = ? AND book_id = ? AND return_date IS NULL";
        try (PreparedStatement stmt = connection.prepareStatement(query)) {
            stmt.setInt(1, userId);
            stmt.setInt(2, bookId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Date borrowDate = rs.getDate("borrow_date");
                    long daysBorrowed = (System.currentTimeMillis() - borrowDate.getTime()) / (1000 * 60 * 60 * 24);

                    double lateFee = daysBorrowed > 14 ? (daysBorrowed - 14) * 2.0 : 0.0;

                    String returnQuery = "UPDATE borrow_records SET return_date = CURDATE() WHERE user_id = ? AND book_id = ?";
                    String updateBookQuery = "UPDATE books SET available = TRUE WHERE id = ?";

                    try (PreparedStatement returnStmt = connection.prepareStatement(returnQuery);
                         PreparedStatement updateStmt = connection.prepareStatement(updateBookQuery)) {
                        returnStmt.setInt(1, userId);
                        returnStmt.setInt(2, bookId);
                        returnStmt.executeUpdate();

                        updateStmt.setInt(1, bookId);
                        updateStmt.executeUpdate();

                        System.out.println("Book returned successfully. Late fee: $" + lateFee);
                    }
                } else {
                    System.out.println("No active borrowing record found for this book.");
                }
            }
        }
    }

    public static void main(String[] args) {
        try {
            LibraryManagementSystem system = new LibraryManagementSystem();
            Scanner scanner = new Scanner(System.in);

            System.out.print("Enter user ID: ");
            int userId = scanner.nextInt();
            scanner.nextLine();

            String roleQuery = "SELECT role FROM users WHERE id = ?";
            try (PreparedStatement stmt = system.connection.prepareStatement(roleQuery)) {
                stmt.setInt(1, userId);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        String role = rs.getString("role");
                        if ("admin".equals(role)) {
                            system.adminMenu();
                        } else if ("user".equals(role)) {
                            system.userMenu(userId);
                        } else {
                            System.out.println("Invalid role.");
                        }
                    } else {
                        System.out.println("User not found.");
                    }
                }
            }

            system.close();
        } catch (SQLException e) {
            e.printStackTrace();
        }
    }
}
