crazyflie-android-client-no-otg
===============================

Fly Crazyflie with Android device, even if you dont have a PS3/XBOX controller or an OTG cable.

This has two parts - 

1. Android Client 
2. PC Client 

Both the clients are similar to the the original CrazyFlie client.

The Android Client creates a NanoHttpd server to stream json data, to which the PC client connects.  

Installation Instructions - 

1. Install Android Client 
2. Connect you PC to your Android device using a local WIFI hotspot (create on your Android device)
3. Select "Connect" from the menu
4. Note down the Ip Address (X.X.X.X - forget the :8080 bit)
5. Open PC Client, and under file menu, enter the ip (X.X.X.X)
6. Select "Server Connect" before you connect CrazyFlie
7. Test the connection, by moving the joystick on your Android device. The values for pitch,roll,yaw,thrust should change.
8. Then hit connect and select your CrazyFlie connection. 
9. Fly without any controller/OTG

You can also update the minimum/max values. Do this before you hit Server Connect, to update the server with new values.
