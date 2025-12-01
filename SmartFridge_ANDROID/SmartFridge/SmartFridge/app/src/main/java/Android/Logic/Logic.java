package Android.Logic;

import Android.db.ConnectionDB;
import Android.Database.ConectionDDBB;
import java.util.ArrayList;
import java.util.Date;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.PreparedStatement;
import java.sql.ResultSet;

public class Logic 
{
	public static ArrayList<Measurement> getDataFromDB()
	{
		ArrayList<Measurement> values = new ArrayList<Measurement>();
		
		ConectionDDBB conector = new ConectionDDBB();
		Connection con = null;
		try
		{
			con = conector.obtainConnection(true);
			Logs.info("Database: ","Database Connected");
			
			PreparedStatement ps = ConectionDDBB.getDataBD(con);
			Logs.info("Database: ","Query=>" + ps.toString());
			ResultSet rs = ps.executeQuery();
			while (rs.next())
			{
				Measurement measure = new Measurement();
				measure.setValue(rs.getInt("VALUE"));
				measure.setDate(rs.getTimestamp("DATE"));
				values.add(measure);
			}	
		} catch (SQLException e)
		{
			Logs.error("Error: ", e.toString());
			values = new ArrayList<Measurement>();
		} catch (NullPointerException e)
		{
			Logs.error("Error: ", e.toString());
			values = new ArrayList<Measurement>();
		} catch (Exception e)
		{
			Logs.error("Error: ", e.toString());
			values = new ArrayList<Measurement>();
		}
		conector.closeConnection(con);
		return values;
	}

	public static ArrayList<Measurement> setDataToDB(int value)
	{
		ArrayList<Measurement> values = new ArrayList<Measurement>();
		
		ConnectionDB conector = new ConnectionDB();
		Connection con = null;
		try
		{
			con = conector.obtainConnection(true);
			Logs.info("Database: ","Database Connected");

			PreparedStatement ps = ConectionDDBB.setDataBD(con);
			ps.setInt(1, value);
			ps.setTimestamp(2, new Timestamp((new Date()).getTime()));
			Logs.info("Database:", "Query=>" + ps.toString());
			ps.executeUpdate();
		} catch (SQLException e)
		{
			Logs.error("Error: " ,e.toString());
			values = new ArrayList<Measurement>();
		} catch (NullPointerException e)
		{
			Logs.error("Error: " ,e.toString());
			values = new ArrayList<Measurement>();
		} catch (Exception e)
		{
			Logs.error("Error: " ,e.toString());
			values = new ArrayList<Measurement>();
		}
		conector.closeConnection(con);
		return values;
	}
	
	
}
