/**
 * @copyright 2012 City of Bloomington, Indiana
 * @license http://www.gnu.org/licenses/gpl.txt GNU/GPL, see LICENSE.txt
 * @author Cliff Ingham <inghamn@bloomington.in.gov>
 */
package gov.in.bloomington.open311.view;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.apache.http.NameValuePair;
import org.apache.http.message.BasicNameValuePair;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import gov.in.bloomington.open311.R;
import gov.in.bloomington.open311.controller.GeoreporterAPI;
import gov.in.bloomington.open311.controller.GeoreporterUtils;
import gov.in.bloomington.open311.controller.ServicesItem;
import gov.in.bloomington.open311.model.ExternalFileAdapter;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.telephony.TelephonyManager;
import android.text.format.Time;
import android.util.Log;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnFocusChangeListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;

public class NewReport extends Activity implements OnClickListener  {
		//for threading
		private Thread thread_service;
		private JSONArray jar_attributes;
		private int id;
		private int last_id = 0;
		private int i;
		private int n_rb;
		private RelativeLayout r0;
		private String server_name;
		
		
		private ImageView img_photo;
		private EditText edt_newReport;
		private EditText edt_firstName;
		private EditText edt_lastName;
		private EditText edt_email;
		private EditText edt_phone;
		private Button btn_send;
		private TextView failed;
		private Button btn_service;
		private Button btn_picture;
		private Button btn_location;

		private String content;
		Bitmap photo = null;
		private static final int CAMERA_REQUEST = 1888; 
		private static final int GALLERY_REQUEST = 1889; 
		
		//for location
		private LocationManager lm;
		private LocationListener ll;
		private Location loc;
		private double longitude;
		private double latitude;
		
		//for send report
		private String server_jurisdiction_id;
		private String service_code;
		private String service_name;
		private String first_name;
		private String last_name;
		private String email;
		private String phone;
		private String device_id;
		private String attribute_content;
		private JSONArray jar_services_global;
		private JSONArray reply;
		
		SharedPreferences pref;
		
		/** Called when the activity is first created. */
	    @Override
	    protected void onCreate(Bundle savedInstanceState) {
	        super.onCreate(savedInstanceState);
	        setContentView(R.layout.report);
	        
	      //for acquiring location
		    lm = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
			ll = new MyLocationListener();
			
			//if GPS available, use GPS -- first priority
			if (lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
				lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, ll);
			}
			//if network provider available, use network provider
			if (lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER))  {
				lm.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, ll);
			}
	        
	        //for click listener
		    img_photo = (ImageView) findViewById(R.id.img_photo);
		    img_photo.setOnClickListener((OnClickListener) this);
		    
		    edt_newReport = (EditText) findViewById(R.id.edt_newReport);
		    edt_newReport.setOnFocusChangeListener(new OnFocusChangeListener() {
				public void onFocusChange(View v, boolean hasFocus) {
					if (edt_newReport.getText().toString().equals("Fill your report here")) {
			        	edt_newReport.setText(""); 
					}
					 edt_newReport.setTextColor(Color.BLACK);
				}
			});
		    
		    btn_send = (Button) findViewById(R.id.btn_submit);
		    btn_send.setOnClickListener((OnClickListener)this);
		    
			failed = (TextView) findViewById(R.id.txt_SendingFailed);
			failed.setVisibility(TextView.GONE);
			
			btn_service = (Button) findViewById(R.id.btn_service);
			btn_service.setOnClickListener((OnClickListener)this);
			
			btn_picture = (Button) findViewById(R.id.btn_picture);
			btn_picture.setOnClickListener((OnClickListener)this);
			
			btn_location = (Button) findViewById(R.id.btn_location);
			btn_location.setOnClickListener((OnClickListener)this);
			
	    }
	    
	    
	    public void onClick(View v) {
			// TODO Auto-generated method stub
		    content = edt_newReport.getText().toString();

			switch (v.getId()) {
			case R.id.btn_location:
				Intent intent = new Intent(NewReport.this, LocationMap.class);
				intent.putExtra("longitude", longitude);
				intent.putExtra("latitude", latitude);
	            startActivity(intent);
	            break;
			
			case R.id.btn_picture:
				AlertDialog.Builder builder = new AlertDialog.Builder(NewReport.this);
				builder.setMessage("Choose Source of Picture")
				       .setPositiveButton("Camera", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        	   Intent cameraIntent = new Intent(android.provider.MediaStore.ACTION_IMAGE_CAPTURE); 
				               startActivityForResult(cameraIntent, CAMERA_REQUEST);
				           }
				       })
				       .setNeutralButton("Gallery", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				        	   Intent i = new Intent(Intent.ACTION_PICK, android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
				   			   startActivityForResult(i, GALLERY_REQUEST);
				           }
				       })
				       .setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
				           public void onClick(DialogInterface dialog, int id) {
				    	    	dialog.cancel();
				           }
				       });
				AlertDialog alert = builder.create();
				alert.show();
	            break;
	        
			case R.id.btn_submit:			
				    	    
				String service_request_id = null;
				
				//check whether report is filled
	    	    if (!content.equals("") && !content.equals("Fill your report here")) {
	    	    	first_name = edt_firstName.getText().toString();
	    	    	last_name = edt_lastName.getText().toString();
	    	    	email = edt_email.getText().toString();
	    	    	phone = edt_phone.getText().toString();
	    	    	
	    	    	//to get device id
	    	    	TelephonyManager telephonyManager;                                             
	    	        telephonyManager  =  ( TelephonyManager )getSystemService( Context.TELEPHONY_SERVICE );
	    	        device_id = telephonyManager.getDeviceId(); 
	    	    	
	    	        if (ServicesItem.hasAttribute(jar_services_global, service_code)) {
	    	        
		    	        List<NameValuePair> attribute = new ArrayList<NameValuePair>();
		    	        
		    	        id = 101;
		    	        for (i=0;i<jar_attributes.length();i++) {
		    	        	attribute_content = "";
		    	        	
	    		            //check datatype of input
	    		            try {
								if (jar_attributes.getJSONObject(i).getString("type").equals("text")) {
									//if datatype == text
									EditText edt_contentAttribute = (EditText) findViewById(++id);
									attribute_content = edt_contentAttribute.getText().toString();
									Log.d("new report", "1 edit text "+ attribute_content);
								}
								else if (jar_attributes.getJSONObject(i).getString("type").equals("singlevaluelist")) {
									//if datatype == singlevaluelist
									JSONArray jar_value = jar_attributes.getJSONObject(i).getJSONArray("values");
									n_rb = jar_value.length();
									id++; // for radio group
									
									for (int j=0; j< n_rb; j++) {
										RadioButton rb_contentAttribute = (RadioButton) findViewById(++id);
										if (rb_contentAttribute.isChecked()) {
											attribute_content = rb_contentAttribute.getText().toString();
										}
									}
									Log.d("new report", "2 radio button "+ attribute_content);
								}
								else if (jar_attributes.getJSONObject(i).getString("type").equals("multivaluelist")) {
									//if datatype == multivaluelist
									boolean isfirst = true;
									JSONArray jar_value = jar_attributes.getJSONObject(i).getJSONArray("values");
									int n_cb = jar_value.length();
									
									for (int j=0; j< n_cb; j++) {
										CheckBox cb_contentAttribute = (CheckBox) findViewById(++id);
										if (cb_contentAttribute.isChecked()) {
											if (isfirst) {
												attribute_content = cb_contentAttribute.getText().toString();
												isfirst = false;
											}
											else 
												attribute_content = attribute_content + ", " +  cb_contentAttribute.getText().toString();
										}
									}
									Log.d("new report", "3 checkbox "+ attribute_content);
								}
								attribute.add(new BasicNameValuePair(jar_attributes.getJSONObject(i).getString("name"), attribute_content));
								id++;
							} catch (JSONException e) {
								// TODO Auto-generated catch block
								e.printStackTrace();
							}
			    		}
		    	        if (photo == null)
		    	        	reply = GeoreporterAPI.sendReport(NewReport.this, server_jurisdiction_id, service_code, latitude, longitude, true, attribute, email, device_id, first_name, last_name, phone, content);
		    	        else 
		    	        	reply = GeoreporterAPI.sendReportWithPicture(NewReport.this, photo, server_jurisdiction_id, service_code, latitude, longitude, true, attribute, email, device_id, first_name, last_name, phone, content);
	    	        }
	    	        else {
	    	        	if (photo == null)
	    	        		reply = GeoreporterAPI.sendReport(NewReport.this, server_jurisdiction_id, service_code, latitude, longitude, false, null, email, device_id, first_name, last_name, phone, content);
	    	        	else
	    	        		reply = GeoreporterAPI.sendReportWithPicture(NewReport.this, photo, server_jurisdiction_id, service_code, latitude, longitude, false, null, email, device_id, first_name, last_name, phone, content);
	    	        }
	    	        	
	    	        
	    	        try {
						service_request_id = reply.getJSONObject(0).getString("service_request_id");
						
						if (service_request_id != null) {
		    	        	Toast.makeText(getApplicationContext(), "Report has been sent with service request id :"+service_request_id, Toast.LENGTH_LONG).show();
		    	        	
		    	        	JSONArray ja_savedreports;
		    	        	if (ExternalFileAdapter.readJSON(NewReport.this, "reports") == null) {
		    	        		ja_savedreports = new JSONArray();
		    	        	}
		    	        	else {
		    	        		ja_savedreports = ExternalFileAdapter.readJSON(NewReport.this, "reports");
		    	        	}
		    	        	
		    	        	
		    	        	JSONObject object = new JSONObject();
		    	        	try {
		    	        		Time today = new Time(Time.getCurrentTimezone());
		    	        		today.setToNow();
		    	        		
		    	        		String datetime;
		    	        		datetime = GeoreporterUtils.getMonth(today.month+1)+" "+today.monthDay+", "+today.year+" "+today.format("%k:%M:%S");
		    	        		
		    	        		
		    	        		
		    	        		object.put("service_request_id", service_request_id);
		    	        		object.put("jurisdiction_id", server_jurisdiction_id);
		    	        		object.put("report_service", service_name);
		    	        		object.put("date_time", datetime);
		    	        		object.put("server_name", server_name);
		    	        	} catch (JSONException e) {
		    	        		e.printStackTrace();
		    	        	}
		    	        	
		    	        	ja_savedreports.put(object);
		    	        	
		    	        	ExternalFileAdapter.writeJSON(NewReport.this, "reports", ja_savedreports);
		    	        	//switch to my report screen
		    				switchTabInActivity(2);
		    	        }
		    	        else {
		    	        	Toast.makeText(getApplicationContext(), "Sending unsuccessful", Toast.LENGTH_LONG).show();
		    	        }
						
					} catch (JSONException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
	    	        
	    	    }    
	    	   //if report content hasn't been filled yet
	    	    else 
	    	    	Toast.makeText(this, "Please fill your report first", Toast.LENGTH_SHORT).show();
	    	    
				break;
			
			case R.id.btn_service:
				//for showing group, services, and attributes
		        thread_service = new Thread() {
					public void run() {	
						
				      //get the current server
						pref = getSharedPreferences("server",0);
						JSONObject server;
						
						try {
							server = new JSONObject(pref.getString("selectedServer", ""));
							server_name = server.getString("name");
							server_jurisdiction_id = server.getString("jurisdiction_id");
						} catch (JSONException e) {
							// TODO Auto-generated catch block
							e.printStackTrace();
						}
						
						//check whether user connected to the internet
				    	if (GeoreporterAPI.isConnected(NewReport.this)) {
				    		//make the first alert dialog for services group
				    		service_handler.post(service_update_group);
			    	    	
				    	}
				    	//if user is not connected to the internet
				    	else {
				    		service_handler.post(service_update_notconnected);
				    	}
					}
		        };
		        thread_service.start();
				break;
			}
		}
	    
	    
	    protected void onActivityResult(int requestCode, int resultCode, Intent data) {  
	        if (requestCode == CAMERA_REQUEST) {  
	        	if (resultCode == Activity.RESULT_OK) {
	        		img_photo.setVisibility(ImageView.VISIBLE);
	        		btn_picture.setText("Change Picture");
		            photo = (Bitmap) data.getExtras().get("data"); 
		            img_photo.setImageBitmap(photo);
	        	}
	        }
	        else if (requestCode == GALLERY_REQUEST) {
		        if(resultCode == RESULT_OK){  
		        	img_photo.setVisibility(ImageView.VISIBLE);
	        		btn_picture.setText("Change Picture");
		            Uri selectedImage = data.getData();
					try {
						photo = ExternalFileAdapter.decodeUri(selectedImage, this);
			            img_photo.setImageBitmap(photo);
			            
					} catch (FileNotFoundException e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
		            
		        }
		    }
	    }  

	    @Override
		protected void onResume (){
			super.onResume();
			
			//for Shared Preferences
	        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
	        edt_firstName = (EditText) findViewById(R.id.edt_firstname);
	        edt_firstName.setText(preferences.getString("firstname", ""));
	        
	        edt_lastName = (EditText) findViewById(R.id.edt_lastname);
	        edt_lastName.setText(preferences.getString("lastname", ""));
	        
	        edt_email = (EditText) findViewById(R.id.edt_email);
	        edt_email.setText(preferences.getString("email", ""));
	        
	        edt_phone = (EditText) findViewById(R.id.edt_phone);
	        edt_phone.setText(preferences.getString("phone", ""));
	        
	    }
	    
	    //handler for updating topic list
	    final Handler service_handler = new Handler();

	    
	  // Create runnable for posting
	  //for updating textview
	    final Runnable service_update_group = new Runnable() {
	        public void run() {
	            service_update_group_in_ui();
	        }
	    };
	    
	  //for updating not connected message
	    final Runnable service_update_notconnected = new Runnable() {
	        public void run() {
	            service_update_notconnected_in_ui();
	        }
	    };
	    
	    private void service_update_group_in_ui() {
	    	AlertDialog.Builder builder = new AlertDialog.Builder(NewReport.this);
			builder.setTitle("Available Service Groups from "+server_name);
			//final JSONArray jar_services = GeoreporterAPI.getServices(NewReport.this);
			try {
				final JSONArray jar_services = new JSONArray(pref.getString("ServerService", ""));
				jar_services_global = jar_services;
				final CharSequence[] group = ServicesItem.getGroup(jar_services);
				builder.setItems(group, new DialogInterface.OnClickListener() {
				    public void onClick(DialogInterface dialog, int nid) {
				    	display_services_dialogbox(group, nid, jar_services);
				    }
				});
				AlertDialog alert = builder.create();
				alert.show();
			}
			catch (JSONException e) {
				e.printStackTrace();
			}
	    }
	    
	    private void service_update_notconnected_in_ui(){
	    	Toast.makeText(getApplicationContext(), "No internet connection or the server URL is not vaild", Toast.LENGTH_LONG).show();
	    }
	    
	    private void display_services_dialogbox(CharSequence[] group, int nid, final JSONArray jar_services) {
	    	//make the second alert dialog for services in selected group
	    	AlertDialog.Builder builder = new AlertDialog.Builder(NewReport.this);
			builder.setTitle(group[nid]+" Services");
			final CharSequence[] services = ServicesItem.getServicesByGroup(jar_services, group[nid]);
    		builder.setItems(services, new DialogInterface.OnClickListener() {
    		    public void onClick(DialogInterface dialog, int nid) {
    		    	
    		    	//set the service_code - for report posting
					service_code = ServicesItem.getServiceCode(jar_services, services[nid]);
					service_name = services[nid]+"";
    		    	
    		    	Button btn_service = (Button) findViewById(R.id.btn_service);
    		    	btn_service.setText(ServicesItem.getServiceDescription(jar_services, services[nid]));
    		    	
		    		//remove previous view
    		    	
    		    	r0 = (RelativeLayout) findViewById(R.id.r0);
    		    	
		    		if (last_id==id) {
    		    		for (int i=100; i<=last_id; i++) {
    		    			r0.removeView(findViewById(i));
    		    		}
    		    		last_id = 0;
	    			}
    		    	
    		    	//check whether the following service has attribute
    		    	if (ServicesItem.hasAttribute(jar_services, service_code)) {
    		    		//display the attribute
    		    		
    		    		//fetch the atrribute
    		    		JSONObject jo_attributes_service = GeoreporterAPI.getServiceAttribute(NewReport.this, ServicesItem.getServiceCode(jar_services, services[nid]));
    		    		
    		    		try {
    		    			jar_attributes = jo_attributes_service.getJSONArray("attributes");
    		    			
	    		    		id = 100;
	    		    		n_rb = 0;
	    		    		for (i=0;i<jar_attributes.length();i++) {
	    		    			
	    		    			//print the text label
	    		    			display_textview();
	    		    			
    	    		            //chekck datatype of input
    	    		            if (jar_attributes.getJSONObject(i).getString("datatype").equals("text")) {
    	    		            	//if datatype == text
    	    		            	display_edittext();
    	    		            }
    	    		            else if (jar_attributes.getJSONObject(i).getString("datatype").equals("singlevaluelist")) {
    	    		            	//if datatype == singlevaluelist
    	    		            	display_radiobutton();
    	    		            }
    	    		            else if (jar_attributes.getJSONObject(i).getString("datatype").equals("multivaluelist")) {
    	    		            	//if datatype == multivaluelist
    	    		            	display_combobox();
    	    		            }
    	    		            last_id = id;
	    		    		}
	    		    		
	    		    		//add first name etc at the right place
	    		            TextView txt_picture = (TextView) findViewById(R.id.txt_picture);
	    		            RelativeLayout.LayoutParams txt_picture_params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	    		            txt_picture_params.addRule(RelativeLayout.BELOW, id-n_rb);
	    		            txt_picture_params.setMargins(15, 20, 0, 0);
	    		            txt_picture.setLayoutParams(txt_picture_params);
    		            
    		    		} catch (JSONException e) {
    		    			// TODO Auto-generated catch block
    		    			e.printStackTrace();
    		    		}
    		    		
    		    	}
    		    	else {
    		    		//add first name etc at the right place
    		    		TextView txt_picture= (TextView) findViewById(R.id.txt_picture);
    		            RelativeLayout.LayoutParams txt_picture_params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    		            txt_picture_params.addRule(RelativeLayout.BELOW, R.id.edt_newReport);
    		            txt_picture_params.setMargins(15, 20, 0, 0);
    		            txt_picture.setLayoutParams(txt_picture_params);
    		    	}
    		    }
    		});
    		AlertDialog alert = builder.create();
    		alert.show();
	    }
	    
	    private void display_textview() {
	        // Back in the UI thread -- update our UI elements based on the data in mResults
	    	TextView txt_attribute= new TextView(NewReport.this);
            try {
				txt_attribute.setText(jar_attributes.getJSONObject(i).getString("description"));
	            txt_attribute.setTextColor(Color.BLACK);
	            txt_attribute.setId(++id);
	            RelativeLayout.LayoutParams txt_params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	            if (i==0) {
	            	txt_params.addRule(RelativeLayout.BELOW, R.id.edt_newReport);
	            	txt_params.setMargins(15, 15, 15, 0);
	            }
	            else {
	            	txt_params.addRule(RelativeLayout.BELOW, id-n_rb-1);
	            	txt_params.setMargins(15, 0, 15, 0);
	            }
	            txt_attribute.setLayoutParams(txt_params);
	            r0.addView(txt_attribute);
	        	n_rb = 0;
            } catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    
	    private void display_edittext() {
	    	EditText edt_attribute = new EditText(NewReport.this);
            edt_attribute.setId(++id);
            RelativeLayout.LayoutParams edt_params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            edt_params.addRule(RelativeLayout.BELOW, id-n_rb-1);
            edt_params.setMargins(15, 0, 15, 0);
            edt_attribute.setLayoutParams(edt_params);
            r0.addView(edt_attribute);
        	n_rb = 0;
	    }
	    
	    private void display_radiobutton() {
	    	JSONArray jar_value;
			try {
				jar_value = jar_attributes.getJSONObject(i).getJSONArray("values");
	        	n_rb = jar_value.length();
	        	RadioButton[] rb = new RadioButton[n_rb];
	            RadioGroup rg = new RadioGroup(NewReport.this); 
	            rg.setOrientation(RadioGroup.VERTICAL);
	            rg.setId(++id);
	            for(int j=0; j<n_rb; j++){
	                rb[j]  = new RadioButton(NewReport.this);
	                rb[j].setId(++id);
	                rb[j].setText(jar_value.getJSONObject(j).getString("name"));
	                //rb[j].setText(jar_value.getString(j));
	                rb[j].setTextColor(Color.BLACK);
	                rg.addView(rb[j]); 
	            }
	            RelativeLayout.LayoutParams rg_params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
	            rg_params.addRule(RelativeLayout.BELOW, id-jar_value.length()-1);
	            rg_params.setMargins(15, 0, 15, 0);
	            rg.setLayoutParams(rg_params);
	            r0.addView(rg);
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }

	    private void display_combobox() {
	    	JSONArray jar_value;
			try {
				jar_value = jar_attributes.getJSONObject(i).getJSONArray("values");
	        	int n_cb = jar_value.length();
	        	CheckBox[] cb = new CheckBox[n_cb];
	        	n_rb = 0;
	            for(int j=0; j<n_cb; j++){
	                cb[j]  = new CheckBox(NewReport.this);
	                cb[j].setId(++id);
	                cb[j].setText(jar_value.getJSONObject(j).getString("name"));
	                //cb[j].setText(jar_value.getString(j));
	                cb[j].setTextColor(Color.BLACK);
	                r0.addView(cb[j]);
	                RelativeLayout.LayoutParams cb_params = new RelativeLayout.LayoutParams(ViewGroup.LayoutParams.FILL_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
		            cb_params.addRule(RelativeLayout.BELOW, id-1);
		            cb_params.setMargins(15, 0, 15, 0);
		            cb[j].setLayoutParams(cb_params);
	            }
			} catch (JSONException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
	    }
	    
	    //LocationListener class 
		private class MyLocationListener implements LocationListener {

			public void onLocationChanged(Location location) {
				if (location != null) {
					loc = location;
					latitude = loc.getLatitude();
					longitude = loc.getLongitude();
					
				}
			}

			public void onProviderDisabled(String provider) {
				// TODO Auto-generated method stub
			}

			public void onProviderEnabled(String provider) {
				// TODO Auto-generated method stub
			}

			public void onStatusChanged(String provider, int status, Bundle extras) {
				// TODO Auto-generated method stub
			}
			
		}
		
		public void switchTabInActivity(int indexTabToSwitchTo){
			Main ParentActivity;
			ParentActivity = (Main) this.getParent();
			ParentActivity.switchTab(indexTabToSwitchTo);
		}

}
