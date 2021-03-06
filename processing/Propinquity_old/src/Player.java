import java.util.ArrayList;

import processing.core.PApplet;
import processing.core.PConstants;
import xbee.*;

import ddf.minim.AudioPlayer;

public class Player implements PConstants {

  final int PROXIMITY_LOW_THRESHOLD = 80;

  //xbee
  final int XPAN_PROX_BASES = 1; //2;
  final int XPAN_ACCEL_BASES = 1;
  final int XPAN_VIBE_BASES = 1;

  final boolean USE_ACCEL = false; //when false, touch event triggers when proximity is 0

  final int VIBE_DIFF_THRESHOLD = 15;

  final int TOUCH_PENALITY_PTS = 100;

  final float HUD_ROTATION = PI/256f;
  final float HUD_FRICTION = 0.94f;

  Propinquity parent;

  String name;
  int clr;

  float hudAngle;
  float hudVel;
  
  Step[] steps;
  int periodPts; //total number of pts for the period
  int totalPts;  //total number of pts
  int killPts;   //number of pts to remove

  int stepProximity;
  boolean stepTouched;
  int stepReadings;

  final int NUM_ACCEL_READINGS = 4;
  final int ACCEL_MULTIPLIER_THRESHOLD = 250;
  int[] accelReadings = new int[NUM_ACCEL_READINGS];
  int accelReadingsIndex = 0;
  int accelMultiplier;

  //boolean stubbed;
  final int NUM_PROX_READINGS = 4;
  int[] recentReadings = new int[NUM_PROX_READINGS];
  int ri = 0;
  int averageReading;
  int lastVibe;
  //int totalVibe;

  //xbee
  XPan[] xpansProx;
  XPan[] xpansAccel;
  XPan[] xpansVibe;
  //int[] outdata;
  int numPatches;

  //stubs
  ArrayList proxStub = null;
  int proxStubIndex = 0;
  ArrayList accelStub = null;
  int accelStubIndex = 0; 
  
  //audio feedback
  AudioPlayer negSoundPlayer = null;
  AudioPlayer coopNegSoundPlayer = null;
  
  boolean coopMode;

  //public Player(PApplet p, String n, color c)
  public Player(Propinquity p, int c)
  {
    this.parent = p;
    this.name = "noname";
    this.clr = c;
    this.xpansProx = new XPan[XPAN_PROX_BASES];
    this.xpansAccel = new XPan[XPAN_ACCEL_BASES];
    this.xpansVibe = new XPan[XPAN_VIBE_BASES];
    this.numPatches = 0;
    reset();
  }

  public void reset()
  {
    steps = null;
    periodPts = 0;
    totalPts = 0;
    stepProximity = 0;
    stepReadings = 0;
    stepTouched = false;

    hudAngle = 0; //same as coop default angle
    hudVel = 0;

    accelMultiplier = 1;
    accelReadingsIndex = 0;
    for (int i=0; i<NUM_ACCEL_READINGS; i++)
      accelReadings[i] = 0; 

    ri = 0;
    for (int i=0; i<NUM_PROX_READINGS; i++)
      recentReadings[i] = 0; 

    lastVibe = 0;

    //reset stub
    if (proxStub != null) proxStubIndex = 0;
    if (accelStub != null) accelStubIndex = 0;
  }

  public void clear() {
    //clear vibration
    sendVibes(0);

    //close xbee comm   
    for(int i = 0; i < xpansProx.length; i++)
      if (xpansProx[i] != null) xpansProx[i].stop();
    for(int i = 0; i < xpansVibe.length; i++)
      if (xpansVibe[i] != null) xpansVibe[i].stop();
    for(int i = 0; i < xpansAccel.length; i++)
      if (xpansAccel[i] != null) xpansAccel[i].stop();
  }

  public String getName() { 
    return name;
  }
  public int getColor() { 
    return clr;
  }
  
  public void setNumPatches(int num) {
   numPatches = num; 
  }
  
  public void addNegSound(AudioPlayer ap) {
   negSoundPlayer = ap; 
  }
  
  public void addCoopNegSound(AudioPlayer ap) {
   coopNegSoundPlayer = ap; 
  }
  
  public void playNegSound() {
   if (!parent.MUTE)
   {
     if (isInCoopMode())
     {
       if (coopNegSoundPlayer != null)
       {
         coopNegSoundPlayer.play();
         coopNegSoundPlayer.rewind();
       } 
     }
     else
     {
       if (negSoundPlayer != null)
       {
          negSoundPlayer.play();
          negSoundPlayer.rewind();
       } 
     }
   }
  }

  public void approachHudTo(float targetAngle) {
    if (hudAngle == targetAngle) return; 
    
    float diff = targetAngle - hudAngle;
    int dir = diff > 0 ? 1 : -1;
    
    if (diff*dir > HUD_ROTATION) {
      hudVel += HUD_ROTATION*dir;
    }
    else {
      hudAngle = targetAngle;
    }
    
    hudVel *= HUD_FRICTION;
    
    hudAngle += hudVel;
  }

  public void initializeSteps(int stepLength) { 
    steps = new Step[stepLength];
  }
  public void addStep(Step s, int i) { 
    steps[i] = s;
  } 
  public Step getStep(int i) { 
    return steps[i];
  }

  //public void setStubbed(boolean s) { stubbed = s; }
  public void subPeriodPts(int pts) { 
    periodPts -= pts;
  }
  public void subKillPts(int pts) { 
    killPts -= pts;
  }
  public int getPeriodPts() { 
    return periodPts;
  }
  public int getTotalPts() { 
    return totalPts;
  }
  public int getKillPts() { 
    return killPts;
  }

  public void addPts(int pts) {
    periodPts += pts;
    totalPts += pts;
  }

  public void removePts(int pts) { 
    periodPts -= pts;
    if (periodPts < 0) periodPts = 0;
    totalPts -= pts;
    if (totalPts < 0) totalPts = 0;
    killPts += pts;
  }

  public int processStep() {
    int result = 0;

    //if the player touched, then remove penality pts
    if (hasTouched()) {
      parent.println(name + " TOUCHED "); 
      removePts(TOUCH_PENALITY_PTS);
      result = -1;
     }
    //else add pts
    else {
      //parent.println(name + " scores " + getProximity()*accelMultiplier + " (" + accelMultiplier + ")"); 
      addPts(getProximity()*accelMultiplier);
      //if (accelMultiplier > 1) result = 1;
      if (getProximity() > 180) result = 1;
      else if (!USE_ACCEL && getProximity() == 0 && stepReadings > 1) result = -1;
    }

    stepTouched = false;
    stepProximity = 0;
    stepReadings = 0; 

    return result;
  }
  
  public void processConfigAck(int patch, int turnLength)
  {
    
  }

  public void processProxReading(int patch, int step, boolean touched, int proximity) {
    //check if the reading is for the current step or if it's too late
    //TODO
    
    //keep track of the step touched
    //and give negative audio feedback immediately
    if (!stepTouched && touched)
    {
      playNegSound(); 
    }
    stepTouched |= touched;

    //filter out the low values
    if (proximity < PROXIMITY_LOW_THRESHOLD)
      return;

    //add up readings and counter
    stepProximity += proximity;
    stepReadings++;

    //avg last 4 readings and send them out to vibes
    recentReadings[ri++] = proximity;
    if (ri >= NUM_PROX_READINGS) ri = 0;
    averageReading = recentReadings[0];
    for (int i=0; i<NUM_PROX_READINGS; i++)
      if (recentReadings[i] > averageReading) averageReading = recentReadings[i];

    int total = 0;
    for (int i=0; i<NUM_PROX_READINGS; i++)
      total += recentReadings[i];

    averageReading = total/NUM_PROX_READINGS;
    sendVibes(averageReading);
  }

  public void processAccelReading(int patch, int x, int y, int z) {
    //keep track of values
    accelReadings[accelReadingsIndex++] = x+y+z;
    if (accelReadingsIndex >= NUM_ACCEL_READINGS) accelReadingsIndex = 0;

    //calculate average
    int avg = 0;
    for(int i = 0; i < NUM_ACCEL_READINGS; i++)
      avg += accelReadings[i];
    avg /= NUM_ACCEL_READINGS;

    if (avg < ACCEL_MULTIPLIER_THRESHOLD)
      accelMultiplier = 1;
    else
      accelMultiplier = parent.min((avg-ACCEL_MULTIPLIER_THRESHOLD+100)/100, 4);

    //parent.println(name + ": " + patch + " " + x + ", " + y + ", " + z + " : " + accelMultiplier);
  }

  public boolean hasTouched() { 
    return stepTouched;
  }
  public int getProximity() { 
    return stepReadings==0?0:stepProximity/stepReadings;
  }
  public void setCoopMode(boolean b) {
   coopMode = b; 
  }
  public boolean isInCoopMode() {
   return coopMode; 
  }

  ProxData nextProxStub(long time) {
    if (proxStub == null) return null;
    if (proxStubIndex >= proxStub.size()) return null;

    String[] data = ((String)proxStub.get(proxStubIndex)).split(",");

    //check if we reached the time for this step
    if (Integer.valueOf(data[0]) >= time) return null;

    //parent.println(time + ": " + Integer.valueOf(data[2]) + " " + (Integer.valueOf(data[3])==1) + " " + Integer.valueOf(data[4]));

    /*processProxReading(Integer.valueOf(data[2]),
                       proxStubIndex,
                       Integer.valueOf(data[3])==1,
                       Integer.valueOf(data[4]));*/

    return new ProxData(Integer.valueOf(data[1]), Integer.valueOf(data[2]), proxStubIndex++, Integer.valueOf(data[3])==1, Integer.valueOf(data[4]));
  }

  AccelData nextAccelStub(long time) {
    if (accelStub == null) return null;
    if (accelStubIndex >= accelStub.size()) return null;

    String[] data = ((String)accelStub.get(accelStubIndex)).split(",");

    //check if we reached the time for this step
    if (Integer.valueOf(data[0]) >= time) return null;

    /*processAccelReading(Integer.valueOf(data[2]),
    Integer.valueOf(data[3]),
    Integer.valueOf(data[4]),
    Integer.valueOf(data[5]));*/

    accelStubIndex++;
    
    return new AccelData(Integer.valueOf(data[1]), Integer.valueOf(data[2]), Integer.valueOf(data[3]), Integer.valueOf(data[4]), Integer.valueOf(data[5]));
  }  

  void loadProxStub(int index, String stubFile) {
    //proximity data stub
    String[] data = parent.loadStrings(stubFile);
    if (data == null || data.length == 0) {
      parent.println("Error: Proximity stub was empty. I don't think that's right.");
    }

    proxStub = new ArrayList();

    //parse to keep only data for this player
    String[] dataline;
    for(int i = 0; i < data.length; i++) {
      dataline = data[i].split(",");

      if (dataline.length != 5) {
        parent.println("Warning: Proximity stub line " + i + " (" + data[i] + ") is not formatted correctly");
        continue;
      }

      if (Integer.valueOf(dataline[1]) == index) {
        proxStub.add(data[i]);
      }
    }

    //start the stub at the beginning               
    proxStubIndex = 0;

    parent.println(" Proximity stub... " + proxStub.size());
  } 

  void loadAccelStub(int index, String stubFile) {
    //proximity data stub
    String[] data = parent.loadStrings(stubFile);
    if (data == null || data.length == 0) {
      parent.println("Error: Proximity stub was empty. I don't think that's right.");
    }

    accelStub = new ArrayList();

    //parse to keep only data for this player
    String[] dataline;
    for(int i = 0; i < data.length; i++) {
      dataline = data[i].split(",");

      if (dataline.length != 6) {
        parent.println("Warning: Proximity stub line " + i + " (" + data[i] + ") is not formatted correctly");
        continue;
      }

      if (Integer.valueOf(dataline[1]) == index) {
        accelStub.add(data[i]);
      }
    }

    //start the stub at the beginning               
    accelStubIndex = 0;

    parent.println(" Accelerometer stub... " + accelStub.size());
  }  

  void initProxComm(String ni1, String ni2)
  {
    //TODO load bases using their serial number...?
    if (ni1 != null) {
      XBeeReader xbee = parent.xbeeManager.reader(ni1);
      if (xbee != null) {
        xpansProx[0] = new XPan(xbee, parent);
        parent.println("Initialized Xbee for proximity #1: " + ni1);
      }
      else {
        System.err.println("Could not initialize Xbee for proximity #1: " + ni1);
      }
    }
    if (ni2 != null) {
      XBeeReader xbee = parent.xbeeManager.reader(ni2);
      if (xbee != null) {
        xpansProx[1] = new XPan(xbee, parent);
        parent.println("Initialized Xbee for proximity #2: " + ni2);
      }
      else {
        System.err.println("Could not initialize Xbee for proximity #2: " + ni2);
      }
    }

    //create the data packet that requests proximity values
    //outdata = new int[XPan.PROX_OUT_PACKET_LENGTH];
    //outdata[0] = XPan.PROX_OUT_PACKET_TYPE;
    //for (int i=1; i < outdata.length; i++)
    //  outdata[i] = 0;
  }

  XPan[] getProxXPans() { return xpansProx; }

  void initAccelComm(String ni)
  {
    if (ni == null) return;

    XBeeReader xbee = parent.xbeeManager.reader(ni);
    if (xbee != null) {
      xpansAccel[0] = new XPan(xbee, parent);
      parent.println("Initialized Xbee for acceleration: " + ni);
    }
    else {
      System.err.println("Could not initialize Xbee for acceleration: " + ni);
    }
  }

  void initVibeComm(String ni)
  {
    if (ni == null) return;

    XBeeReader xbee = parent.xbeeManager.reader(ni);
    if (xbee != null) {
      xpansVibe[0] = new XPan(xbee, parent);
      parent.println("Initialized Xbee for vibration: " + ni);
    }
    else {
      System.err.println("Could not initialize Xbee for vibration: " + ni);
    }
  }

  public void sendStep(int stepNum)
  {
    //parent.println(name + " sending step: " + stepNum);
    if (xpansProx[0] == null) return;

    //broadcast step to patches
    //xpansProx[0].sendOutgoing(XPan.BROADCAST_ADDR, outdata, stepNum, 0/*???*/);
    Step step1 = stepNum < steps.length ? steps[stepNum] : null;
    Step step2 = stepNum+1 < steps.length ? steps[stepNum+1] : null;
    Step step3 = stepNum+2 < steps.length ? steps[stepNum+2] : null;
    Step step4 = stepNum+3 < steps.length ? steps[stepNum+3] : null;
    //xpansProx[0].broadcastStep(stepNum, step1, step2, step3, step4); //Does this only broadcast to two of the prox sensors then?
    for(int i = 0; i < xpansProx.length; i++)
      if (xpansProx[i] != null) xpansProx[i].broadcastStep(stepNum, step1, step2, step3, step4);
  }  

  public void sendVibes(int avgReading) { sendVibes(avgReading, false); }
  public void sendVibes(int avgReading, boolean override) {
    //make sure the vibe xbee is present
    if (xpansVibe[0] == null) return;

    //if we are not overriding the threshold filter
    if (!override) { 
      //then donothing is the value is less than the 
      //threshold away from the last sent value
      int diff = lastVibe - avgReading;
      if (diff < 0) diff *= -1;
      if (diff < VIBE_DIFF_THRESHOLD) return;
    }

    //broadcast vibe paket
    //int[] myData = { XPan.VIBE_OUT_PACKET_TYPE, avgReading };
    //xpansVibe[0].sendOutgoing(XPan.BROADCAST_ADDR, myData); 
    xpansVibe[0].broadcastVibe(avgReading);

    //keep track of last sent value
    lastVibe = avgReading;
  }  
  
  public void sendConfig(int stepLength)
  {
    for (int i = 0; i < xpansProx.length; i++)
      if (xpansProx[i] != null) xpansProx[i].broadcastProxConfig(stepLength);
  }

  //TODO replace this with configPatches to pass the step length
  //at the same time as detecting which ones respond.
  public void discoverPatches() {
    parent.println("Discover patches...");
    for(int i = 0; i < XPAN_PROX_BASES; i++)
      if (xpansProx[i] != null) {
        parent.println("Discover proximity " + (i+1));
        xpansProx[i].nodeDiscover();
      }

    for(int i = 0; i < XPAN_ACCEL_BASES; i++)
      if (xpansAccel[i] != null) {
        parent.println("Discover acceleration " + (i+1));
        xpansAccel[i].nodeDiscover();
      }

    for(int i = 0; i < XPAN_VIBE_BASES; i++)
      if (xpansVibe[i] != null) {
        parent.println("Discover vibration " + (i+1));
        xpansVibe[i].nodeDiscover();
      }
  }
}

