/**
 *    ||          ____  _ __                           
 * +------+      / __ )(_) /_______________ _____  ___ 
 * | 0xBC |     / __  / / __/ ___/ ___/ __ `/_  / / _ \
 * +------+    / /_/ / / /_/ /__/ /  / /_/ / / /_/  __/
 *  ||  ||    /_____/_/\__/\___/_/   \__,_/ /___/\___/
 *
 * Copyright (C) 2013 Bitcraze AB
 *
 * Crazyflie Nano Quadcopter Client
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 *
 */

package com.shreymalhotra.crazyflieserver;

import java.math.BigDecimal;

import android.content.Context;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Compound component that groups together flight data UI elements
 *
 */
public class FlightDataView extends LinearLayout {

    private TextView textView_leftX;
    private TextView textView_leftY;
    private TextView textView_rightX;
    private TextView textView_rightY;
    private MainActivity context;

    public FlightDataView(Context context, AttributeSet attrs) {
      super(context, attrs);

      this.context = (MainActivity) context;
      
      setOrientation(LinearLayout.HORIZONTAL);

      LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
      inflater.inflate(R.layout.view_flight_data, this, true);
      
      textView_leftX = (TextView) findViewById(R.id.leftX);
      textView_leftY = (TextView) findViewById(R.id.leftY);
      textView_rightX = (TextView) findViewById(R.id.rightX);
      textView_rightY = (TextView) findViewById(R.id.rightY);

      //initialize
      updateFlightData();
    }

    public FlightDataView(Context context) {
      this(context, null);
    }

    public void updateFlightData() {
        textView_leftX.setText("Pitch:"+ round(context.getPitch() * -1)); // inverse
        textView_leftY.setText("Roll:"+round(context.getRoll()));
        textView_rightX.setText("Thrust:"+round(context.getThrust()));
        textView_rightY.setText("Yaw:"+round(context.getYaw()));
    }


    public static double round(double unrounded) {
        BigDecimal bd = new BigDecimal(unrounded);
        BigDecimal rounded = bd.setScale(2, BigDecimal.ROUND_HALF_UP);
        return rounded.doubleValue();
    }
    

}
