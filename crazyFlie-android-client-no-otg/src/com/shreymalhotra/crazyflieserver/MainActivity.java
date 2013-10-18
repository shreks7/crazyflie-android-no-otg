/**
 *
 *  The MIT License (MIT)
 *
 *	Copyright (c) 2013 Shrey Malhotra
 *
 *	Permission is hereby granted, free of charge, to any person obtaining a copy of
 *	this software and associated documentation files (the "Software"), to deal in
 *	the Software without restriction, including without limitation the rights to
 *	use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of
 *	the Software, and to permit persons to whom the Software is furnished to do so,
 *	subject to the following conditions:
 *	
 *	The above copyright notice and this permission notice shall be included in all
 *	copies or substantial portions of the Software.
 *	
 *	THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 *	IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS
 *	FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR
 *	COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER
 *	IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN
 *	CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.shreymalhotra.crazyflieserver;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;
import java.util.Properties;
import java.util.Random;

import org.apache.http.conn.util.InetAddressUtils;
import teaonly.droideye.StreamingLoop;
import teaonly.droideye.TeaServer;
import android.app.Activity;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.TextView;

import com.MobileAnarchy.Android.Widgets.Joystick.DualJoystickView;
import com.MobileAnarchy.Android.Widgets.Joystick.JoystickMovedListener;

public class MainActivity extends Activity {

	private FlightDataView mFlightDataView;
	private DualJoystickView mJoysticks;
	public int resolution = 1000;

	private float right_analog_x;
	private float right_analog_y;
	private float left_analog_x;
	private float left_analog_y;
	private float split_axis_yaw_right;
	private float split_axis_yaw_left;

	private float mMaxTrim = 0.5f;
	private float mTrimIncrements = 0.1f;
	private String mLinkStatus = "Not Connected";

	public float deadzone = 0.1f;
	private int maxRollPitchAngle = 20;
	private int maxYawAngle = 150;
	private int maxThrust = 80;
	private int minThrust = 25;
	private boolean xmode = false;
	private float mRollTrim = 0.2f;
	private float mPitchTrim = 0;

	/*
	 * Web Server
	 */

	boolean inProcessing = false;
	TeaServer webServer = null;
	private TextView serverStatus;
	private TextView serverStatus2;
	boolean ret = false;
	private StreamingLoop jsonLoop;

	/*
	 * (non-Javadoc)
	 * 
	 * @see android.app.Activity#onCreate(android.os.Bundle)
	 */

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);

		mFlightDataView = (FlightDataView) findViewById(R.id.flightdataview);

		mJoysticks = (DualJoystickView) findViewById(R.id.joysticks);
		mJoysticks.setMovementRange(resolution, resolution);
		mJoysticks.setOnJostickMovedListener(_listenerLeft, _listenerRight);

		serverStatus = (TextView) findViewById(R.id.serverStatus);
		serverStatus2 = (TextView) findViewById(R.id.serverStatus2);

		System.loadLibrary("natpmp");
		initStreamingLoop();
	}

	private void initStreamingLoop() {

		if (jsonLoop == null) {
			Random rnd = new Random();
			String etag = Integer.toHexString(rnd.nextInt());
			jsonLoop = new StreamingLoop("teaonly.droideye" + etag);
		}

	}

	@Override
	public void onPause() {
		super.onPause();
		inProcessing = true;
		ret = true;
		if (webServer != null)
			webServer.stop();

	}

	public String getLocalIpAddress() {
		try {
			for (Enumeration<NetworkInterface> en = NetworkInterface
					.getNetworkInterfaces(); en.hasMoreElements();) {
				NetworkInterface intf = en.nextElement();
				for (Enumeration<InetAddress> enumIpAddr = intf
						.getInetAddresses(); enumIpAddr.hasMoreElements();) {
					InetAddress inetAddress = enumIpAddr.nextElement();
					// if (!inetAddress.isLoopbackAddress() &&
					// !inetAddress.isLinkLocalAddress() &&
					// inetAddress.isSiteLocalAddress() ) {
					if (!inetAddress.isLoopbackAddress()
							&& InetAddressUtils.isIPv4Address(inetAddress
									.getHostAddress())) {
						String ipAddr = inetAddress.getHostAddress();
						return ipAddr;
					}
				}
			}
		} catch (SocketException ex) {
			Log.d("CrazyFlieServer", ex.toString());
		}
		return null;
	}

	private boolean initWebServer() {
		String ipAddr = getLocalIpAddress();
		if (ipAddr != null) {
			Log.i("TEA", "Ip Address: " + ipAddr);
			try {
				webServer = new TeaServer(8080, this);
				webServer.registerCGI("/cgi/setup", doSetup);
				webServer.registerCGI("/stream/json", doCapture);
			} catch (IOException e) {
				webServer = null;
				Log.e("TEA", e.toString());
			}
		}

		if (webServer != null) {
			serverStatus.setText(getString(R.string.msg_access_local)
					+ " http://" + ipAddr + ":8080");
			NatPMPClient natQuery = new NatPMPClient();
			natQuery.start();
			return true;
		} else {
			serverStatus.setText(getString(R.string.msg_error));
			return false;
		}

	}


	private TeaServer.CommonGatewayInterface doSetup = new TeaServer.CommonGatewayInterface() {
		@Override
		public String run(Properties parms) {

			if (jsonLoop.isConnected())
				return null;

			maxRollPitchAngle = Integer.parseInt(parms
					.getProperty("maxRollPitchAngle"));
			maxYawAngle = Integer.parseInt(parms.getProperty("maxYawAngle"));
			maxThrust = (int) Float.parseFloat(parms.getProperty("maxThrust"));
			minThrust = (int) Float.parseFloat(parms.getProperty("minThrust"));
			xmode = parms.getProperty("xmode") == "True" ? true : false;
			// mRollTrim = Float.parseFloat(parms.getProperty("mRollTrim"));
			// mPitchTrim = Float.parseFloat(parms.getProperty("mRollTrim"));

			Log.d("TEAONLY", ">>>>>>>run in doSetup " + maxRollPitchAngle + "|"
					+ maxYawAngle + "|" + maxThrust + "|" + minThrust);

			return "OK";
		}

		@Override
		public InputStream streaming(Properties parms) {
			return null;
		}
	};

	private TeaServer.CommonGatewayInterface doCapture = new TeaServer.CommonGatewayInterface() {
		@Override
		public String run(Properties parms) {
			return null;
		}

		@Override
		public InputStream streaming(Properties parms) {

			if (jsonLoop.isConnected()) {
				return null; // tell client is is busy by 503
			}

			jsonLoop.InitLoop(128, 8192);
			InputStream is = null;

			try {
				is = jsonLoop.getInputStream();
			} catch (IOException e) {
				jsonLoop.ReleaseLoop();
				return null;
			}

			parms.setProperty("mime", "application/json");

			JsonEncoder jsonEncoder = new JsonEncoder();
			jsonEncoder.start();

			return is;

		}
	};

	private class JsonEncoder extends Thread {

		@Override
		public void run() {

			OutputStream os = null;
			try {
				os = jsonLoop.getOutputStream();
			} catch (IOException e) {
				os = null;
				jsonLoop.ReleaseLoop();
				return;
			}

			while (true) {

				if (ret == true)
					break;

				List<Float> values = new ArrayList<Float>();

				try {
					values.add(getRoll());
					values.add(getPitch());
					values.add(getThrust());
					values.add(getYaw());

				} catch (Exception e) {
					e.printStackTrace();
				}

				String output = values.toString();
				int length = values.toString().length();
				int remainder = 0;
				if (length < 27) {
					remainder = 27 - length;
				}
				for (int i = 0; i < remainder; i++) {
					output += "#";
				}

				try {
					os.write(output.getBytes("UTF-8"), 0, 27);
				} catch (IOException e) {
					break;
				}
			}

			jsonLoop.ReleaseLoop();
		}
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		getMenuInflater().inflate(R.menu.main, menu);
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.menu_connect:
			if (initWebServer()) {

			}
			break;
		case R.id.menu_disconnect:
			ret = false;
			jsonLoop.ReleaseLoop();
			if (webServer != null)
				webServer.stop();
			serverStatus.setText("Server Closed");
			serverStatus2.setText("");
			break;
		}
		return true;
	}

	public float getThrust() {
		float thrust = getRightAnalog_Y();
		thrust = thrust * -1; // invert
		if (thrust > deadzone) {
			return Math.round(minThrust + (thrust * getThrustFactor()));
		}
		return 0;
	}

	public float getRoll() {
		float roll = getLeftAnalog_X();
		return Math.round((roll + getRollTrim()) * getRollPitchFactor()
				* getDeadzone(roll));
	}

	private float getRollTrim() {
		return mRollTrim;
	}

	public float getPitch() {
		float pitch = getLeftAnalog_Y();
		return Math.round((pitch + getPitchTrim()) * getRollPitchFactor()
				* getDeadzone(pitch));
	}

	private float getPitchTrim() {
		return mPitchTrim;
	}

	public float getYaw() {
		float yaw = 0;
		boolean mUseSplitAxisYaw = false;
		if (mUseSplitAxisYaw) {
			yaw = split_axis_yaw_right - split_axis_yaw_left;
		} else {
			yaw = getRightAnalog_X();
		}
		return Math.round(yaw * getYawFactor() * getDeadzone(yaw));
	}

	private float getDeadzone(float axis) {
		if (axis < deadzone && axis > deadzone * -1) {
			return 0;
		}
		return 1;
	}

	public float getRightAnalog_X() {
		return right_analog_x;
	}

	public float getRightAnalog_Y() {
		return right_analog_y;
	}

	public float getLeftAnalog_X() {
		return left_analog_x;
	}

	public float getLeftAnalog_Y() {
		return left_analog_y;
	}

	public float getRollPitchFactor() {
		return maxRollPitchAngle;
	}

	public float getYawFactor() {
		return maxYawAngle;
	}

	public float getThrustFactor() {
		int addThrust = 0;
		if ((maxThrust - minThrust) < 0) {
			addThrust = 0; // do not allow negative values
		} else {
			addThrust = (maxThrust - minThrust);
		}
		return addThrust;
	}

	public boolean isXmode() {
		return this.xmode;
	}

	private void resetAxisValues() {
		right_analog_y = 0;
		right_analog_x = 0;
		left_analog_y = 0;
		left_analog_x = 0;
	}

	@Override
	public void onConfigurationChanged(Configuration newConfig) {
		super.onConfigurationChanged(newConfig);
		setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
	}

	private JoystickMovedListener _listenerRight = new JoystickMovedListener() {

		@Override
		public void OnMoved(int pan, int tilt) {
			right_analog_y = (float) tilt / resolution;
			right_analog_x = (float) pan / resolution;

			mFlightDataView.updateFlightData();
		}

		@Override
		public void OnReleased() {
			// Log.i("Joystick-Right", "Release");
			right_analog_y = 0;
			right_analog_x = 0;
		}

		@Override
		public void OnReturnedToCenter() {
			// Log.i("Joystick-Right", "Center");
			right_analog_y = 0;
			right_analog_x = 0;
		}
	};

	private JoystickMovedListener _listenerLeft = new JoystickMovedListener() {

		@Override
		public void OnMoved(int pan, int tilt) {
			left_analog_y = (float) tilt / resolution;
			left_analog_x = (float) pan / resolution;

			mFlightDataView.updateFlightData();
		}

		@Override
		public void OnReleased() {
			left_analog_y = 0;
			left_analog_x = 0;
		}

		@Override
		public void OnReturnedToCenter() {
			left_analog_y = 0;
			left_analog_x = 0;
		}
	};

	public String getLinkStatus() {
		return mLinkStatus;
	}

	static private native String nativeQueryInternet();

	private class NatPMPClient extends Thread {
		String queryResult;
		Handler handleQueryResult = new Handler(getMainLooper());

		@Override
		public void run() {
			queryResult = nativeQueryInternet();
			if (queryResult.startsWith("error:")) {
				handleQueryResult.post(new Runnable() {
					@Override
					public void run() {
						serverStatus2
						.setText(getString(R.string.msg_access_query_error));
						Log.e("TEA", getString(R.string.msg_access_query_error));
					}
				});
			} else {
				handleQueryResult.post(new Runnable() {
					@Override
					public void run() {
						serverStatus2
						.setText(getString(R.string.msg_access_internet)
								+ " " + queryResult);
						Log.i("TEA", getString(R.string.msg_access_internet)
								+ " " + queryResult);
					}
				});
			}
		}
	}

}
