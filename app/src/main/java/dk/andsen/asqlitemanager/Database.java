/**
 * Part of aSQLiteManager (http://sourceforge.net/projects/asqlitemanager/)
 * a Android SQLite Manager by andsen (http://sourceforge.net/users/andsen)
 *
 *	This class contains all all database functions
 *
 * @author andsen
 *
 */
package dk.andsen.asqlitemanager;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import android.app.Dialog;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;
import android.os.Handler;
import android.os.Message;
import android.widget.ProgressBar;
import android.widget.TextView;
import dk.andsen.RecordEditor.types.TableField;
import dk.andsen.csv.CSVUtils;
import dk.andsen.types.AField;
import dk.andsen.types.AField.FieldType;
import dk.andsen.types.Field;
import dk.andsen.types.FieldDescr;
import dk.andsen.types.ForeignKeyHolder;
import dk.andsen.types.QueryResult;
import dk.andsen.types.Record;
import dk.andsen.types.ViewUpdateable;
import dk.andsen.utils.SQLTools;
import dk.andsen.utils.Utils;
/**
 * @author Andsen
 *
 */
public class Database {
	public boolean isDatabase = false;
	public static SQLiteDatabase _db = null;
	public String _dbPath;
	private Context _cont;
	private String nl = "\n"; 
	private ProgressBar myProgressBar;
	private int myProgress = 0;
	private TextView progressTitle;
	private TextView progressTable;
	private String progressTitleText = "";
	private String progressTableText = "";
	private Dialog pd;
	private Handler theHandle;	
	private boolean _logging = false;
	
	//TODO add Context to all call to be able to show error messages generated here 
	/**
	 * Open a existing database at the given path
	 * @param dbPath Path to the database
	 */
	public Database(String dbPath, Context cont) {
		_dbPath = dbPath;
		_logging = Prefs.getLogging(cont);
		try {
			if (testDBFile(dbPath)) {
				// Here we know it is a SQLite 3 file
				Utils.logD("Trying to open (RW): " + dbPath, _logging);
				//_db = DBViewer._db;
				_db = SQLiteDatabase.openDatabase(dbPath, null, SQLiteDatabase.OPEN_READWRITE);
				_cont = cont;
				isDatabase = true;
			}
		} catch (Exception e) {
			Utils.logE("Trying to open Exception: " + e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
			// It is not a database
			isDatabase = false;
		}
	}
	
	/**
	 * Test if a file is a SQLite database
	 * @param dbPath path to the database file
	 * @return true if it is a SQLite file
	 */
	private boolean testDBFile(String dbPath) {
		// File must start with the following 16 bytes
		// 0x53 0x51 0x4c 0x69 0x74 0x65 0x20 0x66 0x6f 0x72 0x6d 0x61 0x74 0x20 0x33 0x00
		// to be a SQLite 3 database
		File backupFile = new File(dbPath);
		FileReader f = null;
		if (backupFile.canRead()) {
			try {
				f = new FileReader(backupFile);
				char buffer[] = new char[16];
				f.read(buffer, 0, 16);
				if (buffer[0] == 0x53 && 
						buffer[1] == 0x51 &&
						buffer[2] == 0x4c &&
						buffer[3] == 0x69 &&
						buffer[4] == 0x74 &&
						buffer[5] == 0x65 &&
						buffer[6] == 0x20 &&
						buffer[7] == 0x66 &&
						buffer[8] == 0x6f &&
						buffer[9] == 0x72 &&
						buffer[10] == 0x6d && 
						buffer[11] == 0x61 &&
						buffer[12] == 0x74 &&
						buffer[13] == 0x20 &&
						buffer[14] == 0x33 &&
						buffer[15] == 0x00) {
					f.close();
					return true; 
				}
			} catch (FileNotFoundException e) {
				Utils.logE("File not found", _logging);
				Utils.printStackTrace(e, _logging);
				e.printStackTrace();
			} catch (IOException e) {
				Utils.logE("IO error", _logging);
				Utils.printStackTrace(e, _logging);
			}
			try {
				f.close();
			} catch (IOException e) {
				Utils.logE("Close error", _logging);
				Utils.printStackTrace(e, _logging);
			}
			return false;
		}
		return false;
	}

	/**
	 * Close the database
	 */
	public void close() {
		//This sometimes throws SQLiteException: unable to close due to unfinalised statements
		try {
			Utils.logD("Closing database", _logging);
			_db.close();
		} catch (Exception e) {
			Utils.logE("onClose", _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Test the database if not open open it
	 */
	private void testDB() {
		if (_db == null) {
			Utils.logD("TestDB database is null", _logging);
			if (_dbPath != null) {  //then Content probably also null
				try {
					_db = SQLiteDatabase.openDatabase(_dbPath, null, SQLiteDatabase.OPEN_READWRITE); 
				} catch (Exception e) {
					Utils.logE("testDB " + e.getLocalizedMessage().toString(), _logging);
					Utils.printStackTrace(e, _logging);
					Utils.showMessage(_cont.getText(R.string.Error).toString(), //TODO 3.0 null pointer exception here _cont?
							e.getLocalizedMessage().toString() + "\n" +
							_cont.getText(R.string.StrangeErr).toString(), _cont);
				}
			} else {
				Utils.showMessage(_cont.getText(R.string.Error).toString(),
						_cont.getText(R.string.StrangeErr).toString(), _cont);
			}
		} else if (!_db.isOpen()) {
			Utils.logD("TestDB database not open", _logging);
			if (_dbPath == null) {
				Utils.showMessage(_cont.getText(R.string.Error).toString(),
						_cont.getText(R.string.StrangeErr).toString() + " dbPath = null", _cont);
			} else {
				try {
					_db = SQLiteDatabase.openDatabase(_dbPath, null, SQLiteDatabase.OPEN_READWRITE);
				} catch (Exception e) {
					Utils.logE("testDB " + e.getLocalizedMessage().toString(), _logging);
					Utils.printStackTrace(e, _logging);
					Utils.showMessage(_cont.getText(R.string.Error).toString(),
							e.getLocalizedMessage().toString() + "\n" +
							_cont.getText(R.string.StrangeErr).toString(), _cont);
				}
			}
		} else {

		}
	}
	
	/**
	 * Retrieve all the table names of the database
	 * @return
	 */
	public String[] getTables() {
		testDB();
		String sql ="select name from sqlite_master where type = 'table' order by name";
		Cursor res = _db.rawQuery(sql, null);
		int recs = res.getCount();
		String[] tables = new String[recs + 1];
		int i = 1;
		tables[0] = "sqlite_master";
		//Utils.logD("Tables: " + recs);
		while(res.moveToNext()) {
			tables[i] = res.getString(0);
			i++;
		}
		res.close();
		return tables;
	}

	/**
	 * Retrieve all views from a database
	 * @return
	 */
	public String[] getViews() {
		testDB();
		String sql ="select name from sqlite_master where type = 'view'";
		Cursor res = _db.rawQuery(sql, null);
		int recs = res.getCount();
		String[] views = new String[recs];
		int i = 0;
		//Utils.logD("Views: " + recs);
		while(res.moveToNext()) {
			views[i] = res.getString(0);
			i++;
		}
		res.close();
		return views;
	}

	/**
	 * Retrieve all views from a database
	 * @return
	 */
	public String[] getIndex() {
		testDB();
		String sql ="select name from sqlite_master where type = 'index'";
		Cursor res = _db.rawQuery(sql, null);
		int recs = res.getCount();
		String[] index = new String[recs];
		int i = 0;
		//Utils.logD("Index: " + recs);
		while(res.moveToNext()) {
			index[i] = res.getString(0);
			i++;
		}
		res.close();
		return index;
	}

	/**
	 * Retrieve a list of field names from a table
	 * @param table
	 * @return
	 */
	public Field[] getFields(String table) {
		// Get field type
		// SELECT typeof(sql) FROM sqlite_master where typeof(sql) <> "null" limit 1
		testDB();
		String sql = "select * from [" + table + "] limit 1";
		sql = "pragma table_info([" + table + "])";
		Cursor res = _db.rawQuery(sql, null);
		int cols = res.getCount();
		Field[] fields = new Field[cols];
		int i = 0;
		// getting field names
		while(res.moveToNext()) {
			Field field = new Field();
			field.setFieldName(res.getString(1));
			field.setFieldType(res.getString(2));
			field.setNotNull(res.getInt(3));
			field.setDef(res.getString(4));
			field.setPk(res.getInt(5));
			fields[i] = field;
			i++;
		}
		res.close();
		return fields;
	}

	/** 
	 * Return a String list with all field names of the table
	 * @param table
	 * @return
	 */
	public String[] getFieldsNames(String table) {
		testDB();
		String sql = "pragma table_info([" + table + "])";
		Cursor res = _db.rawQuery(sql, null); //TODO 3.3 NullPointerException here
		int cols = res.getCount();
		String[] fields = new String[cols];
		int i = 0;
		// getting field names
		while(res.moveToNext()) {
			fields[i] = res.getString(1);
			i++;
		}
		res.close();
		return fields;
	}
	
	/**
	 * Retrieve the number of columns in a table
	 * @param table
	 * @return
	 */
	public int getNumCols(String table) {
		testDB();
		String sql = "select * from [" + table + "] limit 1";
		Cursor cursor = _db.rawQuery(sql, null);
		int cols = cursor.getColumnCount();
		cursor.close();
		return cols;
	}

	/**
	 * This should replace getTableData and return both data and type
	 * @param table
	 * @param offset
	 * @param limit
	 * @param view
	 * @return
	 */
	public Record[] getTableData(String table, int offset, String order, int limit, boolean view) {
		Utils.logD("getTableData view " + view, _logging);
		String sql = "";
		if (view)
			sql = "select ";
		else
			sql = "select typeof(rowid), rowid as rowid, ";
		String[] fieldNames = getFieldsNames(table);
		for (int i = 0; i < fieldNames.length; i++) {
			sql += "typeof([" + fieldNames[i] +"]), [" + fieldNames[i] + "]";
			if (i < fieldNames.length - 1)
				sql += ", ";
		}
		sql += " from [" + table + "] " + order + " " + " limit " + limit + " offset " + offset;
		Utils.logD(sql, _logging);
		Cursor cursor = _db.rawQuery(sql, null);
		int columns = cursor.getColumnCount() / 2;
		Utils.logD("Columns: " + columns, _logging);
		int rows = cursor.getCount();
		Utils.logD("Rows = " + rows, _logging);
		Record[] recs = new Record[rows];
		int i = 0;
		while(cursor.moveToNext()) {
			recs[i] = new Record();
			AField[] fields = new AField[columns];
			for(int j = 0; j < columns; j++) {
				AField fld = new AField();
				//Get the field type due to SQLites flexible handling of field types the type from 
				//the table definition can't be used
				try {
					String fldType = cursor.getString(j*2);   //TODO still problems here with BLOB fields!?!?!?!
					fld.setFieldType(getFieldType(fldType));
				} catch(Exception e) {
					fld.setFieldType(AField.FieldType.UNRESOLVED);
				}
				if (fld.getFieldType() == AField.FieldType.NULL) {
					fld.setFieldData("");
				} else if (fld.getFieldType() == AField.FieldType.BLOB) {
					fld.setFieldData("BLOB (size: " + cursor.getBlob(j*2 + 1).length + ")");
				} else if (fld.getFieldType() == AField.FieldType.UNRESOLVED) {
					fld.setFieldData("Unknown field");
				} else {
					fld.setFieldData(cursor.getString(j*2 + 1));
				}
				fields[j] = fld;
			}
			recs[i++].setFields(fields);
		}
		cursor.close();
		return recs;
	}

	/**
	 * Retrieve data for table viewer
	 * @param cont Context on which to display errors
	 * @param table Name of table
	 * @param where Where clause for filter
	 * @param order Order if data sorted (by clicking on title)
	 * @param offset Offset if paging
	 * @param limit Page size
	 * @param view indication of view (with out on update trigger) 
	 * @return a list of Records
	 */
	public Record[] getTableDataWithWhere(Context cont, String table, String where, String order, int offset, int limit, boolean view) {
		testDB();
		String sql = "";
		if (view)
			sql = "select ";
		else
			sql = "select typeof(rowid), rowid as rowid, ";
		if (where.trim().equals(""))
			where = "";
		else
			where = " where " + where + " ";
		String[] fieldNames = getFieldsNames(table);
		for (int i = 0; i < fieldNames.length; i++) {
			sql += "typeof([" + fieldNames[i] +"]), [" + fieldNames[i] + "]";
			if (i < fieldNames.length - 1)
				sql += ", ";
		}
		sql += " from [" + table + "] " + where + order + " limit " + limit + " offset " + offset;
		Record[] recs = null;
		Utils.logD(sql, _logging);
		try {
			Cursor cursor = _db.rawQuery(sql, null);
			int columns = cursor.getColumnCount() / 2;
			//Utils.logD("Columns: " + columns, logging);
			int rows = cursor.getCount();
			//Utils.logD("Rows = " + rows, logging);
			recs = new Record[rows];
			int i = 0;
			while(cursor.moveToNext()) {
				recs[i] = new Record();
				AField[] fields = new AField[columns];
				for(int j = 0; j < columns; j++) {
					AField fld = new AField();
					//Get the field type due to SQLites flexible handling of field types the type from 
					//the table definition can't be used
					try {
						String fldType = cursor.getString(j*2);   //TODO still problems here with BLOB fields!?!?!?!
						fld.setFieldType(getFieldType(fldType));
					} catch(Exception e) {
						fld.setFieldType(AField.FieldType.UNRESOLVED);
					}
					if (fld.getFieldType() == AField.FieldType.NULL) {
						fld.setFieldData("");
					} else if (fld.getFieldType() == AField.FieldType.BLOB) {
						fld.setFieldData("BLOB (size: " + cursor.getBlob(j*2 + 1).length + ")");
					} else if (fld.getFieldType() == AField.FieldType.UNRESOLVED) {
						fld.setFieldData("Unknown field");
					} else {
						fld.setFieldData(cursor.getString(j*2 + 1));
					}
					fields[j] = fld;
				}
				recs[i++].setFields(fields);
			}
			cursor.close();
		} catch (Exception e) {
			Utils.showMessage(_cont.getText(R.string.Error).toString(), e.getLocalizedMessage(), cont);
			Utils.logE("getTableDataWithWhere", _logging);
			Utils.printStackTrace(e, _logging);
		}
		return recs;
	}
	
	/**
	 * Translate a field type in text format to the field type as "enum"
	 * @param fldType
	 * @return
	 */
	private FieldType getFieldType(String fldType) {
		if (fldType.equalsIgnoreCase("TEXT"))
			return AField.FieldType.TEXT;
		else if (fldType.equalsIgnoreCase("INTEGER"))
			return AField.FieldType.INTEGER;
		else if (fldType.equalsIgnoreCase("REAL"))
			return AField.FieldType.REAL;
		else if (fldType.equalsIgnoreCase("BLOB"))
			return AField.FieldType.BLOB;
		else if (fldType.equalsIgnoreCase("NULL"))
			return AField.FieldType.NULL;
		return AField.FieldType.UNRESOLVED;
	}

	/**
	 * Retrieve all data form the tables and return it as two dimensional string list
	 * @param table
	 * @return
	 */
	public String[][] oldgetTableData(String table, int offset, int limit, boolean view) {
		/*
		 * If not a query or view include rowid in data if no single field
		 * primary key exists
		 */
		testDB();
		String sql = "";
		if (view)
			sql = "select * from [" + table + "] limit " + limit + " offset " + offset;
		else
			sql = "select rowid as rowid, * from [" + table + "] limit " + limit + " offset " + offset;
		Utils.logD("SQL = " + sql, _logging);
		Cursor cursor = _db.rawQuery(sql, null);
		int cols = cursor.getColumnCount();
		int rows = cursor.getCount();
		String[][] res = new String[rows][cols];
		int i = 0;
		//int j = 0;
		while(cursor.moveToNext()) {
			for (int k=0; k<cols; k++) {
				try {
					//cursor.
					res[i][k] = cursor.getString(k);
				} catch (Exception e) {
					// BLOB fields cannot be read with getString catch them here 
					res[i][k] = "BLOB (size: " + cursor.getBlob(k).length + ")";
					//cursor.getBlob(k);
				} 
			}
			i++;
		}
		return res;
	}

	/**
	 * Return the SQL that defines the table
	 * @param table
	 * @return a String[] with sql needed to create the table
	 */
	public Record[] getSQL(Context cont, String table) {
		testDB();
		String sql = "select sql from sqlite_master where UPPER(tbl_name) = '" + table.toUpperCase(Locale.US) +"'	";
		Record[] recs = null;
		Utils.logD(sql, _logging);
		String[] fieldNames = getTableStructureHeadings(table);
		try {
			Cursor cursor = _db.rawQuery(sql, null);
			int columns = cursor.getColumnCount();
			int rows = cursor.getCount();
			//Utils.logD("Rows " + rows + " cols " + columns, logging);
			recs = new Record[rows];
			int i = 0;
			while(cursor.moveToNext()) {
				recs[i] = new Record();
				AField[] fields = new AField[columns];
				for(int j = 0; j < columns; j++) {
					AField fld = new AField();
					fld.setFieldData(cursor.getString(j));
					fld.setFieldType(FieldType.TEXT);
					fields[j] = fld;
				}
				recs[i].setFieldNames(fieldNames);
				recs[i].setFields(fields);
				i++;
			}
			cursor.close();
		} catch (Exception e) {
			Utils.showMessage(cont.getText(R.string.Error).toString(), e.getLocalizedMessage(), cont);
			Utils.logE("getTableDataWithWhere", _logging);
			Utils.printStackTrace(e, _logging);
		}
		return recs;
	}

	/**
	 * Return the headings for a tables structure
	 * @param table
	 * @return
	 */
	public String[] getTableStructureHeadings(String table) {
		String[] ret = {"id", "name","type","notnull","dflt_value","pk"};
		return ret;
	}
	
	/**
	 * Return table structure i a list of Record
	 * @param cont 
	 * @param table
	 * @return
	 */
	public Record[] getTableStructure(Context cont, String table) {
		testDB();
		String sql = "pragma table_info (["+table+"])";
		Record[] recs = null;
		Utils.logD(sql, _logging);
		String[] fieldNames = getTableStructureHeadings(table);
		try {
			Cursor cursor = _db.rawQuery(sql, null);
			int columns = cursor.getColumnCount();
			int rows = cursor.getCount();
			//Utils.logD("Rows " + rows + " cols " + columns, logging);
			recs = new Record[rows];
			int i = 0;
			while(cursor.moveToNext()) {
				recs[i] = new Record();
				AField[] fields = new AField[columns];
				for(int j = 0; j < columns; j++) {
					AField fld = new AField();
					fld.setFieldData(cursor.getString(j));
					fld.setFieldType(FieldType.TEXT);
					fields[j] = fld;
					//Utils.logD("Field no " + j + " " + fld.toString(), logging);
				}
				recs[i].setFieldNames(fieldNames);
				recs[i].setFields(fields);
				i++;
			}
			cursor.close();
		} catch (Exception e) {
			Utils.showMessage(cont.getText(R.string.Error).toString(), e.getLocalizedMessage(), cont);
			Utils.logE("getTableDataWithWhere", _logging);
			Utils.printStackTrace(e, _logging);
		}
		return recs;
	}

	/**
	 * Retrieve a list of FieldDescr to describe all fields of a table
	 * @param tableName
	 * @return
	 */
	public FieldDescr[] getTableStructureDef(String tableName) {
		testDB();
		String sql = "pragma table_info (["+tableName+"])";
		Cursor cursor = _db.rawQuery(sql, null);
		int rows = cursor.getCount();
		FieldDescr[] flds = new FieldDescr[rows]; 
		int i = 0;
		while(cursor.moveToNext()) {
			FieldDescr fld = new FieldDescr();
			fld.setCid(cursor.getInt(0));
			fld.setName(cursor.getString(1));
			fld.setType(fieldType2Int(cursor.getString(2)));
			fld.setNotNull(int2boolean(cursor.getInt(3)));
			fld.setDefaultValue(cursor.getString(4));
			fld.setPk(int2boolean(cursor.getInt(5)));
			flds[i] = fld;
			i++;
			//Utils.logD("getTableStructureDef: " + fld.getName());
		}
		cursor.close();
		return flds;
	}
	
	/**
	 * Convert a field type retrieved by a pragma table_info (tableName)
	 * to a RecordEditorBuilder editor type
	 * @param fieldType
	 * @return
	 */
	private int fieldType2Int(String fieldType) {
		if (fieldType.equalsIgnoreCase("STRING")
				|| fieldType.equalsIgnoreCase("TEXT"))
			return TableField.TYPE_STRING;
		else if (fieldType.equalsIgnoreCase("INTEGER"))
			return TableField.TYPE_INTEGER;
		else if (fieldType.equalsIgnoreCase("REAL") 
				|| fieldType.equalsIgnoreCase("FLOAT")
				|| fieldType.equalsIgnoreCase("DOUBLE"))
			return TableField.TYPE_FLOAT;
		else if (fieldType.equalsIgnoreCase("BOOLEAN")
				|| fieldType.equalsIgnoreCase("BOOL"))
			return TableField.TYPE_BOOLEAN;
		else if (fieldType.equalsIgnoreCase("DATE"))
			return TableField.TYPE_DATE;
		else if (fieldType.equalsIgnoreCase("TIME"))
			return TableField.TYPE_TIME;
		else if (fieldType.equalsIgnoreCase("DATETIME"))
			return TableField.TYPE_DATETIME;
		else if (fieldType.equalsIgnoreCase("PHONENO"))
			return TableField.TYPE_PHONENO;
		else
			return TableField.TYPE_STRING;
	}
	
	/**
	 * Convert the SQLite 0 / 1 boolean to Java boolean 
	 * @param intBool
	 * @return
	 */
	private boolean int2boolean(int intBool) {
		boolean res = false;
		if (intBool == 1)
			res = true;
		return res;
	}
	
	/**
	 * Return the result of the query as a comma separated test in String list
	 * @param sql
	 * @return
	 */
	public String[] getSQLQuery(String sql) {
		testDB();
		String[] tables = {_cont.getText(R.string.NoResult).toString()};
		try {
			Cursor res = _db.rawQuery(sql, null);
			int recs = res.getCount();
			tables = new String[recs];
			int i = 0;
			Utils.logD("Views: " + recs, _logging);
			while(res.moveToNext()) {
				for(int j = 0; j < res.getColumnCount(); j++) {
					if (j == 0)
						tables[i] = res.getString(j);
					else 
						tables[i] += ", " + res.getString(j);
				}
				i++;
			}
			res.close();
		} catch (Exception e) {
			tables = new String [] {"Error: " + e.toString()};
			Utils.logE("getSQLQuery", _logging);
			Utils.printStackTrace(e, _logging);
		}
		return tables;
	}

	/**
	 * Return a string list with the field names of one ore more tables
	 * @param tables
	 * @return
	 */
	public String[] getTablesFieldsNames(String[] tables) {
		testDB();
		Cursor res;
		List<String> tList = new ArrayList<String>();
		int i = 0;
		for (int j = 0; j < tables.length; j++) {
			String sql = "pragma table_info([" + tables[j] + "])";
			Utils.logD("getTablesFieldsNames: " + sql, _logging);
			res = _db.rawQuery(sql, null);
			i = 0;
			// getting field names
			while(res.moveToNext()) {
				tList.add("[" + tables[j] + "].[" + res.getString(1) + "]");
				//fields[i] = res.getString(1);
				i++;
			}
			res.close();
		}
		String[] fieldList = new String[tList.size()];
		i = 0;
		for (String str: tList) {
			fieldList[i] = str;
			i++;
		}
		return fieldList;
	}

	/**
	 * Save a SQL statement in a aSQLiteManager table in the current database.
	 * @param saveSql statement to save
	 */
	public void saveSQL(String saveSql) {
		testDB();
		testHistoryTable();
		String sql = "insert into aSQLiteManager (sql) values (\"" + saveSql +"\")";
		try {
			Utils.logD("SQL save", _logging);
			_db.execSQL(sql);
		} catch (SQLException e) {
			// All duplicate SQL ends here
			Utils.logD("saveSQL dublicate SQL not saved", _logging);
			//Utils.printStackTrace(e, logging);
		}
	}

	/**
	 * Test for a aSQLiteManager table in the current database. If it does not
	 * exists create it.
	 */
	private void testHistoryTable() {
		testDB();
		Cursor res = _db.rawQuery("select name from sqlite_master where type = \"table\" and name = \"aSQLiteManager\"", null);
		int recs = res.getCount();
		res.close();
		if (recs > 0) {
			return;
		} else {
			// create the aSQLiteManager table
			String sql = "create table aSQLiteManager (_id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL UNIQUE, sql TEXT NOT NULL UNIQUE)";
			_db.execSQL(sql);
			Utils.logD("aSQLiteManager table created", _logging);
			saveSQL("delete from aSQLiteManager where 1=1");
			saveSQL("drop table aSQLiteManager");
		}
	}

	/**
	 * Retrieve a number of rows based on a sql query
	 * @param sqlStatement the statement
	 * @param offset number of rows to skip
	 * @param limit max number of rows to retrieve
	 * @return a QueryResult object
	 */
	public QueryResult getSQLQueryPage(String sqlStatement, int offset, int limit) {
		testDB();
		String sql;
		//  || sqlStatement.toLowerCase().startsWith("pragma")
		if (sqlStatement.toLowerCase(Locale.US).startsWith("select"))
			sql = sqlStatement + " limit " + limit + " offset " + offset;
		else 
			sql = sqlStatement;
		Cursor cursor = null;
		QueryResult nres = null;
		boolean rawType = false;
		//Find out which for of query to use
		if (sql.trim().toLowerCase(Locale.US).startsWith("select"))
			rawType  = true;
		else if (sql.trim().toLowerCase(Locale.US).startsWith("pragma"))
			rawType = true;
		Utils.logD("Use rawType: " + rawType, _logging);
		// Use execSQL where no result is expected
		if (!rawType) {
			try {
				Utils.logD("execSQL: " + sql, rawType);
				_db.execSQL(sql);
				nres = new QueryResult();
				nres.Data = new String[1][1];
				nres.setColumnNames(new String[] {""});
				nres.Data[0][0] = _cont.getText(R.string.NotAnArror).toString();
			} catch (Exception e) {
				Utils.logE(e.toString(), _logging);
				Utils.printStackTrace(e, _logging);
				nres = new QueryResult();
				nres.setColumnNames(new String[] {_cont.getText(R.string.Error).toString()});
				nres.Data = new String[1][1];
				nres.Data[0][0] = e.toString();			
				Utils.logE(e.toString(), _logging);
			}
		} else {
			try {
				nres = new QueryResult();
				Utils.logD("rawQuery: " + sql, _logging);
				cursor = _db.rawQuery(sql, null);
				//TOD get column names
				nres.columnNames = cursor.getColumnNames();
				int rows = cursor.getCount();
				int cols = cursor.getColumnCount();
				nres.Data = new String[rows][cols];
				int i = 0;
				boolean result = false; 
				while(cursor.moveToNext()) {
					result = true;
					for (int k=0; k<cols; k++) {
						//Fails if it is a BLOB field
						try {
							nres.Data[i][k] = cursor.getString(k);
						} catch (Exception e) {
							nres.Data[i][k] = "BLOB (size: " + cursor.getBlob(k).length +")";
						}
					}
					i++;
				}
				if (!result) {
					nres = new QueryResult();
					nres.Data = new String[1][1];
					nres.setColumnNames(new String[] {""});
					nres.Data[0][0] = _cont.getText(R.string.NoResult).toString();
				}
				cursor.close();
			} catch (Exception e) {
				Utils.logE(e.toString(), _logging);
				Utils.printStackTrace(e, _logging);
				nres.setColumnNames(new String[] {_cont.getText(R.string.Error).toString()});
				nres.Data = new String[1][1];
				nres.Data[0][0] = e.toString();			
				if (cursor != null)
					cursor.close();
				Utils.logE(e.toString(), _logging);
			}
		}
		return nres;
	}

	/**
	 * Return a index definition from its name
	 * @param indexName name of index
	 * @return the sql to create the index
	 */
	public String getIndexDef(String indexName) {
		testDB();
		String res = "";
		String sql;
		sql = "select sql from sqlite_master where type = \"index\" and name = \"" + indexName + "\"";
		Utils.logD("get indexef: "+ sql, _logging);
		Cursor cursor = _db.rawQuery(sql, null);
		int rows = cursor.getCount();
		if (rows > 0) {
			while(cursor.moveToNext()) {
				res = cursor.getString(0);
			}
		}
	  cursor.close();
		return res;
	}

	/**
	 * Return a list of recent executed SQL statements from current database
	 * ordered by latest first
	 * @return a String[] with SQL statements
	 */
	public String[] getListOfSQL() {
		testDB();
		String sql = "select * from aSQLiteManager order by _id desc";
		String[] list = null;
		try {
			Cursor res = _db.rawQuery(sql, null);
			int cols = res.getCount();
			list = new String[cols];
			int i = 0;
			// getting field names
			while(res.moveToNext()) {
				String str = new String();
				str = res.getString(1);
				list[i] = str;
				i++;
			}
			res.close();
			return list;
		} catch (Exception e) {
			Utils.logE(e.toString(), _logging);
			Utils.printStackTrace(e, _logging);
			return list;
		}
	}
	
	/**
	 * Backup current database
	 * @return true on success
	 */
	public boolean exportDatabase() {
		testDB();
		String backupName = _dbPath + ".sql";
		File backupFile = new File(backupName);
		pd = new Dialog(_cont);
		pd.setContentView(R.layout.progressbar);
		myProgressBar = (ProgressBar) pd.findViewById(R.id.progressbar_Horizontal);
		progressTitle = (TextView) pd.findViewById(R.id.ProgressTitle);
		progressTable = (TextView) pd.findViewById(R.id.ProgressTable);
		Utils.logD(progressTitle.toString(), _logging);
		Utils.logD(progressTable.toString(), _logging);
		pd.show();
		new Thread(myExportThread).start();
		Utils.logD("Exportet to; " + backupFile.getAbsolutePath(), _logging);
		return true;
	}

	private Runnable myExportThread = new Runnable(){
		public void run() {
			testDB();
			String backupName = _dbPath + ".sql";
			File backupFile = new File(backupName);
			FileWriter f;
			BufferedWriter out;
	    try {
				f = new FileWriter(backupFile);
				out = new BufferedWriter(f);
				theHandle = myHandle;
				Utils.logD("Exporting to; " + backupFile, _logging);
	      Utils.logD("-- Database export made by aSQLiteManager", _logging);
				out.write("--\n");
	      out.write("-- Database export made by aSQLiteManager\n");
				out.write("--\n");
				// progress dialog should count from 0 to 100 for table def, data, idex, views
	      // export table definitions
				progressTitleText = "exporting table definitions";
				myProgress = 0;
				myHandle.sendMessage(myHandle.obtainMessage());
				//myHandle.sendMessage(myHandle.obtainMessage());
	      exportTableDefinitions(out);
	      // export data
				progressTitleText = "exporting table data";
				myProgress = 25;
				myHandle.sendMessage(myHandle.obtainMessage());
	      exportData(out);
	      // export index definitions
				progressTitleText = "exporting index definitions";
				myProgress = 50;
				myHandle.sendMessage(myHandle.obtainMessage());
	      exportIndexDefinitions(out);
	      // export view definitions
				progressTitleText = "exporting view definitions";
				myProgress = 75;
				myHandle.sendMessage(myHandle.obtainMessage());
	      exportViews(out);
	      //TODO export constraints -- how and i which order?

	      //TODO export triggers, procedures, ...
				progressTitleText = "exporting view triggers";
				myProgress = 90;
				myHandle.sendMessage(myHandle.obtainMessage());
	      exportTriggers(out);
	      
	      //Close the output stream
				myProgress = 100;
				myHandle.sendMessage(myHandle.obtainMessage());
	      out.close();
	      f.close();
	    } catch (IOException e) {
	    	//TODO can't show exception dialog here
	    	//TODO save the error and show it later
	    	//Utils.showException(e.getMessage(), _cont);
				Utils.logE("Runnable", _logging);
				Utils.printStackTrace(e, _logging);
	    	//return false;
		  }
			pd.dismiss();
			Utils.logD("Finish!!!", _logging);
			try {
			} catch (Throwable e) {
				Utils.logE("getSQLQuery", _logging);
				Utils.printStackTrace((Exception) e, _logging);
			}
		}
		Handler myHandle = new Handler(){
			@Override
			public void handleMessage(Message msg) {
				myProgressBar.setProgress(myProgress);
				progressTitle.setText("Processing: " + progressTitleText);
				progressTable.setText("Exporting: " + progressTableText);
			}
		};
	};
	
	/**
	 * Export all data from current database
	 * @param out
	 */
	private void exportData(BufferedWriter out) {
		// can't use the field type from sqlite_master as blobs can be
		// in any type of fields
		// insert into [programs] ([_id], [name])
		// values (9, X'1234567890ABCDEF')
		//The length of the hex must a multiple of 2 
		String sql = "select name from sqlite_master where type = 'table'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				String tabName = res.getString(0);   //  || tabName.equals("sqlite_sequence") set sequence as it was
				if(!(tabName.equals("sqlite_master") || tabName.equals("android_metadata")
						|| tabName.equals("sqlite_sequence"))) {
					progressTableText = tabName;
					theHandle.sendMessage(theHandle.obtainMessage());
					exportSingleTableData(tabName, out);
				}
			}
			res.close();
		} catch (Exception e) {
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Export all index definitions from current database
	 * @param out
	 */
	private void exportIndexDefinitions(BufferedWriter out) {
		String sql = "select name, sql from sqlite_master where type = 'index'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				// for auto index SQL is null
				if (res.getString(1) != null) {
					out.write("--\n");
					out.write("-- Exporting index definitions for " + res.getString(0) + nl);
					out.write("--\n");
					out.write(res.getString(1) + ";" + nl);
				}
			}
			res.close();
		} catch (IOException e) {
			Utils.logE("exportIndexDefinition", _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Export all view definitions from current database
	 * @param out
	 */
	private void exportViews(BufferedWriter out) {
		String sql = "select name, sql from sqlite_master where type = 'view'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				out.write("--\n");
				out.write("-- Exporting view definitions for " + res.getString(0) + nl);
				out.write("--\n");
				out.write(res.getString(1) + ";" + nl);
			}
			res.close();
		} catch (IOException e) {
			Utils.logE("exportViews", _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Export all trigger definitions from current database
	 * @param out
	 */
	private void exportTriggers(BufferedWriter out) {
		String sql = "select name, sql from sqlite_master where type = 'trigger'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				out.write("--\n");
				out.write("-- Exporting trigger definitions for " + res.getString(0) + nl);
				out.write("--\n");
				out.write(res.getString(1) + ";" + nl);
			}
			res.close();
		} catch (IOException e) {
			Utils.logE("exportTrigger", _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Export all table definitions of current database
	 * @param out
	 */
	private void exportTableDefinitions(BufferedWriter out) {
		String sql = "select name, sql from sqlite_master where type = 'table'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				String table = res.getString(0);
				if(!(table.equals("sqlite_master") || table.equals("sqlite_sequence") || 
						table.equals("android_metadata"))) {
					out.write("--\n");
					out.write("-- Exporting table definitions for " + table + nl);
					out.write("--\n");
					out.write(res.getString(1) + ";" + nl);
				}
			}
			res.close();
		} catch (IOException e) {
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
		if (res != null)
			res.close();
	}

	/**
	 * Restore current database from a file of same name but with .sql extension
	 * @return true on success
	 */
	public boolean restoreDatabase() {
		// Just delete all user data and then run the exported file as
		// a script
		testDB();
		String backupName = _dbPath + ".sql";
		File backupFile = new File(backupName);
		if (!backupFile.exists())
			Utils.showMessage(_cont.getText(R.string.Restore).toString(),
					_cont.getText(R.string.NoExportToRestore).toString(), _cont);
		// drop all views
		Utils.logD("Dropping all views", _logging);
		dropAllViews();
		// drop all user tables
		Utils.logD("Dropping all tables", _logging);
		dropAllTables();
		return runScript(backupFile);
	}
	
	/**
	 * 
	 * 
	 * @param sql 
	 */
	/**
	 * Execute a single line of SQL
	 * 
	 * @param sql the SQL statement to execute
	 * @param cont the content on which to show exception
	 */
	public boolean executeStatement(String sql, Context cont) {
		boolean res = true;
		Utils.logD("Executing statement:" + sql, _logging);
		testDB();
		try {
			_db.execSQL(sql);
		} catch (SQLException e) {
			res = false;
			//TODO tis msg is sometime shown on the wrong screen
			// _db has DBViewer's content so when called from table viewer
			// add content to arg
			Utils.showException(e.toString(), cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
		return res;
	}

	/**
	 * Run a SQL script from a file
	 * @param scriptFile
	 * @return true upon success
	 */
	public boolean runScript(File scriptFile) {
		testDB();
		Utils.logD("Running script " + scriptFile.getAbsolutePath(), _logging);
    try {
    	List<String> statements = SQLTools.parseSQLFile(_cont, scriptFile.getAbsolutePath());
    	Utils.logD("Statements " + statements.size(), _logging);
    	for(String line: statements) {
  	  	Utils.logD("SQL: " + line, _logging);
  	  	// execute SQL but not comments
  	  	if (!line.trim().startsWith("--"))
  	  		_db.execSQL(line);
    	}
    } catch (Exception e) {
    	Utils.showException(e.getLocalizedMessage(), _cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
    	return false;
    }
    return true;
	}

	/**
	 * Drop all user tables in current database
	 */
	private void dropAllTables() {
		String sql = "select name, sql from sqlite_master where type = 'table'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				String table = res.getString(0);
				if(!(table.equals("sqlite_master") || table.equals("sqlite_sequence") || 
						table.equals("android_metadata"))) {
					sql = "drop table " + table;
					_db.execSQL(sql);
				}
			}
		} catch (Exception e) {
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Drop all views in current database
	 */
	private void dropAllViews() {
		String sql = "select name, sql from sqlite_master where type = 'view'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			while(res.moveToNext()) {
				String view = res.getString(0);
				sql = "drop view " + view;
				_db.execSQL(sql);
			}
		} catch (Exception e) {
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
		res.close();
	}

	/**
	 * Starts a transaction. Transaction can be nested as the SQLite savepoints
	 * @return true if transaction started
	 */
	public boolean beginTransaction() {
		testDB();
		_db.beginTransaction();
		return _db.inTransaction();
	}
	
	/**
	 * Commit updates back to last begin transaction / savepoint
	 * @return true if still in transaction
	 */
	public boolean commit() {
		testDB();
		_db.setTransactionSuccessful();
		_db.endTransaction();
		return _db.inTransaction();
	}
	
	/**
	 * Roll back updates back to last transaction / savepoint 
	 * @return true if still in transaction
	 */
	public boolean rollback() {
		testDB();
		_db.endTransaction();
		return _db.inTransaction();
	}
	
	/**
	 * Return true i a transaction has not been commitet / rolled back
	 * @return
	 */
	public boolean inTransaction() {
		return _db.inTransaction();
	}

	/**
	 * Export the current query to a file named after the database with the 
	 * extension .export
	 * @param sql The SQL to query
	 */
	public void exportQueryResult(String sql) {
		testDB();
		try {
			Cursor data = _db.rawQuery(sql, null);  
			String backupName = _dbPath + ".export";
			File backupFile = new File(backupName);
			FileWriter f;
			BufferedWriter out;
			f = new FileWriter(backupFile);
			out = new BufferedWriter(f);
			while(data.moveToNext()) {
				// write export
				String fields = "";
				for(int i = 0; i < data.getColumnCount(); i++) {
					String val = data.getString(i);
					//tabInf.moveToPosition(i);
					//String type = tabInf.getString(2);
					if (val == null){
						fields += "null";
						if (i != data.getColumnCount()-1)
							fields += "; ";
//					} else if (type.equals("INTEGER") || type.equals("REAL")) {
//						fields += val;
//						if (i != data.getColumnCount()-1)
//							fields += ", ";
					} else {  // it must be string or blob(?) so quote it
						fields += "\"" + val + "\"";
						if (i != data.getColumnCount()-1)
							fields += "; ";
					}
				}
				out.write(fields + nl);
			}
			data.close();
			out.close();
			f.close();
		} catch (Exception e) {
			Utils.showException(e.getMessage(), _cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * @return Return true if the history table aSQLiteManager exists
	 */
	public boolean historyExists() {
		String sql = "select * from aSQLiteManager order by _id desc";
		try {
			Cursor res = _db.rawQuery(sql, null);
			res.close();
			return true;
		} catch (Exception e) {
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
			return false;
		}
	}

	/**
	 * Retrieve a record based on table name and rowid
	 * @param tableName
	 * @param rowId
	 * @return a list of TableFields one for each field the first contains
	 * the rowid for the record
	 */
	public TableField[] getRecord(String tableName, long rowId) {
		String sql = "select rowid as rowid, * from '" + tableName + "' where rowid = " + rowId;
		Utils.logD(sql, _logging);
		// retrieves field types, pk, ... from database
		FieldDescr[] tabledef = getTableStructureDef(tableName);
		Cursor cursor = _db.rawQuery(sql, null);
		TableField[] tfs = new TableField[cursor.getColumnCount()];
		int fields = cursor.getColumnCount();
		cursor.moveToNext(); 
		for (int j = 0; j < fields; j++) {
			TableField tf = new TableField();
			tf.setName(cursor.getColumnName(j));
			tf.setDisplayName(cursor.getColumnName(j));
			// The extra field rowid
			if (j == 0) {
				// Don't allow updating of rowid
				tf.setUpdateable(false);
				tf.setType(TableField.TYPE_INTEGER);
			} else {
				tf.setUpdateable(true);
				tf.setType(tabledef[j-1].getType());
				tf.setNotNull(tabledef[j-1].isNotNull());
				tf.setPrimaryKey(tabledef[j-1].isPk());
				tf.setDefaultValue(tabledef[j-1].getDefaultValue());
				//TODO need to retrieve the foreign key
				Utils.logD("Name - type: " + tf.getName() + " - " + tabledef[j-1].getType(), _logging);
			}
			//TODO Implement BLOB edit
			//is it a BLOB field turn edit off
			try {
				tf.setValue(cursor.getString(j));
			} catch (Exception e) {
				tf.setUpdateable(false);
				tf.setValue("BLOB");
				Utils.logE(e.getMessage(), _logging);
				Utils.printStackTrace(e, _logging);
			}
			tfs[j] = tf;
		}
		cursor.close();
		// Get foreign keys
		sql = "PRAGMA foreign_key_list(["+tableName+"])";
		cursor = _db.rawQuery(sql, null);
		while(cursor.moveToNext()) {
			//Go through all fields to see if the fields has FK
			for(int i = 0; i < fields; i++) {
				String fkName = cursor.getString(3);
				if (tfs[i].getName().equals(fkName)) {
					Utils.logD("FK: " + cursor.getString(2)+ "->" + cursor.getString(4), _logging);
					tfs[i].setForeignKey("select [" + cursor.getString(4) + "] from [" + cursor.getString(2)+ "]");
					break;
				} 
			}
		}
		cursor.close();
		return tfs;
	}

	/**
	 * Retrieve a list of TableFields to match a empty record for the
	 * database
	 * @param tableName
	 * @return
	 */
	public TableField[] getEmptyRecord(String tableName) {
		FieldDescr[] fd = getTableStructureDef(tableName);
		TableField[] tfs = new TableField[fd.length];
		for (int i = 0; i < fd.length; i++) {
			TableField tf = new TableField();
			tf.setName(fd[i].getName());
			tf.setType(fd[i].getType());
			tf.setPrimaryKey(fd[i].isPk());
			tf.setUpdateable(true);
			tf.setValue(null);
			tf.setNotNull(fd[i].isNotNull());
			tf.setDefaultValue(fd[i].getDefaultValue());
			tfs[i] = tf;
		}
		//Get the FK's
		// Get foreign keys
		String sql = "PRAGMA foreign_key_list(["+tableName+"])";
		Cursor cursor = _db.rawQuery(sql, null);
		while(cursor.moveToNext()) {
			//Go through all fields to see if the fields has FK
			for(int i = 0; i < fd.length; i++) {
				String fkName = cursor.getString(3);
				//Utils.logD("NameMH: " + tfs[i].getName());
				if (tfs[i].getName().equals(fkName)) {
					Utils.logD("FK: " + cursor.getString(2)+ "->" + cursor.getString(4), _logging);
					tfs[i].setForeignKey("select [" + cursor.getString(4) + "] from [" + cursor.getString(2)+ "]");
					break;
				} 
			}
		}
		cursor.close();
		return tfs;
	}
	
	/**
	 * Update a record in tableName based on it rowId with the fields
	 * in  
	 * @param tableName
	 * @param rowId
	 * @param fields
	 * @param cont on which to show errors
	 */
	public void updateRecord(String tableName, long rowId, TableField[] fields, Context cont) {
    ContentValues values = new ContentValues();
    //TODO This clears BLOB fields
		try {
			for (TableField fld: fields) {
				String val = fld.getValue();
				if (val.trim().equals(""))
					val = null;
				//Only change updateable fields
				if (fld.isUpdateable())
					values.put("[" + fld.getName() + "]", val);
			}
			String[] args = {"" + rowId};
			_db.update("[" + tableName + "]", values, "rowId = ?", args);
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}
	
	/**
	 * This is used to update views
	 * @param tableName
	 * @param oldData
	 * @param newData
	 * @param cont
	 */
	public void updateViewRecord(String tableName, Record oldData, TableField[] newData,
			Context cont) {
		String where = "";
		String[] args = new String[oldData.getFields().length];
		//String[] fieldNames = getFieldsNames(tableName);
		ContentValues values = new ContentValues();
		boolean first = true;
		int i = 0;
		try {
			// fill in field names, values and build where with old data
			for (TableField fld: newData) {
				fld.getType();
				String val = fld.getValue();
				if (val.trim().equals(""))
					val = null;
				values.put(fld.getName(), val);
				//Utils.logD("UpdateView field, new value " +fld.getName() +", " + val, _logging);
				Utils.logD("First? " + first, _logging);
				if (first) {
					first = false;
				} else {
					where += " and ";
				}
				String oldVal = oldData.getFields()[i].getFieldData();
				if (oldVal.trim().equals(""))
					oldVal= null;
				args[i] = oldVal; 
				if (oldVal == null)
					where += fld.getName() + " is ? ";
				else
					where += fld.getName() + " = ? ";
				//Utils.logD("Where "+ where, _logging);
				i++;
			}
			Utils.logD("Where " + where, _logging);
			Utils.logD("Updating view", _logging);
			_db.update(tableName, values, where, args);
		} catch (Exception e) {
			if (e != null) {
				Utils.showMessage("Error", e.getLocalizedMessage(), cont);
				Utils.logE(e.getMessage(), _logging);
			}
			Utils.logD(e.getLocalizedMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

//	/**
//	 * @param fld
//	 * @return
//	 */
//	private String quoteStrings(TableField fld) {
//		boolean quete = true;
//		if (fld.getValue() == null || fld.getValue().equals(""))
//			return "null";
//		switch (fld.getType()) {
//		case TableField.TYPE_BOOLEAN:
//		case TableField.TYPE_FLOAT:
//		case TableField.TYPE_INTEGER:
//			quete = false;
//			break;
//		}
//		if (quete)
//			return "\"" + fld.getValue()+"\"";
//		else
//			if (fld.getType() == TableField.TYPE_BOOLEAN)
//				if (fld.getValue().equalsIgnoreCase("true"))
//					return "1";
//				else
//					return "0";
//			return fld.getValue();
//	}

	/**
	 * @param tableName
	 * @param fields
	 * @param cont on which to display errors
	 */
	public void insertRecord(String tableName, TableField[] fields, Context cont) {
    ContentValues values = new ContentValues();
		try {
			for (TableField fld: fields) {
				String val = fld.getValue();
				if (!val.trim().equals(""))
					values.put("[" + fld.getName() + "]", val);
			}
			_db.insertOrThrow("[" + tableName + "]", null, values);
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}

	/**
	 * Export a single table to a file in the same catalog as the database named
	 * database_tablename.sql
	 * @param tableName
	 * @return true on success
	 */
	public boolean exportTable(String table) {
		Utils.logD("Dumping table: " + table, _logging);
		String backupName = _dbPath + "." + table + ".sql";
		File backupFile = new File(backupName);
		FileWriter f;
		BufferedWriter out;
    try {
			f = new FileWriter(backupFile);
			out = new BufferedWriter(f);
			exportSingleTableDefinition(table, out);
			Utils.logD("Def exported", _logging);
			exportSingleTableData(table, out);
			Utils.logD("Data exported", _logging);

			out.close();
      f.close();
    } catch (IOException e) {
    	Utils.showException(e.getMessage(), _cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
    	return false;
    }
		return true;
	}

	/**
	 * Export data form any table as a SQL script
	 * @param tableName
	 * @param out
	 * @return
	 */
	private boolean exportSingleTableData(String tableName, BufferedWriter out) {
		//TODO use some of the SQLite core functions http://www.sqlite.org/lang_corefunc.html
		//  to handle BLOBs better:
		//  typeof() -> "null", "integer", "real", "text", or "blob"
		//  quote() quote fields thats need quoting
		//	hex() transform a blot to hexadecimals for export
		try {
			String sql = "";
				out.write("--\n");
				out.write("-- Exporting data for  " + tableName+ nl);
				out.write("--\n");
//				sql = "PRAGMA table_info (" + tableName + ")";
//				Cursor tabInf = _db.rawQuery(sql, null);
				// retrieve data
				//TODO change this to [field1], typeof([field1]), ....
				sql = selectWithTypes(tableName);
				Utils.logD(sql, _logging);
				Cursor data = _db.rawQuery(sql, null);
				int columns = data.getColumnCount() / 2;
				while (data.moveToNext()) {
					String fields = "";
					for(int i = 0; i < columns; i++) {
						FieldType fldt;
						String field = "";
						try {
							String fldType = data.getString(i*2);
							fldt = getFieldType(fldType);
						} catch(Exception e) {
							fldt = AField.FieldType.UNRESOLVED;
						}
						if (fldt == AField.FieldType.NULL) {
							field = "null";
						} else if (fldt == AField.FieldType.BLOB) {
							field = "X'" + byteArrayToHexString(data.getBlob(i*2 + 1)) +"'";
						} else if (fldt == AField.FieldType.INTEGER) {
							field = data.getString(i*2 + 1);
						} else if (fldt == AField.FieldType.REAL) {
							field = data.getString(i*2 + 1);
						} else if (fldt == AField.FieldType.TEXT) {
							field = "'" + data.getString(i*2 + 1) + "'";
						} else if (fldt == AField.FieldType.UNRESOLVED) {
							Utils.showMessage("Problem", "Encountered unresolved field type, not correct exported.\n" +
									"Please report problem!", _cont);
						} else {
							Utils.showMessage("Problem", "Encountered unknown field type, not correct exported\n" +
									"Please report problem!", _cont);
						}
						if (i > 0)
							fields += ", " + field;
						else
							fields = field;
					}
					out.write("insert into " + tableName + " values (" + fields + ");" + nl);
					Utils.logD("insert into " + tableName + " values (" + fields + ");" + nl, _logging);
				}
				data.close();
		} catch (Exception e) {
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
		return false;
	}

	/**
	 * Generate a sql to select all fields together with their field types
	 * @param tableName
	 * @return a select statement like:
	 * select typeof([field1]), [field1],
	 *   typeof([field2]), [field2], ...,
	 *   typeof([fieldn]), [fieldn]
	 * from [tableName]
	 */
	private String selectWithTypes(String tableName) {
		String sql = "select ";
		String[] fieldNames = getFieldsNames(tableName);
		for (int i = 0; i < fieldNames.length; i++) {
			sql += "typeof([" + fieldNames[i] +"]), [" + fieldNames[i] + "]";
			if (i < fieldNames.length - 1)
				sql += ", ";
		}
		sql += " from [" + tableName + "]";
		Utils.logD(sql, _logging);
		return sql;
	}

	private String byteArrayToHexString(byte[] b) {
	  String result = "";
	  for (int i=0; i < b.length; i++) {
	    result +=
	          Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
	  }
	  return result;
	}
	
	@SuppressWarnings("unused")
	private static byte[] hexStringToByteArray(String s) {
    int len = s.length();
    byte[] data = new byte[len / 2];
    for (int i = 0; i < len; i += 2) {
        data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                             + Character.digit(s.charAt(i+1), 16));
    }
    return data;
	}
	
	/**
	 * @param tableName
	 * @param out
	 * @return
	 */
	private boolean exportSingleTableDefinition(String tableName, BufferedWriter out) {
		String sql = "select name, sql from sqlite_master where type = 'table' and name = '" + tableName +"'"; 
		Cursor res = _db.rawQuery(sql, null);
		try {
			//Exporting crate table SQL 
			while(res.moveToNext()) {
				String table = res.getString(0);
				if(!(table.equals("sqlite_master") || table.equals("sqlite_sequence") || 
						table.equals("android_metadata"))) {
					out.write("--\n");
					out.write("-- Exporting table definitions for " + table + nl);
					out.write("--\n");
					out.write(res.getString(1) + ";" + nl);
				}
			}
			//Exporting crate index SQL 
			sql = "select name, sql from sqlite_master where type = 'index' and name = '" + tableName +"'"; 
			res = _db.rawQuery(sql, null);
			while(res.moveToNext()) {
				String table = res.getString(0);
				if(!(table.equals("sqlite_master") || table.equals("sqlite_sequence") || 
						table.equals("android_metadata"))) {
				}
			}
			//Exporting crate trigger SQL 
			sql = "select name, sql from sqlite_master where type = 'trigger' and name = '" + tableName +"'"; 
			res = _db.rawQuery(sql, null);
			while(res.moveToNext()) {
				String table = res.getString(0);
				if(!(table.equals("sqlite_master") || table.equals("sqlite_sequence") || 
						table.equals("android_metadata"))) {
				}
			}
			res.close();
		} catch (IOException e) {
			Utils.logE("exportSingleTableDefinition", _logging);
			Utils.printStackTrace(e, _logging);
			return false;
		}
		return true;
	}


	public String getVersionInfo() {
		// pragma user_version
		// pragma schema_version

		testDB();
		String sql = "pragma schema_version";
		Cursor cursor = _db.rawQuery(sql, null);
		String res = "schema_version: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		sql = "pragma user_version";
		cursor = _db.rawQuery(sql, null);
		res += "\nuser_version: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		sql = "pragma encoding";
		cursor = _db.rawQuery(sql, null);
		res += "\nencoding: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		sql = "pragma page_size";
		cursor = _db.rawQuery(sql, null);
		res += "\npage_size: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		sql = "pragma page_count";
		cursor = _db.rawQuery(sql, null);
		res += "\npage_count: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		sql = "pragma locking_mode";
		cursor = _db.rawQuery(sql, null);
		res += "\nlocking_mode: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		sql = "pragma journal_mode";
		cursor = _db.rawQuery(sql, null);
		res += "\njournal_mode: ";
		while(cursor.moveToNext()) {
			res += cursor.getString(0);
		}
		
		// journal_mode
		// collation_list??
		// auto_vacuum
		
		return res;
	}

	public void deleteRecord(String tableName, Long rowId, Context cont) {
		String sql = "delete from [" + tableName + "] where rowid = " + rowId;
		Utils.logD("Delete SQL = " + sql, _logging);
		try {
			_db.execSQL(sql);
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), cont);
			Utils.logE("deleteRecord", _logging);
			Utils.printStackTrace(e, _logging);
		}
	}
	
	/**
	 * Try to delete a record from a view this only works if there is a
	 * InStedOfDelete trigger on the view if not a SQL exception
	 * @param viewName Name of the view to delete from
	 * @param oldData A Record holding selected record
	 * @param cont The context on which to display messages
	 */
	public void deleteViewRecord(String viewName, Record oldData, Context cont) {
		String where = "";
		boolean first = true;
		String[] args = new String[oldData.getFields().length];
		String[] fieldNames = getFieldsNames(viewName);
		for (String fld: fieldNames) {
			if (!first)
				where += " and ";
			first = false;
			where += fld + " = ? ";
		}
		Utils.logD("Fields: " + oldData.getFields().length, _logging);
		for (int i = 0; i < oldData.getFields().length; i++) {
			args[i] = oldData.getFields()[i].getFieldData();
		}
		_db.delete(viewName, where, args);
	}
	
	public int getNoOfRecords(String tableName, String where) {
		int recs = 0;
		if (where.trim().equals("")) {
			where = "";
		} else {
			where = " where " + where; 
		}
		String sql = "select count(*) from [" + tableName + "] " + where;
		try {
			Cursor cursor = _db.rawQuery(sql, null);
			while(cursor.moveToNext()) {
				recs += cursor.getInt(0);
			}
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), _cont);
			Utils.logE("getNoOfRecords", _logging);
			Utils.printStackTrace(e, _logging);
		}
		return recs;
	}

	/**
	 * Retrieve a list of lookup values for selection lists
	 * @param foreignKey
	 * @return
	 */
	public String[] getFKList(String foreignKey) {
		//TODO must be changed to handle lookup tables with code - values
		// must the return both the foreign key "code" and describing text
		String[] fk = new String[0];
		try {
			Cursor cursor = _db.rawQuery(foreignKey, null);
			fk = new String[cursor.getCount()];
			int i = 0;
			while(cursor.moveToNext()) {
				fk[i++] = cursor.getString(0);
			}
			cursor.close();
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), _cont);
			Utils.logE("getFKList", _logging);
			Utils.printStackTrace(e, _logging);
		}
		return fk;
	}

	/**
	 * Retrieve a list of lookup values for selection lists
	 * @param foreignKey
	 * @return
	 */
	public ForeignKeyHolder getFKList2(String foreignKey) {
		//TODO must be changed to handle lookup tables with code - values
		// must the return both the foreign key "code" and describing text
		ForeignKeyHolder lists = new ForeignKeyHolder();
		String[] ids = new String[0];
		try { 
			Cursor cursor = _db.rawQuery(foreignKey, null);
			ids = new String[cursor.getCount()];
			int i = 0;
			while(cursor.moveToNext()) {
				ids[i++] = cursor.getString(0);
			}
			cursor.close();
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), _cont);
			Utils.logE("getFKList", _logging);
			Utils.printStackTrace(e, _logging);
		}
		// select [id] from [foreign]
		//TODO replace field name with *
		String sql = foreignKey.substring(0, foreignKey.indexOf('[')) + "*" + 
				foreignKey.substring(foreignKey.indexOf(']') + 1);
		Utils.logD("SQL: " + sql, _logging);
		String[] texts = new String[0];
		try {
			Cursor cursor = _db.rawQuery(sql, null);
			texts = new String[cursor.getCount()];
			int i = 0;
			int cols = cursor.getColumnCount();
			while(cursor.moveToNext()) {
				int j = 0;
				String rowText = "";
				for (j = 0; j < cols; j++) {
					rowText += cursor.getString(j);
					if (j < cols - 1) 
						rowText += " | ";
				}
				texts[i++] = rowText;
			}
			cursor.close();
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), _cont);
			Utils.logE("getFKList", _logging);
			Utils.printStackTrace(e, _logging);
		}
		lists.setId(ids);
		lists.setText(texts);
		return lists;
	}
	
	/**
	 * Enable foreign keys checking
	 */
	public void FKOn() {
		int res = 0;
		Utils.logD("Turning on foreign keys checkin", _logging);
		_db.execSQL("PRAGMA foreign_keys = on");
		try {
			Cursor curs = _db.rawQuery("Pragma foreign_keys", null);
			while(curs.moveToNext()) {
				res = curs.getInt(0);
			}
			curs.close();
		} catch (Exception e) {
			Utils.showMessage("Error", e.getLocalizedMessage(), _cont);
			Utils.logE("FKOn", _logging);
			Utils.printStackTrace(e, _logging);
		}
		Utils.logD("Foreign key on? " + res, _logging);
		if (res == 0) {
			Utils.showMessage("Error", "Could not turn on foreign keys - too old Android?", _cont);
		}
	}

	/**
	 * Test if data from a view can updated, deleted or new data can be
	 * inserted through INSTEAD OF trigger
	 * @param viewName
	 * @return a ViewUpdatable 
	 */
	public ViewUpdateable isViewUpdatable(String viewName) {
		ViewUpdateable res = new ViewUpdateable();
		// TODO not tested
		String sql = "select sql from sqlite_master where type = \"trigger\" " +
				"and Upper(tbl_name) = \"" + viewName.toUpperCase(Locale.US) + "\"";
		String trigger = null;
		res.setDeleteable(false);
		res.setUpdateable(false);
		res.setInsertable(false);
		try {
			Cursor curs = _db.rawQuery(sql, null);
			Utils.logD("Recs: " + curs.getCount(), _logging);
			while(curs.moveToNext()) {
				trigger = curs.getString(0);
				if (trigger.toUpperCase(Locale.US).contains("INSTEAD OF UPDATE"))
					res.setUpdateable(true);
				if (trigger.toUpperCase(Locale.US).contains("INSTEAD OF INSERT"))
					res.setInsertable(true);
				if (trigger.toUpperCase(Locale.US).contains("INSTEAD OF DELETE"))
					res.setDeleteable(true);
			}
			curs.close();
		} catch (Exception e) {
			Utils.logE("Could not determine triggeer type", _logging);
			e.printStackTrace();
		}
		Utils.logD("View updateable: " + res, _logging);
		return res;
	}

	public String csvExport(Context cont, String table, String exportFile) {
		// if no filename is given use databasename + "." + tablename + .csv
		if (exportFile == null)
			exportFile = _dbPath + "." + table + ".csv";
		File file = new File(exportFile);
	  FileWriter txt;
		try {
			txt = new FileWriter(file);
			Cursor curs = _db.rawQuery("select * from [" + table + "]", null);
			while(curs.moveToNext()) {
				//Utils.logD("Exporting new row", logging);
				List<String> values = new ArrayList<String>();
				int cols = curs.getColumnCount();
				for (int i = 0; i < cols; i++) {
					String cal = null;
					try {
						cal = curs.getString(i);
					} catch (Exception e) {
						cal = "Blob";
					}
					values.add(cal);
				}
				try {
					CSVUtils.writeLine(txt, values);
				} catch (Exception e) {
					e.printStackTrace();
					return _cont.getText(R.string.CSVExportError) + e.getLocalizedMessage();
				}
			}
			curs.close();
			txt.close();
		} catch (IOException e) {
			e.printStackTrace();
			return _cont.getText(R.string.CSVExportError) + e.getLocalizedMessage();
		}
		return _cont.getText(R.string.CSVExportSavedHere) + exportFile;
	}

	public String csvExport(Context cont, String table, List<String> fields, String exportFile) {
		// if no filename is given use databasename + "." + tablename + .csv
		if (exportFile == null)
			exportFile = _dbPath + "." + table + ".csv";
		File file = new File(exportFile);
	  FileWriter txt;
		try {
			txt = new FileWriter(file);
			String flds = "";
			for (String field: fields) {
				if (flds.length() > 0)
					flds += ", [" + field + "]";
				else
					flds = "[" + field + "]";
			}
			Cursor curs = _db.rawQuery("select " + flds + " from [" + table + "]", null);
			while(curs.moveToNext()) {
				//Utils.logD("Exporting new row", logging);
				List<String> values = new ArrayList<String>();
				int cols = curs.getColumnCount();
				for (int i = 0; i < cols; i++) {
					String cal = null;
					try {
						cal = curs.getString(i);
					} catch (Exception e) {
						cal = "Blob";
					}
					values.add(cal);
				}
				try {
					CSVUtils.writeLine(txt, values);
				} catch (Exception e) {
					e.printStackTrace();
					return _cont.getText(R.string.CSVExportError) + e.getLocalizedMessage();
				}
			}
			curs.close();
			txt.close();
		} catch (IOException e) {
			e.printStackTrace();
			return _cont.getText(R.string.CSVExportError) + e.getLocalizedMessage();
		}
		return _cont.getText(R.string.CSVExportSavedHere) + exportFile;
	}
	
	/**
	 * Import a CSV filr
	 * @param table
	 * @param importFile
	 * @return
	 */
	public String cvsImport(Context cont, String table, String importFile) {
		// if no filename is given use databasename + "." + tablename + .csv
		if (importFile == null)
			importFile = _dbPath + "." + table + ".csv";
		File file = new File(importFile);
		if (!file.canRead())
			return _cont.getText(R.string.CSVEImortError) + " "
				+ _cont.getText(R.string.CanNotRead) + importFile;
  	String line = null;
    try {
  		FileInputStream fstream = new FileInputStream(importFile);
  	  DataInputStream in = new DataInputStream(fstream);
  	  BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	String[] fieldNames = getFieldsNames(table);
    	while ((line = br.readLine()) != null)   {
    		int fieldNo = 0;
    		//Utils.logD("CSV line: " + line, logging);
      	List<String> items = CSVUtils.parseLine(line);
        ContentValues values = new ContentValues();
  			for (String item: items) {
  				// Only import as many items as fields in the table
  				if (fieldNo >= fieldNames.length)
  					break;
  				if (!(item == null || item.trim().equals("")))
  					values.put("[" + fieldNames[fieldNo] + "]", item.trim());
  				//Utils.logD("Field " + fieldNames[fieldNo] +" val " + item.trim(), logging);
  				fieldNo++;
  			}
  			_db.insertOrThrow("[" + table + "]", null, values);
    	}
    	br.close();
    	in.close();
		} catch (Exception e) {
			e.printStackTrace();
			return _cont.getText(R.string.CSVEImortError) + e.getLocalizedMessage() + "\n"
					+ line;
		}
		return _cont.getText(R.string.CSVImported) + importFile;
	}

	/**
	 * Import data from a CSV file into "table" import the data to specific "cols" of
	 * the CSV file into specific fields the list of field names must match the list of
	 * columns
	 * @param cont
	 * @param table The table to import into
	 * @param importFile The CSV file to import
	 * @param skipLines The number of lines to skip before importing
	 * @param fields A List<String> with the file names to import into
	 * @param cols A List<Integer> with the column no to import
	 * @return
	 */
	public String cvsImport(Context cont, String table, String importFile, int skipLines,
			List<String> fields, List<Integer> cols) {
		// if no filename is given use databasename + "." + tablename + .csv
		if (importFile == null)
			importFile = _dbPath + "." + table + ".csv";
		File file = new File(importFile);
		if (!file.canRead())
			return _cont.getText(R.string.CSVEImortError) + " "
				+ _cont.getText(R.string.CanNotRead) + importFile;
  	String line = null;
    try {
  		FileInputStream fstream = new FileInputStream(importFile);
  	  DataInputStream in = new DataInputStream(fstream);
  	  BufferedReader br = new BufferedReader(new InputStreamReader(in));
    	long lineNo = 0;
    	while ((line = br.readLine()) != null)   {
    		if (lineNo++ >= skipLines) {
      		int fieldNo = 0;
        	List<String> items = CSVUtils.parseLine(line);
          ContentValues values = new ContentValues();
    			for (Integer col: cols) {
  					values.put("[" + fields.get(fieldNo) + "]", items.get(col-1));
      			//Utils.logD("Field - val " + fields.get(fieldNo) + " - " + items.get(col -1), logging);
  					fieldNo++;
    			}
    			_db.insertOrThrow("[" + table + "]", null, values);
    		}
    	}
    	br.close();
    	in.close();
		} catch (Exception e) {
			e.printStackTrace();
			return _cont.getText(R.string.CSVEImortError) + e.getLocalizedMessage() + "\n"
					+ line;
		}
		return _cont.getText(R.string.CSVImported) + importFile;
	}
	
	/**
	 * Read a single BLOB field from the database and save it on sdCard
	 * 
	 * @param fileName the file to save the blob in
	 * @param tableName table to read data from
	 * @param rowNo row of the table
	 * @param columnNo column to find the blob field
	 * @param cont the context of the form to display messages on
	 */
	public void saveBlobData(String fileName, String tableName, long rowNo,
			int columnNo, Context cont) {
		String sql = "select rowid, * from " + tableName + " where rowid = " + rowNo;
		Cursor cursor = _db.rawQuery(sql, null);
		int cols = cursor.getColumnCount();
		Utils.logD("cols " + cols + " columnNo " + columnNo, _logging);
		if (!(columnNo > cols-1)) {
			while(cursor.moveToNext()) {
				byte[] blob = cursor.getBlob(columnNo);
				Utils.logD("Field length " + blob.length, _logging);
				BufferedOutputStream bos;
				try {
					String path = _dbPath.substring(0, _dbPath.lastIndexOf("/"));
					Utils.logD("Path " + path, _logging);
					bos = new BufferedOutputStream(new FileOutputStream(path + "/" + fileName));
					bos.write(blob);
					bos.flush();
					bos.close();
				} catch (FileNotFoundException e) {
					Utils.showException(e.getLocalizedMessage(), cont);
					e.printStackTrace();
				} catch (IOException e) {
					Utils.showException(e.getLocalizedMessage(), cont);
					e.printStackTrace();
				}
			}
		} else {
			Utils.showMessage(cont.getText(R.string.Error).toString(), "column miss match", cont);
		}

		//byte[] blob = c.getBlob(1);
		// write insert blob
		//byte[] photo = baos.toByteArray(); 
		//db.insertUserDetails(value1,value2, value3, photo,value2);_
	}

	/**
	 * Load a file into a BLOB field
	 * @param fileName name of file toload
	 * @param tableName table name
	 * @param rowNo The rowId of the table toload data into
	 * @param columnNo The column of the record which hold the BLOB field
	 * @param cont The Content to display errors on
	 */
	public void loadBlobData(String fileName, String tableName, long rowNo,
			int columnNo, Context cont) {
		String sql = "select rowid, * from " + tableName + " where rowid = " + rowNo;
		Cursor cursor = _db.rawQuery(sql, null);
		String fieldName = "";
		while(cursor.moveToNext()) {
			fieldName = cursor.getColumnName(columnNo);
		}
		Utils.logD("Updating BLOB in field" + fieldName, _logging);
    ContentValues values = new ContentValues();
		try {
			String path = _dbPath.substring(0, _dbPath.lastIndexOf("/"));
			Utils.logD("Path " + path, _logging);
			File f = new File(path + "/" + fileName);
			if (!f.isFile()) {
				Utils.showMessage(_cont.getText(R.string.Error).toString(), 
						_cont.getText(R.string.FileNotFound).toString(), cont);
				return;
			}
			if (!f.canRead()) {
				Utils.showMessage(_cont.getText(R.string.Error).toString(), 
						_cont.getText(R.string.CanNotReadFile).toString() , cont);
				return;
			}
			InputStream is = _cont.getContentResolver().openInputStream(Uri.fromFile(f));
			int size = (int)f.length();
			byte[] bytes = new byte[size];
			is.read(bytes);
			values.put("[" + fieldName + "]", bytes);
			_db.update(tableName, values, "rowId = " + rowNo, null);
		} catch (Exception e) {
			Utils.showException(e.getLocalizedMessage(), cont);
			Utils.logE(e.getMessage(), _logging);
			Utils.printStackTrace(e, _logging);
		}
	}
}