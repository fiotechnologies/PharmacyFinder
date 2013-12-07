package com.fio.pharmacyfinder;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.Color;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.ContextMenu;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.AutoCompleteTextView;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;
import java.io.IOException;
import java.util.List;
import java.util.Locale;

public class MainActivity extends Activity {

	private static final long MIN_DISTANCE_FOR_UPDATE = 1000;// IN METERS
	private static final long MIN_TIME_FOR_UPDATE = 1000 * 60 * 15;// IN MILLI
																	// SECONDS
	private static final String ZIP_CODE = "zipcode";
	private static final String LOCALITY = "locality";
	private static final String STREET = "street";
	protected LocationManager locationManager;
	private String locationProvider;
	private TextView locationLable;
	private ProgressBar addressProgress;
	private String zipCode;
	private String locality;
	private String street;
	private final String LOCATION_MSG = "Showing results for ";
	private final String NORESULT_MSG = "No Results found for ";
	private static final int MAX_NO_OF_ATTEMPTS = 5;
	private AlertDialog alertDialog;
	private Boolean flag = false;
	String provider = null;

	/**
	 * Called when the activity is first created.
	 */
	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);
		addressProgress = (ProgressBar) findViewById(R.id.addressProgress);
		locationLable = (TextView) findViewById(R.id.locationLable);
		if (savedInstanceState == null) {
			fetchLocation();
		}
	}

	private void fetchLocation() {
		locationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);

		locationProvider = getAvailableLocationProvider(locationManager);

		if (locationProvider == null || locationProvider.trim().equals("")) {
			// showZipcodeAlert(MainActivity.this, null);

		} else {
			showPharmaciesForLocation();
		}
	}

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		outState.putString(ZIP_CODE, this.zipCode);
		outState.putString(LOCALITY, this.locality);
		outState.putString(STREET, this.street);
		super.onSaveInstanceState(outState);
	}

	@Override
	protected void onRestoreInstanceState(Bundle savedInstanceState) {
		super.onRestoreInstanceState(savedInstanceState);
		setContentView(R.layout.main);
		addressProgress = (ProgressBar) findViewById(R.id.addressProgress);
		locationLable = (TextView) findViewById(R.id.locationLable);
		this.zipCode = savedInstanceState.getString(ZIP_CODE);
		this.locality = savedInstanceState.getString(LOCALITY);
		this.street = savedInstanceState.getString(STREET);
		if (this.zipCode == null && this.locality == null
				&& this.street == null) {
			fetchLocation();
		} else {
			showPharmacyList(this.zipCode, this.locality, this.street);
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.location_refresh:
			clearOldData();
			fetchLocation();
			//showPharmaciesForLocation();
			return true;
		case R.id.search:
			clearOldData();
			showZipcodeAlert(MainActivity.this, null);
		default:
			return super.onOptionsItemSelected(item);
		}
	}

	@Override
	public void onCreateContextMenu(ContextMenu menu, View v,
			ContextMenu.ContextMenuInfo menuInfo) {
		super.onCreateContextMenu(menu, v, menuInfo);
		if (v instanceof ViewGroup) {
			Pharmacy pharmacy = getAddressFromListItem((ViewGroup) v);
			menu.setHeaderTitle("Options");
			menu.add(0, v.getId(), 0, "Call");
			menu.add(0, v.getId(), 1, "Show Map");
			Intent callIntent = createCallIntent(pharmacy.getPhone1());
			(menu.getItem(0)).setIntent(callIntent);
			Intent mapIntent = createMapIntent(pharmacy);
			(menu.getItem(1)).setIntent(mapIntent);
		}

	}

	@Override
	public boolean onContextItemSelected(MenuItem item) {
		startActivity(item.getIntent());
		return true;
	}

	public void callPharmacy(View view) {
		ViewParent subparent = view.getParent();
		ViewParent parent = subparent.getParent();
		Pharmacy pharmacy;
		if (parent instanceof ViewGroup) {
			pharmacy = getAddressFromListItem((ViewGroup) parent);
			startActivity(createCallIntent(pharmacy.getPhone1()));
		}
	}

	public void showMap(View view) {
		ViewParent subparent = view.getParent();
		ViewParent parent = subparent.getParent();
		Pharmacy pharmacy;
		if (parent instanceof ViewGroup) {
			pharmacy = getAddressFromListItem((ViewGroup) parent);
			startActivity(createMapIntent(pharmacy));
		}
	}

	protected void showPharmaciesForLocation() {
		LocationListener locationListner = new PharmacyLocationListner();
		locationManager.requestLocationUpdates(locationProvider,
				MIN_TIME_FOR_UPDATE, MIN_DISTANCE_FOR_UPDATE, locationListner);
		Location location = locationManager
				.getLastKnownLocation(locationProvider);
		if (location == null) {
			location = tryToFindLocationAgain();
		}
	//	getResultsForUpdatedLocation(location);
	}

	private String getAvailableLocationProvider(LocationManager locationManager) {
		/*
		 * Criteria criteria = new Criteria();
		 * criteria.setAccuracy(Criteria.ACCURACY_COARSE); provider =
		 * locationManager.getBestProvider(criteria, true);
		 */
		flag = displayGpsStatus();
		if (flag) {
			List<String> providers = locationManager.getProviders(true);
			for (String str : providers) {
				if (str.equals(LocationManager.NETWORK_PROVIDER)) {
					provider = str;
					return provider;
				} else {
					provider = str;
				}
			}
		} else {
			alertbox("Gps Status!!", "Your GPS is: OFF");
		}
		return provider;
	}

	private Boolean displayGpsStatus() {
		// TODO Auto-generated method stub
		ContentResolver contentResolver = getBaseContext().getContentResolver();
		boolean gpsStatus = Settings.Secure.isLocationProviderEnabled(
				contentResolver, LocationManager.GPS_PROVIDER);
		if (gpsStatus) {
			return true;
		} else {
		}
		return false;
	}

	private void alertbox(String title, String mymessage) {
		AlertDialog.Builder builder = new AlertDialog.Builder(this);
		builder.setMessage("Your Device's GPS is Disable")
				.setCancelable(false)
				.setTitle("** Gps Status **")
				.setPositiveButton("Gps On",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								Intent myIntent = new Intent(
										Settings.ACTION_LOCATION_SOURCE_SETTINGS);
								dialog.cancel();
								startActivity(myIntent);
								}
						})
				.setNegativeButton("Cancel",
						new DialogInterface.OnClickListener() {
							public void onClick(DialogInterface dialog, int id) {
								// cancel the dialog box
								dialog.cancel();
								showZipcodeAlert(MainActivity.this, null);
							}
						});
		AlertDialog alert = builder.create();
		alert.show();
	}
	private void showZipcodeAlert(Context ctx, String message) {
		final AlertDialog.Builder alert = new AlertDialog.Builder(ctx);
		alert.setIcon(R.drawable.ic_launcher);
		alert.setTitle("Enter Your Area Information");
		alert.setMessage(message != null && !message.trim().equals("") ? message
				+ "\nEnter your Zip Code or Area"
				: "Enter your Zip Code or Area");
		LayoutInflater inflater = getLayoutInflater();
		final View zipAlertView = inflater.inflate(R.layout.zip_alert, null);
		alert.setView(zipAlertView);
		final EditText zipCodeText = (EditText) zipAlertView
				.findViewById(R.id.zipCode);
		final AutoCompleteTextView areaText = (AutoCompleteTextView) zipAlertView
				.findViewById(R.id.locality);
		final AutoCompleteTextView streetText = (AutoCompleteTextView) zipAlertView
				.findViewById(R.id.street);
		String Areas[] = getResources().getStringArray(R.array.area_array);
		String Streets[] = getResources().getStringArray(R.array.street_array);
		ArrayAdapter<String> adapter = new ArrayAdapter<String>(this,
				android.R.layout.simple_dropdown_item_1line, Areas);
		areaText.setAdapter(adapter);
		areaText.setThreshold(3);
		ArrayAdapter<String> adapter1 = new ArrayAdapter<String>(this,
				android.R.layout.simple_dropdown_item_1line, Streets);
		streetText.setAdapter(adapter1);
		streetText.setThreshold(3);
		// To clear any error message set on the Zip code field
		areaText.addTextChangedListener(new TextWatcher() {
			public void beforeTextChanged(CharSequence s, int start, int count,
					int after) {
			}
			public void onTextChanged(CharSequence s, int start, int before,
					int count) {
				zipCodeText.setError(null);
				// MainActivity.this.adapter.getFilter().filter(s);
			}
			public void afterTextChanged(Editable s) {
			}
		});
		final Button okButton = (Button) zipAlertView
				.findViewById(R.id.zipAlertOKButton);
		okButton.setOnClickListener(new View.OnClickListener() {
			public void onClick(View v) {
				if (v == okButton) {
					zipCode = zipCodeText.getText().toString();
					locality = areaText.getText().toString();
					street = streetText.getText().toString();
					zipCode = zipCode != null && zipCode.trim().equals("") ? null
							: zipCode;
					locality = locality != null && locality.trim().equals("") ? null
							: locality;
					street = street != null && street.trim().equals("") ? null
							: street;

					if (zipCode == null && locality == null && street == null) {
						zipCodeText
								.setError("Zipcode or Area or street is required");
					} else {
						closeZipAlert();
						showPharmacyList(zipCode, locality, street);
					}
				}
			}
		});
		alertDialog = alert.show();
	}
	public void showPharmacyList(String zipCode, String locality, String street) {
		closeZipAlert();
		this.zipCode = zipCode;
		this.locality = locality;
		this.street = street;
		DbAdapter dbAdapter = new DbAdapter(MainActivity.this);
		dbAdapter.open();
		ListView listView = (ListView) findViewById(R.id.pharmacy_list);
		Cursor cursor = dbAdapter.fetchListItems(zipCode, locality, street);
		if (cursor != null && cursor.getCount() > 0) {
			String[] from = { DbAdapter.PHARM_NAME, DbAdapter.PHARM_ADDRESS,
					DbAdapter.LOCATION, DbAdapter.STREET, DbAdapter.ZIPCODE,
					DbAdapter.PHARM_PHONE1 };
			int[] to = { R.id.pharm_name, R.id.pharm_address, R.id.location,
					R.id.street, R.id.zipcode, R.id.pharm_phone1 };
			SimpleCursorAdapter cursorAdapter = new SimpleCursorAdapter(
					MainActivity.this, R.layout.pharmacy_list, cursor, from,
					to, 1);
			listView.setAdapter(cursorAdapter);
			listView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
				public void onItemClick(AdapterView<?> parent, View view,
						int position, long id) {
					registerForContextMenu(view);
					openContextMenu(view);
				}
			});
			showResultMessage(zipCode, locality, street, LOCATION_MSG, true);
		} else {
			listView.setAdapter(new ArrayAdapter<String>(MainActivity.this,
					R.layout.empty_list,
					new String[] { "Try again with better search parameters!" }));
			showResultMessage(zipCode, locality, street, NORESULT_MSG, false);
		}
	}
	private void clearOldData() {
		locationLable.setText("");
		TextView noResult = (TextView) findViewById(R.id.no_results);
		if (noResult != null && noResult.getVisibility() == View.VISIBLE) {
			noResult.setText("");
			noResult.setVisibility(View.GONE);
		}
	}

	private void showResultMessage(String zipCode, String locality,
			String street, String message, boolean success) {
		message = message
				+ ((zipCode != null && !zipCode.trim().equals("")) ? "Zip Code : "
						+ zipCode
						: "")
				+ " "
				+ ((locality != null && !locality.trim().equals("")) ? "Area : "
						+ locality
						: "")
				+ " "
				+ ((street != null && !street.trim().equals("")) ? "Street : "
						+ street : "");
		if (message.trim().endsWith("for")) {
			message = message.trim().replace("for", "");
		}
		locationLable.setText(message);
		if (success) {
			locationLable.setTextColor(Color.parseColor("#1920FA"));
		} else {
			locationLable.setTextColor(Color.parseColor("#FA0A1A"));
		}
	}

	protected void getResultsForUpdatedLocation(Location location) {
		if (location != null) {
			if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.GINGERBREAD
					&& Geocoder.isPresent()) {
				(new GetAddressTask(this)).execute(location);

			} else {
				showZipcodeAlert(this, "Your Phone does not support Geocoder");
			}
		} else {
			showZipcodeAlert(this, "Your location could not be determined");
		}
	}

	private Location tryToFindLocationAgain() {
		int noOfAttempts = 0;
		Location location = null;
		while (noOfAttempts <= MAX_NO_OF_ATTEMPTS) {
			try {
				Thread.sleep(1000 * 2);
				location = locationManager
						.getLastKnownLocation(locationProvider);
				if (location != null) {
					break;
				}
				noOfAttempts++;
			} catch (InterruptedException ex) {
				// do nothing
			}
		}
		return location;
	}

	private void closeZipAlert() {
		if (alertDialog != null && alertDialog.isShowing()) {
			alertDialog.hide();
		}
	}

	private Intent createCallIntent(CharSequence phNo) {
		if (phNo != null && !phNo.toString().trim().equals("")) {
			Intent callIntent = new Intent(Intent.ACTION_CALL);
			callIntent.setData(Uri.parse("tel:" + phNo));
			return callIntent;
		}
		return null;
	}

	private Pharmacy getAddressFromListItem(ViewGroup v) {
		if (v == null) {
			throw new IllegalArgumentException(
					"View object in the list item is null");
		}
		Pharmacy pharmacy = new Pharmacy();
		StringBuilder address = new StringBuilder();
		ViewGroup listItem;

		listItem = (ViewGroup) v;
		View phoneView = listItem.getChildAt(6);
		if (phoneView instanceof TextView) {
			pharmacy.setPhone1(((TextView) phoneView).getText().toString());
		}

		View addressView = listItem.getChildAt(1);
		if (addressView instanceof TextView) {
			address.append(((TextView) addressView).getText());
		}
		View streetView = listItem.getChildAt(2);
		if (streetView instanceof TextView) {
			address.append(",");
			address.append(((TextView) streetView).getText());
		}
		View locationView = listItem.getChildAt(3);
		if (locationView instanceof TextView) {
			address.append(",");
			address.append(((TextView) locationView).getText());
		}

		View zipCodeView = listItem.getChildAt(5);
		if (zipCodeView instanceof TextView) {
			address.append(",");
			address.append(((TextView) zipCodeView).getText());
		}

		pharmacy.setAddress(address.toString());
		return pharmacy;
	}

	private Intent createMapIntent(Pharmacy pharmacy) {
		Intent mapIntent = new Intent(Intent.ACTION_VIEW,
				Uri.parse("geo:0,0?q=" + pharmacy.getAddress()));
		return mapIntent;
	}

	private class PharmacyLocationListner implements LocationListener {

		public void onLocationChanged(Location location) {
			getResultsForUpdatedLocation(location);
			locationManager.removeUpdates(this);
		}

		public void onStatusChanged(String provider, int status, Bundle extras) {
			// Toast.makeText(MainActivity.this, "Provider status changed",
			// Toast.LENGTH_LONG).show();
		}

		public void onProviderEnabled(String provider) {
			// Toast.makeText(MainActivity.this,
			// "Provider enabled by the user. " + provider + " turned on",
			// Toast.LENGTH_LONG).show();
		}

		public void onProviderDisabled(String provider) {
			// Toast.makeText(MainActivity.this,
			// "Provider disabled by the user. " + provider + " turned off",
			// Toast.LENGTH_LONG).show();
		}
	}

	private class GetAddressTask extends AsyncTask<Location, Void, Address> {

		Context context;

		public GetAddressTask(Context context) {

			super();
			this.context = context;
		}

		@Override
		protected void onPreExecute() {
			addressProgress.setVisibility(View.VISIBLE);
		}

		@Override
		protected Address doInBackground(Location... params) {
			Geocoder geocoder = new Geocoder(context, Locale.getDefault());
			Location loc = params[0];
			List<Address> addresses = null;
			if (Geocoder.isPresent()) {
				try {
					addresses = geocoder.getFromLocation(loc.getLatitude(),
							loc.getLongitude(), 1);

				} catch (IOException ex) {
					// do nothing
				}
			}

			if (addresses != null && addresses.size() > 0) {
				Address address = addresses.get(0);
				return address;
			} else {
				return null;
			}
		}

		@Override
		protected void onPostExecute(Address result) {
			addressProgress.setVisibility(View.GONE);
			if (result != null) {

				showPharmacyList(result.getPostalCode(),
						result.getSubLocality(), result.getThoroughfare());
			} else {
				showZipcodeAlert(context,
						"Your Address could not be determined");
			}
		}
	}

}