package Android.Database;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Calendar;


/*import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;*/
import javax.sql.DataSource;
import android.util.Log;


public class ConectionDDBB {

    private static final String DB_URL = "jdbc:mysql://ubicua:1883/ubicua";
    private static final String USER = "ubicua";
    private static final String PASSWORD = "ubicua";

    public static Connection obtainConnection(boolean autoCommit) throws NullPointerException {
        Connection con = null;
        int attempts = 5;

        for (int i = 0; i < attempts; i++) {
            Log.i("Database", "Attempt " + i + " to connect to the database");

            try {
                // Load MySQL JDBC Driver
                Class.forName("com.mysql.cj.jdbc.Driver");

                // Obtain a connection
                con = DriverManager.getConnection(DB_URL, USER, PASSWORD);
                con.setAutoCommit(autoCommit);

                Log.i("Database", "Connection obtained on attempt: " + i);
                break; // Exit loop on success

            } catch (ClassNotFoundException e) {
                Log.e("Database", "MySQL JDBC Driver not found: " + e.getMessage());
                throw new NullPointerException("JDBC Driver is not available.");
            } catch (SQLException e) {
                Log.e("Database", "SQL Error: " + e.getSQLState() + "\n" + e.getMessage());
            }
        }

        if (con == null) {
            throw new NullPointerException("Failed to obtain SQL connection.");
        }

        return con;
    }

    public void closeTransaction(Connection con) {
        try {
            if (con != null) {
                con.commit();
                Log.i("Database", "Transaction closed");
            }
        } catch (SQLException ex) {
            Log.e("Database", "Error closing transaction: " + ex.getMessage());
        }
    }

    public void cancelTransaction(Connection con) {
        try {
            if (con != null) {
                con.rollback();
                Log.i("Database", "Transaction canceled");
            }
        } catch (SQLException ex) {
            Log.e("Database", "Error canceling transaction: " + ex.getMessage());
        }
    }

    public static void closeConnection(Connection con) {
        try {
            if (con != null) {
                con.close();
                Log.i("Database", "Connection closed");
            }
        } catch (SQLException e) {
            Log.e("Database", "Error closing connection: " + e.getMessage());
        }
    }

    public static PreparedStatement getStatement(Connection con, String sql) {
        PreparedStatement ps = null;
        try {
            if (con != null) {
                ps = con.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            }
        } catch (SQLException ex) {
            Log.w("Database", "Error creating PreparedStatement: " + ex.getMessage());
        }
        return ps;
    }

    public static PreparedStatement getDataBD(Connection con) {
        return getStatement(con, "SELECT * FROM UBICOMP.MEASUREMENT");
    }

    public static PreparedStatement setDataBD(Connection con) {
        return getStatement(con, "INSERT INTO UBICOMP.MEASUREMENT VALUES (?, ?)");
    }
}
