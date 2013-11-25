package com.fio.pharmacyfinder;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DbAdapter {

    private static final String DB_PATH = "/data/data/com.fio.pharmacyfinder/databases/";
    private static final String DB_NAME = "PharmacyfinderDatabase";
    private static final String TABLE_ITEMS = "pharmacy_finder";
    public static final String PHARM_ID = "_id";
    public static final String PHARM_NAME = "pharmacy_name";
    public static final String PHARM_ADDRESS = "pharmacy_address";
    public static final String PHARM_PHONE1 = "pharmacy_phone1";
    public static final String STREET = "street";
    public static final String LOCATION = "location";
    public static final String ZIPCODE = "zipcode";
    private static final int DB_VERSION = 1;
    private final Context mCtx;
    private DatabaseHelper mDbHelper;
    private SQLiteDatabase mDb;
    private Map<String,String> shortStrings = new HashMap<String,String>();

    private static class DatabaseHelper extends SQLiteOpenHelper {

        private Context ctx = null;

        DatabaseHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
            this.ctx = context;
            createDatabase();
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        }

        private void createDatabase() {
            boolean dbExists = isDatabaseAlreadyAvailable();
            if (!dbExists) {
                this.getWritableDatabase();
                copyDatabase();
            }
        }

        private boolean isDatabaseAlreadyAvailable() {
            SQLiteDatabase db;
            try {
                db = SQLiteDatabase.openDatabase(DB_PATH + DB_NAME, null, SQLiteDatabase.OPEN_READONLY);
            } catch (Exception e) {
                db = null;
            }
            if (db != null) {
                db.close();
                return true;
            } else {
                return false;
            }
        }

        private void copyDatabase() {
            InputStream inputStream = null;;
            String outFileName = null;;
            OutputStream outStream = null;
            try {
                inputStream = ctx.getAssets().open(DB_NAME);
                outFileName = DB_PATH + DB_NAME;
                outStream = new FileOutputStream(outFileName);
                byte[] buffer = new byte[1024];
                int len;
                while ((len = inputStream.read(buffer)) > 0) {
                    outStream.write(buffer, 0, len);
                }
            } catch (IOException ex) {
                Logger.getLogger(DbAdapter.class.getName()).log(Level.SEVERE, null, ex);
            } finally {
                try {
                    outStream.flush();
                    outStream.close();
                    inputStream.close();
                } catch (IOException ex) {
                    Logger.getLogger(DbAdapter.class.getName()).log(Level.SEVERE, null, ex);
                }

            }
        }
    }

    /**
     * Constructor - takes the context to allow the database to be
     * opened/created
     *
     * @param ctx the Context within which to work
     */
    public DbAdapter(Context ctx) {
        this.mCtx = ctx;
        shortStrings.put("rd","road");
        shortStrings.put("rd.","road");
        shortStrings.put("ave","avenue");
        shortStrings.put("ave.","avenue");
        shortStrings.put("st","street");
        shortStrings.put("st.","street");
    }

    public DbAdapter open() throws SQLException {
        mDbHelper = new DatabaseHelper(mCtx);
        mDb = mDbHelper.getReadableDatabase();
        return this;
    }

    public void close() {
        mDbHelper.close();
    }

    public Cursor fetchListItems(String zipCode,String locality, String thoroughfare) {
        String where = null;
        String[] whereArgs = null;
        if(thoroughfare != null){
        	thoroughfare = eloborateStreet(thoroughfare);
        }
       /* if(zipCode != null && !zipCode.trim().equals("") 
                && locality != null &&  !locality.trim().equals("")&& street != null &&  !street.trim().equals("")){
            where = ZIPCODE+"=? or upper("+LOCATION+") like upper(?) or upper("+STREET+") like upper(?)";
            whereArgs = new String[]{zipCode,"%"+locality+"%","%"+street+"%"};
        }else*/ 
         
        
          if(zipCode != null && !zipCode.trim().equals("")&& locality != null &&  !locality.trim().equals("")){
            where = ZIPCODE+"=? and upper("+LOCATION+") like upper(?)";
            whereArgs = new String[]{zipCode,"%"+locality+"%"};
        }else if(locality != null && !locality.trim().equals("")&& thoroughfare != null && !thoroughfare.trim().equals("")){
            where = "upper("+LOCATION+") like upper(?) and upper("+STREET+") like upper(?)";
            whereArgs = new String[]{"%"+locality+"%","%"+thoroughfare+"%"}; 
        }else if(zipCode != null && !zipCode.trim().equals("")){
            where = ZIPCODE+"=?";
            whereArgs = new String[]{zipCode};
        }else if(locality != null && !locality.trim().equals("")){
            where = "upper("+LOCATION+") like upper(?)";
            whereArgs = new String[]{"%"+locality+"%"};
        
        }else if(thoroughfare != null && !thoroughfare.trim().equals("")){
            where = "upper("+STREET+") like upper(?)";
            whereArgs = new String[]{"%"+thoroughfare+"%"};
        }else{
            where = ZIPCODE+"=?";
            whereArgs = new String[]{""};
        }
        Cursor cursor =
                mDb.query(TABLE_ITEMS, new String[]{PHARM_ID, PHARM_NAME, PHARM_ADDRESS, PHARM_PHONE1, LOCATION,STREET, ZIPCODE},
                where, whereArgs, null, null, PHARM_NAME+" asc");

        if (cursor != null) {
            cursor.moveToFirst();            
        }
        return cursor;
    }
    
    private String eloborateStreet(String street){
        for(String key : shortStrings.keySet()){
         if(street.contains(key)){
    		return street.replace(key, shortStrings.get(key));
            }   
        }    	
    	return street;
    }
}
