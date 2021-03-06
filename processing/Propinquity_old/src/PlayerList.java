import java.util.ArrayList;

import controlP5.Button;
import controlP5.CVector3f;
import controlP5.ControlEvent;
import controlP5.ControlP5;
import controlP5.Textfield;

import processing.core.*;

public class PlayerList {
  final String PLIST_FILE = "player.lst";
  final int PLIST_MAX_PLAYERS = 12;
  final int PLIST_WIDTH = 200;
  final int PLIST_NEXT_WIDTH = 50;
  final int PLIST_NEXT_HEIGHT = 20;
  final int PLIST_NEW_WIDTH = 100;
  final int PLIST_NEW_HEIGHT = 20;
  final int PLIST_PLAYER_HEIGHT = 20;
  final int PLIST_REMOVE_WIDTH = 12;
  final int PLIST_REMOVE_HEIGHT = 20;
  final int PLIST_VERT_SPACER = 20;
  final int PLIST_NEXT_ID = 1;
  final int PLIST_NEW_ID = 2;

  PApplet parent;
  ControlP5 controlP5;
  ArrayList<controlP5.Controller> removeQueue;

  String[] playerNames;
  ArrayList<Textfield> playerFields;
  ArrayList<Button> removeButtons;
  Button nextButton;
  Button newButton;
  
  boolean done;

  public PlayerList(PApplet p) {
    parent = p;
    done = false;

    removeQueue = new ArrayList();
    controlP5 = new ControlP5(p);
  
    //create text fields for each
    playerFields = new ArrayList();
    removeButtons = new ArrayList();
                        
    //load the player list
    //playerList = new ArrayList();
    String[] players = p.loadStrings(PLIST_FILE);
    if (players != null) {
      for(int i = 0; i < players.length; i++) {
        if (players[i].length() > 0) {
          //add UI
          addPlayer(players[i]);
        }
      }
    }
    
    if (playerFields.size() < 1) addPlayer("Player 1");
    if (playerFields.size() < 2) addPlayer("Player 2");
    
    //create button to add new players
    newButton = controlP5.addButton("NEW PLAYER", 0,
                                       parent.width/2 - PLIST_WIDTH/2, parent.height/2,
                                       PLIST_NEW_WIDTH, PLIST_NEW_HEIGHT);
    newButton.setId(PLIST_NEW_ID);
  
    //create next button
    nextButton = controlP5.addButton("NEXT", 0,
    		parent.width/2 + PLIST_WIDTH/2 - PLIST_NEXT_WIDTH, parent.height/2,
                                       PLIST_NEXT_WIDTH, PLIST_NEXT_HEIGHT);
    nextButton.setId(PLIST_NEXT_ID);
    
    layout();
  }

  public String[] getNames() { return playerNames; }
  
  public boolean isDone() { return done; }
  
  public void update() {
    //process controlP5 remove queue
    for(int i = 0; i < removeQueue.size(); i++)
      controlP5.remove(((controlP5.Controller)removeQueue.get(i)).name());
    removeQueue.clear();
  }
  
  void addPlayer(String name) {
    //add a new text field to the list
    Textfield playerField = controlP5.addTextfield("Player "+playerFields.size(),
    		parent.width/2 - PLIST_WIDTH/2, parent.height/2,
                                                   PLIST_WIDTH - PLIST_REMOVE_WIDTH*2,
                                                   PLIST_PLAYER_HEIGHT);
    playerField.setAutoClear(false);
    playerField.setText(name);
    playerField.setCaptionLabel("Player " + (playerFields.size()+1));
    playerField.setFocus(true);
    
    //add a matching remove button
    Button removeBtn = controlP5.addButton("Remove "+playerFields.size(), playerFields.size(),
    		parent.width/2 + PLIST_WIDTH/2 - PLIST_REMOVE_WIDTH, parent.height/2,
                                           PLIST_REMOVE_WIDTH, PLIST_REMOVE_HEIGHT);
    removeBtn.setCaptionLabel("x");

    playerFields.add(playerField);
    removeButtons.add(removeBtn);
  }
  
  public void layout() {
    float y = parent.height/2 - (PLIST_PLAYER_HEIGHT + PLIST_VERT_SPACER)*(playerFields.size()+1)/2;
  
    //move existing player fields up
    CVector3f pos = new CVector3f(0, 0, 0);
    Textfield tf;
    Button btn;
    for(int i = 0; i < playerFields.size(); i++) {
      tf = (Textfield)playerFields.get(i);
      pos = tf.position();
      tf.setPosition(pos.x, y); 
  
      btn = (Button)removeButtons.get(i);
      pos = btn.position();
      btn.setPosition(pos.x, y);
      
      y += PLIST_PLAYER_HEIGHT + PLIST_VERT_SPACER;
    }
    
    y += PLIST_VERT_SPACER;
            
    //move buttons down       
    pos = newButton.position();
    newButton.setPosition(pos.x, y);
    pos = nextButton.position();
    nextButton.setPosition(pos.x, y);
    
    if (playerFields.size() >= PLIST_MAX_PLAYERS)
      newButton.hide();
  }
  
  public void process() {
    //clear empty textfields
    for(int i = 0; i < playerFields.size(); i++) {
      Textfield tf = playerFields.get(i);
      if (tf.getText().length() == 0) {
        playerFields.remove(i);
        i--;
      }
    }      
  
    //save player list
    playerNames = new String[parent.max(playerFields.size(), 2)];
    playerNames[0] = "Player 1";
    playerNames[1] = "Player 2";
  
    for(int i = 0; i < playerFields.size(); i++)
      playerNames[i] = playerFields.get(i).getText();
    
    parent.saveStrings(parent.dataPath(PLIST_FILE), playerNames);
    
    done = true;
  }
  
  public void dispose() {
    controlP5.dispose();
    controlP5 = null;
  }
  
  public void controlEvent(ControlEvent theEvent) {
    switch(theEvent.controller().id()) {
      case(PLIST_NEXT_ID):
        process();
        break;
      case(PLIST_NEW_ID):
        addPlayer("");
        layout();
        
        break;
      default:
        if (theEvent.controller() instanceof Button) {
          //remove textfield that matches button value
          int value = (int)theEvent.controller().value();
          int i;
          
          controlP5.Controller ctrl;
          ctrl = controlP5.controller("Player " + value);
          i = playerFields.indexOf(ctrl);
          playerFields.remove(i);
          ctrl.hide();
          removeQueue.add(ctrl);
          
          //remove button itself
          ctrl = controlP5.controller("Remove " + value);
          removeButtons.remove(i);
          ctrl.hide();
          removeQueue.add(ctrl);
          
          //adjust values
          for(; i < playerFields.size(); i++) {
            Textfield rtf = (Textfield)playerFields.get(i);
            rtf.setCaptionLabel("Player " + (i+1));
          }
          
          layout();
        }
        break;
    }
  }
}
