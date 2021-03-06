package comp3111h.anytaxi.customer;

import java.io.IOException;

import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;

import com.appspot.hk_taxi.anyTaxi.AnyTaxi;
import com.appspot.hk_taxi.anyTaxi.model.Customer;
import com.appspot.hk_taxi.anyTaxi.model.GeoPt;
import com.appspot.hk_taxi.anyTaxi.model.Transaction;
import com.google.api.client.extensions.android.http.AndroidHttp;
import com.google.api.client.json.jackson2.JacksonFactory;

public class RequestToTrackingActivity extends ActionBarActivity {

	private final static String TAG = "LoadingDriverAsyncTask";

	// Tracking the progress of finding near drivers
	private ProgressBar mProgressBar;
	// Delay before sending next inquiry to server
	private int mDelay = 5000;
	Customer customer;
	static AnyTaxi endpoint;

	// The first transaction contains customer info only
	Transaction customerInfoTrans;
	// The second transaction contains customer info plus transaction ID
	Transaction initialTrans;
	// The final transaction contains drivers info as well
	public Transaction returnedTrans;

	// Conform to the variables provided in Transaction class on server side
	private String customerEmail;

	private String customerLocStr;
	private String destLocStr;

	private GeoPt customerLoc;
	private GeoPt destLoc;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_request_to_tracking);

		if (savedInstanceState == null) {
			getSupportFragmentManager().beginTransaction()
					.add(R.id.container, new PlaceholderFragment()).commit();

			// To get the customer information passed by RequestActivity
			Bundle preIntentBundle = getIntent().getExtras();

			GeoPt tempPt = new GeoPt();
			tempPt.setLatitude((float) preIntentBundle.getDouble("LAT"));
			tempPt.setLongitude((float) preIntentBundle.getDouble("LON"));
			

			endpoint = CloudEndpointUtils.updateBuilder(
					new AnyTaxi.Builder(AndroidHttp.newCompatibleTransport(),
							new JacksonFactory(), null)).build();

			customerEmail = preIntentBundle.getString("EMAIL");
			customerLocStr = preIntentBundle.getString("CURADD");
			customerLoc = tempPt;
			destLocStr = preIntentBundle.getString("DEST");
			destLoc = new GeoPt();
			destLoc.setLatitude((float) (0));
			destLoc.setLongitude((float) 0);

		}

		mProgressBar = (ProgressBar) findViewById(R.id.loadingdriverprogress);
		customer = Utils.getCustomer(this);

		new LoadingDriverTask().execute();
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {

		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.request_to_tracking, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		// Handle action bar item clicks here. The action bar will
		// automatically handle clicks on the Home/Up button, so long
		// as you specify a parent activity in AndroidManifest.xml.
		int id = item.getItemId();
		if (id == R.id.action_settings) {
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	/**
	 * A placeholder fragment containing a simple view.
	 */
	public static class PlaceholderFragment extends Fragment {

		public PlaceholderFragment() {
		}

		@Override
		public View onCreateView(LayoutInflater inflater, ViewGroup container,
				Bundle savedInstanceState) {
			View rootView = inflater.inflate(
					R.layout.fragment_request_to_tracking, container, false);
			return rootView;
		}
	}

	class LoadingDriverTask extends AsyncTask<Void, Integer, String> {

		@Override
		protected void onPreExecute() {
			mProgressBar.setVisibility(ProgressBar.VISIBLE);
		}

		@Override
		protected String doInBackground(Void... none) {

			if (endpoint == null) {
				ConnectionUtils.showError(RequestToTrackingActivity.this,
						"endpoint is null!");
			}

			customerInfoTrans = new Transaction();

			customerInfoTrans.setCustomerEmail(customerEmail);
			customerInfoTrans.setCustomerLocStr(customerLocStr);
			customerInfoTrans.setCustomerLoc(customerLoc);
			customerInfoTrans.setDestLocStr(destLocStr);
			// customerInfoTrans.setDestLoc(destLoc);
			assert customerInfoTrans.getCustomerLoc() != null;

			try {
				initialTrans = endpoint.addTransaction(
						Utils.customer.getEmail(), customerInfoTrans).execute();
			} catch (IOException e) {
				e.printStackTrace();
			}
			// simulating long-running operation
			returnedTrans = new Transaction();
			returnedTrans.setDriverEmail(null);
			Integer i = 0;
			
			
			do {
				i++;
				try {
					returnedTrans = endpoint.getTransaction(
							Utils.customer.getEmail(), initialTrans.getId())
							.execute();
				} catch (IOException e) {
					e.printStackTrace();
				}
				i = (i >= 9) ? 9 : i;
				publishProgress(i * 10);
				sleep();
			} while ((returnedTrans == null
					|| returnedTrans.getDriverEmail() == null)&&i<=6);

			if(i>=6)
			{
				return null;
			}
			else
			{
				publishProgress(Integer.valueOf(100));
				return returnedTrans.getDriverEmail();
			}
			

		}

		@Override
		protected void onProgressUpdate(Integer... values) {
			mProgressBar.setProgress(values[0]);
		}

		@Override
		protected void onPostExecute(String driverEmail) {
			mProgressBar.setVisibility(ProgressBar.INVISIBLE);

			if(driverEmail == null)
			{
				new AlertDialog.Builder(RequestToTrackingActivity.this)
			    .setTitle("Not Found")
			    .setMessage("No Driver Found, Go Back And Send Another Request")
			    .setPositiveButton(android.R.string.yes, new DialogInterface.OnClickListener() {
			        public void onClick(DialogInterface dialog, int which) { 
			        	RequestToTrackingActivity.super.onBackPressed();
			            // continue with delete
			        }
			     })
			    .setIcon(android.R.drawable.ic_dialog_alert)
			     .show();
				
				return;
			}
			
			Bundle driverInfo = new Bundle();
			driverInfo.putString("Email", driverEmail);

			Intent intent = new Intent(RequestToTrackingActivity.this,
					TrackingActivity.class);
			if (returnedTrans != null) {
				String.valueOf(returnedTrans.getId());
				intent.putExtra(Utils.PREFS_TRANSACTION_KEY, returnedTrans.getId());
			}
			intent.putExtras(driverInfo);
			startActivity(intent);
			finish();
		}

		private void sleep() {
			try {
				Thread.sleep(mDelay);
			} catch (InterruptedException e) {
				Log.e(TAG, e.toString());
			}
		}
	}

}
