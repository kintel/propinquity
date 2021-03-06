import processing.serial.*;
import xbee.*; // xbee library by shiffman & faludi, version 1.5



final int GAME_STATE_INITIAL = 0;
final int GAME_STATE_CHECK_SERIAL = 1;
final int GAME_STATE_CHECK_XPAN = 2;
final int GAME_STATE_SEND_DISCOVER = 3;
final int GAME_STATE_RECEIVE_DISCOVER = 4;
final int GAME_STATE_CHECKS_DONE = 5;
final int GAME_STATE_COMMUNICATE = 6;
int gameState = GAME_STATE_CHECK_SERIAL;

final int XBEE_DISCOVER_TIMEOUT = 10000; // 10 sec - too short if we have other USB devices

XBeeManager xbeeManager;
Player[] players;

ArrayList foundProxs;
ArrayList foundVibes;
ArrayList foundAccels;
ArrayList foundUndefs;

// No other USB devices please
// Unplug and replug if other devices were plugged in!

void setup() {

  // 1. scan test for local xbees 
  xbeeManager = new XBeeManager(this);

  // 2. discover test for remote xbees
  foundProxs = new ArrayList();
  foundVibes = new ArrayList();
  foundAccels = new ArrayList();
  foundUndefs = new ArrayList();
}


void draw() {
  int startDiscover = 0; // time when discover package is sent
  
  // scan test for local xbees via serial
  if (gameState == GAME_STATE_CHECK_SERIAL) {
    if (xbeeManager.hasAllLocalXbees()) {
      gameState++;
    }
  }
  // set up networks for all local xbees
  else if (gameState == GAME_STATE_CHECK_XPAN) {
    initPlayers();
    gameState++;
  }
  // discover remote xbees
  else if (gameState == GAME_STATE_SEND_DISCOVER) {
    println("Scanning for remote xbees...");
    for (int i=0; i<=1; i++) {
      println("Player "+i+":");
      players[i].discoverRemoteXbees();
    }
    startDiscover = millis();
    gameState++;
  }
  else if (gameState == GAME_STATE_RECEIVE_DISCOVER) {
    // TODO: if all remote xbees are found OR timeout OR user input
    if (millis()-startDiscover < XBEE_DISCOVER_TIMEOUT) {
      if (xbeeManager.hasAllRemoteXbees()) {
        gameState++;
      }
    }
    else {
      println("Not all remote xbees found");
      exit();
    }
  }
  else if (gameState == GAME_STATE_CHECKS_DONE) {
    printDiscovered();
    gameState++;
  }
  else if (gameState == GAME_STATE_COMMUNICATE) {
  }
}

void printDiscovered() {
  if (!foundProxs.isEmpty() 
  &&  !foundVibes.isEmpty() 
  && !foundAccels.isEmpty()
  && !foundUndefs.isEmpty()) {
    println("No remote xbees found");
    exit();
  }
  else {
    printDiscovered(foundProxs, "proximity patch");
    printDiscovered(foundVibes, "vibration glove");
    printDiscovered(foundAccels, "accelerometer anklet");
    printDiscovered(foundUndefs, "undefined");
  }
}

void printDiscovered(ArrayList discovered, String kind) {
  if (!discovered.isEmpty()) {
    println("Discovered "+kind+" remote xbee senders");
    for(int i=0; i<discovered.size() ; i++)
      println(discovered.get(i));
  }
}

void initPlayers() {
  players = new Player[2];
  players[0] = new Player(this);
  players[1] = new Player(this);
  
  // TODO: Node IDs are hard coded, we want this from the scan!!
  // node identifyers of local xbees for player 1 
  ArrayList<String[]> NIS_PLAYER1 = new ArrayList<String[]>();
  String[] proxNIs = {"P1_PROX1","P1_PROX2"};
  String[] accelNI = {"P1_ACCEL"};
  String[] vibeNI = {"P1_VIBE"};
  NIS_PLAYER1.add(proxNIs);
  NIS_PLAYER1.add(accelNI);
  NIS_PLAYER1.add(vibeNI);
  // player 2
  ArrayList<String[]> NIS_PLAYER2 = new ArrayList<String[]>();
  String[] proxNIs2 = {"P2_PROX1","P2_PROX2"};
  String[] accelNI2 = {"P2_ACCEL"};
  String[] vibeNI2 = {"P2_VIBE"};
  NIS_PLAYER2.add(proxNIs2);
  NIS_PLAYER2.add(accelNI2);
  NIS_PLAYER2.add(vibeNI2);
  
  players[0].init(NIS_PLAYER1);
  players[1].init(NIS_PLAYER2);
}


void xBeeEvent(XBeeReader xbee) {
  print(".");
  switch (gameState) {
    case GAME_STATE_CHECK_SERIAL:
      xbeeManager.foundLocalXbeeEvent(xbee);
      break;
    case GAME_STATE_RECEIVE_DISCOVER:
      xbeeManager.foundRemoteXbeeEvent(xbee);
      break;
    case GAME_STATE_COMMUNICATE:
      XBeeDataFrame data = xbee.getXBeeReading();
      if (data.getApiID() == xbee.SERIES1_RX16PACKET) {
        int[] packet = data.getBytes();
        int addr = data.getAddress16();
        int rssi = data.getRSSI(); // received signal strength in dBM
        println("Addr "+binary(addr,16)+" rssi "+rssi);
        //exit();
        parsePacket(packet);
      }
      else {
        println("Bad packet received by game frontend.");
        println("apiid "+data.getApiID());
        //exit();
      }
      break;
    default:
      break;
  }
}

void parsePacket(int[] packet) {
  if (packet.length == XPan.PROX_IN_PACKET_LENGTH 
       && packet[0] == XPan.PROX_IN_PACKET_TYPE) { 
    int patch = (packet[1] >> 1);                
    int player = getPlayerIndexForPatch(patch);
    
    if (player != -1) {
      // TODO packet[1] contains boolean touched, which is obsolete
      int step = ((packet[2] & 0xFF) << 8) | (packet[3] & 0xFF);
      //println(step);
      int proximity = ((packet[4] & 0xFF) << 8) | (packet[5] & 0xFF);
      println("Patch "+patch+" player "+player+" sent prox "+proximity);
      //players[0].broadcastVibe();
    }
    else
      System.err.println("Error: received a packet from patch '"+ patch + 
      "', which is not assigned to a player");
  }
}

// TODO hard coded
public int getPlayerIndexForPatch(int patch) {
  if (patch >= 1 && patch <= 4) return 0;
  else if (patch >= 9 && patch <= 16) return 1;
  else return -1;
}
