import java.sql.DriverManager;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.*; 
public class OracleJDBC {
 
	//public static void main(String[] argv) {
public Connection connect = null;
		//System.out.println("-------- Oracle JDBC Connection Testing ------");
public OracleJDBC(){
		try {
 
			Class.forName("oracle.jdbc.driver.OracleDriver");
 
		} catch (ClassNotFoundException e) {
 
			System.out.println("Where is your Oracle JDBC Driver?");
			e.printStackTrace();
			//return;
		}
		System.out.println("Oracle JDBC Driver Registered!");
		try {
			  connect = DriverManager.getConnection(
                         "jdbc:oracle:thin:@localhost:1521:demo","system","oracle12c");
		} catch (SQLException e) {
 
			System.out.println("Connection Failed! Check output console");
			e.printStackTrace();
			//return;
 
		}
   
		if (connect != null) {
			System.out.println("You made it, take control your database now!");
		} else {
			System.out.println("Failed to make connection!");
		}
	}
        
public ResultSet runSql(String sql) throws SQLException {
		Statement sta = connect.createStatement();
		return sta.executeQuery(sql);
	}

 
    protected void finalize() throws Throwable {
    try {
        if (connect != null || !connect.isClosed()) 
        {
            connect.close();
        }
    }
    finally {
        super.finalize();
    }
	}
}