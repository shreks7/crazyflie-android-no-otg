import sys
from PyQt4 import QtGui, QtCore, uic
from PyQt4.QtCore import pyqtSignal, Qt, pyqtSlot
import ast
import urllib2
import httplib
import logging,threading,time

from cflib.crazyflie import Crazyflie
import cflib.crtp
from cfclient.utils.logconfigreader import LogConfig
from cfclient.utils.logconfigreader import LogVariable
from connectiondialogue import ConnectDialogue

html =""
ipaddress = "http://192.168.43.1"
stream_url = ':8080/stream/json'
setup_url = ':8080/cgi/setup'
ex = False
threads = None
_signalThread = None
MAX_THRUST = 65365.0
MIN_THRUST = 10000.0
_CrazyFlieThread = None
values = None

logging.basicConfig(level=logging.DEBUG)

logger = logging.getLogger(__name__)

class UIState:
    DISCONNECTED = 0
    CONNECTING = 1
    CONNECTED = 2

class App(QtGui.QMainWindow):  
   
    global _CrazyFlieThread,threads
    connectionLostSignal = pyqtSignal(str, str)
    connectionInitiatedSignal = pyqtSignal(str)
    batteryUpdatedSignal = pyqtSignal(object)
    connectionDoneSignal = pyqtSignal(str)
    connectionFailedSignal = pyqtSignal(str, str)
    disconnectedSignal = pyqtSignal(str)
    linkQualitySignal = pyqtSignal(int)
    _motor_data_signal = pyqtSignal(object)
    _imu_data_signal = pyqtSignal(object)

       
    def __init__(self):
        global stream_url,setup_url, ipaddress
        super(App, self).__init__()
        stream_url = ipaddress+stream_url
        setup_url = ipaddress+setup_url
        uic.loadUi('mainUI.ui', self)
        self.initUI()
        
        cflib.crtp.init_drivers(enable_debug_driver=False)
        self.cf = Crazyflie()
        
        self.CrazyFlieSignal = QtCore.pyqtSignal(object)
         
        #oldValues  
        self.oldThrust = 0
        self.maxAngleV = self.maxAngle.value()
        self.maxYawRateV = self.maxYawRate.value()
        self.maxThrustV = self.maxThrust.value()
        self.minThrustV = self.minThrust.value()
        self.slewEnableLimitV = self.slewEnableLimit.value()
        self.thrustLoweringSlewRateLimitV = self.thrustLoweringSlewRateLimit.value()
        
        #Connection Dialogue
        self.connectDialogue = ConnectDialogue()
        
        #Status Bar Update
        self._statusbar_label = QtGui.QLabel("Loading device and configuration.")
        self.statusBar().addWidget(self._statusbar_label)
        
        #Connect to the URI 
        self.connectDialogue.requestConnectionSignal.connect(self.cf.open_link)
        
        # Set UI state in disconnected buy default
        self.setUIState(UIState.DISCONNECTED)
        
        # Connection callbacks and signal wrappers for UI protection
        self.connectionDoneSignal.connect(self.connectionDone)
        self.connectionFailedSignal.connect(self.connectionFailed)
        self.batteryUpdatedSignal.connect(self.updateBatteryVoltage)
        self.connectionLostSignal.connect(self.connectionLost)
        self.disconnectedSignal.connect(
                        lambda linkURI: self.setUIState(UIState.DISCONNECTED,
                                                        linkURI))
        self.connectionInitiatedSignal.connect(
                           lambda linkURI: self.setUIState(UIState.CONNECTING,
                                                           linkURI))
        
        self.cf.connectionFailed.add_callback(self.connectionFailedSignal.emit)
        self.cf.connectSetupFinished.add_callback(self.connectionDoneSignal.emit)
        self.cf.disconnected.add_callback(self.disconnectedSignal.emit)
        self.cf.connectionLost.add_callback(self.connectionLostSignal.emit)
        self.cf.connectionInitiated.add_callback(self.connectionInitiatedSignal.emit) 

        # Connect link quality feedback
        self.cf.linkQuality.add_callback(self.linkQualitySignal.emit)
        self.linkQualitySignal.connect(
                   lambda percentage: self.linkQualityBar.setValue(percentage))
        
        QtCore.QObject.connect(self.connectFlie, QtCore.SIGNAL('clicked()'), self.onConnectButtonClicked)
        
        # Flight Data Signal Connection
        
        self._imu_data_signal.connect(self._imu_data_received)
        self._motor_data_signal.connect(self._motor_data_received)
        
        # menu items
        self.actionSet_Server_Ip.triggered.connect(self.setIpAddress)
      
    # --------- Initialize UI & Define Close Event ------------- 
    
    def setIpAddress(self):
        global ipaddress,stream_url,setup_url
        text, ok = QtGui.QInputDialog.getText(self, 'Input IpAddress (X.X.X.X)', 
            'Enter Ip Address:')
        
        if ok:
            ipaddress = "http://"+text
            stream_url = ':8080/stream/json'
            setup_url = ':8080/cgi/setup'
            stream_url = str(ipaddress+stream_url)
            setup_url = str(ipaddress+setup_url)
        
    def initUI(self):
        self.setWindowTitle('CrazyFlie')   
        self.show()
        self.statusBar()
        QtCore.QObject.connect(self.serverConnect, QtCore.SIGNAL('clicked()'), self.onServerButtonClicked)
        QtCore.QObject.connect(self.updateServer, QtCore.SIGNAL('clicked()'), self.onSendServerAttr)
              
    def closeEvent(self, event):
        reply = QtGui.QMessageBox.question(self, 'Message',
            "Are you sure to quit?", QtGui.QMessageBox.Yes | 
            QtGui.QMessageBox.No, QtGui.QMessageBox.No)
        if reply == QtGui.QMessageBox.Yes:
            ex = True
            self.cf.close_link()
            event.accept()
        else:
            event.ignore()

            
    # ---- Button State Handlers -----------------
    
    def onSendServerAttr(self):
        self.maxAngleV = self.maxAngle.value()
        self.maxYawRateV = self.maxYawRate.value()
        self.maxThrustV = self.maxThrust.value()
        self.minThrustV = self.minThrust.value()
        self.slewEnableLimitV = self.slewEnableLimit.value()
        self.thrustLoweringSlewRateLimitV = self.thrustLoweringSlewRateLimit.value()
        self.console.append("Sending Server: %d, %d ,%f,%f,%f" %(self.maxAngleV,self.maxYawRateV,self.maxThrustV,
                                                                  self.minThrustV,self.slewEnableLimitV))
        threading.Thread(target=self.updateSer).start()
        
    def updateSer(self):
        update_url = setup_url+"?maxRollPitchAngle=%d&maxYawAngle=%d&maxThrust=%f&minThrust=%f&xmode=False" %(self.maxAngleV,self.maxYawRateV,self.maxThrustV,
                                                                  self.minThrustV) 
        try:         
            response = urllib2.urlopen(update_url)
        except urllib2.HTTPError, e:
            self.console.append(str(e))
            return
        except urllib2.URLError, e:
            self.console.append(str(e))
            return
        except httplib.HTTPException, e:
            self.console.append(str(e))
            return
             
        if(response.read=="OK"):
            self.console.append("Server Update Status: OK")    
             
    def onServerButtonClicked(self):
        global stream_url
        ex=False
        self.serverConnect.setEnabled(False)
        downloader = DownloadThread(stream_url, self.console)
        self.threads = downloader
        downloader.data_downloaded.connect(self.on_data_ready,QtCore.Qt.QueuedConnection)
        downloader.start()
        self.updateServer.setEnabled(False)
        
    def onConnectButtonClicked(self):
        if (self.uiState == UIState.CONNECTED):
            self.cf.close_link()
        elif (self.uiState == UIState.CONNECTING):
            self.cf.close_link()
            self.setUIState(UIState.DISCONNECTED)
        else:
            self.connectDialogue.show()
      
    # ------- Connection Callback Handlers -------------------------
    
        
    def connectionFailed(self, linkURI, error):
            msg = "Failed to connect on %s: %s" % (linkURI, error)
            warningCaption = "Communication failure"
            QtGui.QMessageBox.critical(self, warningCaption, msg)
            self.setUIState(UIState.DISCONNECTED, linkURI)
            self.disconnectedFlightData(linkURI)
             
   
    def connectionLost(self, linkURI, msg):
            warningCaption = "Communication failure"
            error = "Connection lost to %s: %s" % (linkURI, msg)
            QtGui.QMessageBox.critical(self, warningCaption, error)
            self.setUIState(UIState.DISCONNECTED, linkURI)
            self.disconnectedFlightData(linkURI)
    
    
    def connectionDone(self, linkURI):
        global _signalThread
        self.setUIState(UIState.CONNECTED, linkURI)
        
        dataThread= threading.Thread(target=self.connectedFlightData(linkURI))
        dataThread.start() 
        
        threading.Thread(target=self.pulse_command).start()
            
    def on_data_ready(self,value):
        global values
        if(value=="error"):
            self.console.setText("Error in connection")
            self.serverConnect.setEnabled(True)
            self.updateServer.setEnabled(True)
            values = None
        else:
            self.targetRoll.setText("%f" % (value[0]))
            self.targetPitch.setText("%f" % (value[1]))
            self.targetThrust.setText("%f" % (value[2]))
            self.targetYaw.setText("%f" % (value[3]))
            self.readInput(value)
    
    
    def readInput(self,value):
                global values,MAX_THRUST,MIN_THRUST,_signalThread
                roll,pitch,thrust,yaw = value
                
                
                     
                if (self.slewEnableLimitV > thrust):
                    if self.oldThrust > self.slewEnableLimitV:
                        self.oldThrust = self.slewEnableLimitV
                    if thrust < (self.oldThrust - (self.thrustLoweringSlewRateLimitV / 100)):
                        thrust = self.oldThrust - self.thrustLoweringSlewRateLimitV / 100
                    if thrust < self.minThrustV:
                        thrust = 0   
                        
                self.oldThrust = thrust
                
                if(thrust>40):
                    thrust  = thrust * MAX_THRUST/100
                else:
                    thrust = MIN_THRUST
                
                
                
                 
               
                pitch = -pitch
               
                values = roll,pitch,yaw,thrust
               
               
                #print str(values)
               
                """
                if(self.uiState!=UIState.CONNECTED):
                    return    
               
               if yaw < -0.2 or yaw > 0.2:
                        if yaw < 0:
                            yaw = (yaw + 0.2) * self.maxYawRateV * 1.25
                        else:
                            yaw = (yaw - 0.2) * self.maxYawRateV * 1.25
                else:
                    self.yaw = 0
                
               
            
                values = roll,pitch,thrust,yaw 
                """
    
    def pulse_command(self): 
        global values
        while(True):
            if(values!=None):
                roll,pitch,yaw,thrust = values
                #print "%f %f %f %f" %(roll, pitch, yaw, thrust)
                self.cf.commander.send_setpoint(roll, pitch, yaw, thrust)
                time.sleep(0.1)
            else:
                break
            
                    
                
    # ------- UI State Handling -------------------   


    def setUIState(self, newState, linkURI=""):
        self.uiState = newState
        if (newState == UIState.DISCONNECTED):
            self.setWindowTitle("Not connected")
            self.connectFlie.setText("Connect")
            self.batteryBar.setValue(3000)
            self.disconnectedFlightData(linkURI)
            self.linkQualityBar.setValue(0)    
        if (newState == UIState.CONNECTED):
            s = "Connected on %s" % linkURI
            self.menuItemConnect.setText("Disconnect")
            self.connectFlie.setText("Disconnect")
        if (newState == UIState.CONNECTING):
            s = "Connecting to %s ..." % linkURI
            self.setWindowTitle(s)
            self.connectFlie.setText("Connecting")

    # ------------------ Flight Data Receiver-----------------------
    
    def connectedFlightData(self, linkURI):
        lg = LogConfig("Battery", 1000)
        lg.addVariable(LogVariable("pm.vbat", "float"))
        self.log = self.cf.log.create_log_packet(lg)
        
        if (self.log != None):
            self.log.data_received.add_callback(self.batteryUpdatedSignal.emit)
            self.log.error.add_callback(self.loggingError)
            self.log.start()
        else:
            print("Could not setup loggingblock!")
        
        lg = LogConfig("Stabalizer", 100)
        lg.addVariable(LogVariable("stabilizer.roll", "float"))
        lg.addVariable(LogVariable("stabilizer.pitch", "float"))
        lg.addVariable(LogVariable("stabilizer.yaw", "float"))
        lg.addVariable(LogVariable("stabilizer.thrust", "uint16_t"))

        self.log = self.cf.log.create_log_packet(lg)
        if (self.log is not None):
            self.log.data_received.add_callback(self._imu_data_signal.emit)
            self.log.error.add_callback(self.loggingError)
            self.log.start()
        else:
            print("Could not setup logconfiguration after "
                           "connection!")

        lg = LogConfig("Motors", 100)
        lg.addVariable(LogVariable("motor.m1", "uint32_t"))
        lg.addVariable(LogVariable("motor.m2", "uint32_t"))
        lg.addVariable(LogVariable("motor.m3", "uint32_t"))
        lg.addVariable(LogVariable("motor.m4", "uint32_t"))

        self.log = self.cf.log.create_log_packet(lg)
        if (self.log is not None):
            self.log.data_received.add_callback(self._motor_data_signal.emit)
            self.log.error.add_callback(self.loggingError)
            self.log.start()
        else:
            print("Could not setup logconfiguration after "
                           "connection!")
    
    def loggingError(self, error):
        logger.warn("logging error %s", error)

    def disconnectedFlightData(self, linkURI):
        self.actualM1.setValue(0)
        self.actualM2.setValue(0)
        self.actualM3.setValue(0)
        self.actualM4.setValue(0)
        self.actualRoll.setText("")
        self.actualPitch.setText("")
        self.actualYaw.setText("")
        self.actualThrust.setText("")
          
        
    def _motor_data_received(self, data):
        self.actualM1.setValue(data["motor.m1"])
        self.actualM2.setValue(data["motor.m2"])
        self.actualM3.setValue(data["motor.m3"])
        self.actualM4.setValue(data["motor.m4"]) 
     
    def _imu_data_received(self, data):
        self.actualRoll.setText(("%.2f" % data["stabilizer.roll"]))
        self.actualPitch.setText(("%.2f" % data["stabilizer.pitch"]))
        self.actualYaw.setText(("%.2f" % data["stabilizer.yaw"]))
        self.actualThrust.setText("%.2f%%" %
                                  self.thrustToPercentage(
                                                  data["stabilizer.thrust"])) 

    def updateBatteryVoltage(self, data):
        self.batteryBar.setValue(int(data["pm.vbat"] * 1000)) 
        
    def thrustToPercentage(self, thrust):
        return ((thrust / MAX_THRUST) * 100.0)

    def percentageToThrust(self, percentage):
        return int(MAX_THRUST * (percentage / 100.0))

# -------------------  Android Server Thread -----------------------------------
            
class DownloadThread(QtCore.QThread):

    data_downloaded = QtCore.pyqtSignal(object)
    def __init__(self, url, console):
        QtCore.QThread.__init__(self)
        self.url = url
        self.console = console

    def run(self):
        global ex
        try: 
            response = urllib2.urlopen(self.url)
        except urllib2.HTTPError, e:
            self.data_downloaded.emit("error")
            ex=True
        except urllib2.URLError, e:
            self.data_downloaded.emit("error")
            ex=True
        except httplib.HTTPException, e:
            self.data_downloaded.emit("error")
            ex=True
        
        while(ex!=True):
            try:
                html = response.read(27);
                if(len(html)>0):
                    index = html.find("]")
                    html = html[:index+1]
                    values = ast.literal_eval(html)
                    consoleText= "Roll: %f | Pitch: %f | Thrust: %f | Yaw: %f" % (values[0],values[1],values[2],values[3])  
                    # print consoleText
                    self.data_downloaded.emit(values)
                else:
                    self.data_downloaded.emit("error")
                    break
            except:
                continue
                pass

# ----------------------- CrazyFlie Thread ---------------------

class CrazyFlieThread(QtCore.QThread):
   
    global values
    def __init__(self,cf):
        QtCore.QThread.__init__(self)
        self.values = values
        self.cf = cf    
    
    def run(self):       
            if(self.values!=None or self.values!="error"):
                roll,pitch,yaw,thrust = self.values
                self.pulse_command(roll,pitch,yaw,thrust)
                    
            
            
       
    def pulse_command(self,roll,pitch,yaw,thrust):
            print "%f %f %f %f" %(roll, pitch, yaw, thrust)
            
            self.cf.commander.send_setpoint(roll, pitch, yaw, thrust)
            time.sleep(0.1)
            
            self.pulse_command(roll, pitch, yaw,thrust)
            
                                 
        
# ----------------------------- Main ------------------------------

def main():
    app = QtGui.QApplication(sys.argv)
    ex = App()
    sys.exit(app.exec_())


if __name__ == '__main__':
    main()