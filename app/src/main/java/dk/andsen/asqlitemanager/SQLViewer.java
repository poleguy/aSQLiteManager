/**
 * Part of aSQLiteManager (http://sourceforge.net/projects/asqlitemanager/)
 * a a SQLite Manager by andsen (http://sourceforge.net/users/andsen)
 *
 * Open files with SQL scripts, let user execute one or all commands
 * 
 * @author andsen
 *
 */

package dk.andsen.asqlitemanager;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.ListView;
import android.widget.SimpleAdapter;
import dk.andsen.utils.SQLTools;
import dk.andsen.utils.Utils;
//extends ListActivity
public class SQLViewer extends Activity implements OnClickListener, Runnable {

	private String _dbPath;
	private ProgressDialog _pd;
//	private FileReader _f;
//	private BufferedReader _in;
	private ListView _lv;
	private static final int MENU_RUN = 0;
	private static final int RUN_STATEMENT = 1;
	private int _dialogClicked;
	private Database _db;
	private String _scriptPath;
	private SQLViewer _cont;
	protected String _sql;
	private boolean logging;
	
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		logging = Prefs.getLogging(this);
		setContentView(R.layout.sqlviewer);
		Bundle extras = getIntent().getExtras();
		_cont = this;
		if(extras !=null)
		{
			_dbPath = extras.getString("db");
			Utils.logD("Path to database: " + _dbPath, logging);
			_scriptPath = extras.getString("script");
			Utils.logD("Path to script: " + _scriptPath, logging);
			_db = new Database(_dbPath, this);
			Utils.logD("Opening SQL file " + _scriptPath, logging);
			_pd = ProgressDialog.show(this, getString(R.string.Working),
					getString(R.string.ReadingScript), true, false);
			Utils.logD("Fetching SQLListView", logging);
	    _lv = (ListView)findViewById(R.id.SQLListView);

	    List<String> statements = SQLTools.parseSQLFile(_cont, _scriptPath);
	    
//	    final ArrayList<HashMap<String, String>> mylist = new ArrayList<HashMap<String, String>>();
//			HashMap<String, String> hmSql;
//	    try {
//	    	String nl = "\n";
//				_f = new FileReader(_scriptPath);
//				_in = new BufferedReader(_f);
//				Utils.logD("Importing from; " + _scriptPath, logging);
//				String line = "";
//				String nline;
//				boolean inTrigger = false;
//				// put each statement in the list
//				while ((nline = _in.readLine()) != null) {
//					line += nline;
//					// starting to read a trigger definition?
//					if (nline.trim().toUpperCase(Locale.US).startsWith("CREATE TRIGGER")) {
//						inTrigger = true;
//					}
//					// if more of statement coming append newline
//					if (!(line.endsWith(";") || line.equals("")) || inTrigger)
//						line += nl;
//		      if(line.startsWith("--")) {
//		        // It a comment just empty line
//						hmSql = new HashMap<String, String>();
//						hmSql.put("Sql", line);
//						mylist.add(hmSql);
//		      	line = "";
//		      } else if((nline.trim().endsWith(";") && !inTrigger) || 
//		      		(inTrigger && nline.trim().toUpperCase(Locale.US).endsWith("END;"))) {
//		        // If line ends with ";" or "end;" if it is a trigger we have a statement
//		      	// ready to execute
//						inTrigger = false;
//		      	line = line.substring(0, line.length() - 1);
//		      	//Utils.logD("SQL: " + line, logging);
//						hmSql = new HashMap<String, String>();
//						hmSql.put("Sql", line);
//						mylist.add(hmSql);
//		      	line = "";
//		      }
//				}
//		    _in.close();
//		    _f.close();
//	    }  catch (Exception e) {
//	    	Utils.logD("Exception!", logging);
//	    }
//			Utils.logD("All lines read", logging);

	    final ArrayList<HashMap<String, String>> mylist = new ArrayList<HashMap<String, String>>();
			HashMap<String, String> hmSql;
			for (String line: statements) {
				hmSql = new HashMap<String, String>();
				hmSql.put("Sql", line);
				mylist.add(hmSql);
			}
			SimpleAdapter mSchedule = new SimpleAdapter(this, mylist,
					R.layout.sql_line, new String[] {"Sql"},
					new int[] { R.id.Sql});
			
			_lv.setAdapter(mSchedule);
			_lv.setOnItemClickListener(new OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View v, int position,
						long id) {
					_dialogClicked = RUN_STATEMENT;
					HashMap<String, String> map = mylist.get(position);
					Collection<String> m =  map.values();
					Iterator<String> itv = m.iterator();
					while (itv.hasNext()) {
						String str = (String) itv.next();
					    Utils.logD("Element: " + str, logging);
					    _sql = str;
					}
					Dialog executeLine = new AlertDialog.Builder(_cont)
					.setTitle(getText(R.string.ExecuteStatement))
					.setMessage(_sql)
					.setPositiveButton(getText(R.string.OK), new DialogButtonClickHandler())
					.setNegativeButton(getText(R.string.Cancel), null)
					.create();
					executeLine.show();
				}
			});
			Utils.logD("Adapter finished", logging);
			Thread thread = new Thread(this);
			thread.start();
		}		
	}
	
	private Handler handler = new Handler() {
		@Override
		public void handleMessage(Message msg) {
			_pd.dismiss();
		}
	};

	public void run() {
		handler.sendEmptyMessage(0);
	}
	
	public boolean onCreateOptionsMenu(Menu menu) {
		menu.add(0, MENU_RUN, 0, R.string.Run);
		return true;
	}

	@Override
	protected Dialog onCreateDialog(int id) 
	{
		String title = "";
		Dialog test = null;
		switch (id) {
		case MENU_RUN:
			title = "Execute SQL script";
			Utils.logD("Creating MENU_RUN", logging);
			title = getText(R.string.Run).toString();
			test = new AlertDialog.Builder(this)
			.setTitle(title)
			.setPositiveButton(getText(R.string.OK), new DialogButtonClickHandler())
			.create();
		}
		return test;
	}
	
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case MENU_RUN:
			_dialogClicked = MENU_RUN; 
			showDialog(MENU_RUN);
			break;
		}
		return false;
	}
	
	/**
	 * @author os
	 *
	 */
	public class DialogButtonClickHandler implements DialogInterface.OnClickListener {

		public void onClick( DialogInterface dialog, int clicked )
		{
			Utils.logD("Dialog: " + dialog.getClass().getName(), logging);
			switch(clicked)
			{
			case DialogInterface.BUTTON_POSITIVE:
				switch (_dialogClicked) {
				case MENU_RUN:
					Utils.logD("Ready to run " + _scriptPath, logging);
					_db.runScript(new File(_scriptPath));
					break;
				case RUN_STATEMENT:
					Utils.logD("Execute statement: " + _sql, logging);
					_db.executeStatement(_sql, _cont);
					break;
				}
				break;
			}
		}
	}

	public void onClick(View v) {
		Utils.logD("SQLViewer onClick called!", logging);
	}
}