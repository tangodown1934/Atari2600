/*
 * Intercessor.java
 *
 * Created on August 13, 2007, 7:38 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package jstella.runner;

import java.awt.event.*;
import java.awt.*;
import javax.swing.*;
import java.io.*;
import jstella.core.*;
import jstella.cart.*;



import static jstella.core.JSConstants.*;



/**
 * A go-between for the GUI classes and JSConsole.  It is mostly a way of
 * centralizing commonly used methods for the various GUI classes (JStellaMain,
 * JStellaApplet, etc.)
 * It is also a way of letting those who work strictly with the GUI be free from
 * a lot of the behind-the-scenes stuff.
 * <p>
 * It handles the timers, etc.
 * </p>
 * 
 * This class is optional--GUI classes are free to interact directly with the JSConsole.
 * @author Sysop
 */
public class Intercessor implements InputMaster.IfcInputMasterClient, IfcConsoleClient {
    
    //NTSC and PAL are television formats...NTSC is used the U.S.
    // -NTSC televisions update the screen 60 times/sec (i.e. 60 Hertz).  To emulate this,
    // a util timer is used, with a delay of 17 milliseconds.  Thus, there are (1000 ms /17 ms) or about 58.8 cycles
    // per second using this delay.
    
    private final static int TIMER_DELAY_NTSC=17;
    private final static int TIMER_DELAY_PAL=20;
    private final static int TIMER_DELAY_SNOW=100;
  
    
   
    private java.util.Timer myUtilTimer=null; 
    private InputMaster myInputMaster=new InputMaster(this);
    private JStellaCanvas myCanvas=null;
    
    
    private IfcIntercessorClient myIntercessorClient=null;
    private JSConsole myConsole=null;
    
    private VirtualJoystickDialog myVirtualJoystickDialog=null;
  
    
    
    private int myCurrentTimerDelay=TIMER_DELAY_NTSC;
    
    private boolean myAutoPauseMode=false;
    
    private boolean myPausedByPlayer=false;
    private boolean myPausedByFocusLoss=false;
    
    
    
    
    
   
    private IntercessorKeyboardFocusListener myCanvasFocusListener=new IntercessorKeyboardFocusListener();
    
    
    
    
    
    /**
     * Creates a new instance of Intercessor.  GUI classes should implement the interface
     * IfcIntercessorClient, which contains methods telling the GUI to update this-and-that.
     * @param aClient the client (i.e. GUI class) of the intercessor.
     */
    public Intercessor(IfcIntercessorClient aClient) {
        myIntercessorClient=aClient;
        createCanvas();
        initConsole(new JSConsole(this));
    }
    
    private void createCanvas()
    {
         if (myCanvas==null) 
        {
            myCanvas=new JStellaCanvas();
           myCanvas.addKeyListener(myInputMaster.getKeyListener());
           myCanvas.addFocusListener(myCanvasFocusListener);
           //myCanvas.setInputVerifier(new IntercessorInputVerifier());
           
           myInputMaster.addPaddleToComponent(0, myCanvas);
           if (myIntercessorClient!=null) myIntercessorClient.displayCanvas(myCanvas);
        }
    }
    
    private void initConsole(JSConsole aConsole) {
        if ((myConsole!=null)&&(myConsole!=aConsole)) {
            myConsole.destroy();
        }//end : destroy old console
        myConsole=aConsole;
        myConsole.setConsoleClient(this);
    
        
        //myConsole.setTelevisionMode(JSConstants.TELEVISION_MODE_TEST_PATTERN);
       updateTelevisionMode();
        
     
        
        
      
        
        
        
    
        myCanvas.requestFocusInWindow();
        updateTimerDelay();
        // myConsole.pauseAudio();
    }
    
    /**
     * Returns the cartridge object.  Will return NULL if no cartridge has been loaded.
     * @return the cartridge object
     */
    public Cartridge getCartridge() {
        return myConsole.getCartridge();
    }
    
    private void updateTimerDelay() {
        if (myConsole.getTelevisionMode()==TELEVISION_MODE_SNOW) myCurrentTimerDelay=TIMER_DELAY_SNOW;
        else if (myConsole.getDisplayFormat()==DisplayFormat.PAL) myCurrentTimerDelay=TIMER_DELAY_PAL;
        else myCurrentTimerDelay=TIMER_DELAY_NTSC;
       myConsole.getAudio().setRealDisplayFrameRate(1000.0 / (double)myCurrentTimerDelay);  
    }
    
    
    public InputMaster getInputMaster()
    {
        return myInputMaster;
    }

    
    
    /**
     * Changes the television mode (game, tv test pattern, snow/static) based
     * on whether a cartridge is loaded, and if not, what the configuration specifies.
     */
    public void updateTelevisionMode()
    {
        
        if (myConsole.getCartridge()!=null) myConsole.setTelevisionMode(TELEVISION_MODE_GAME);
        else
        {
            String zDefaultScreen=myIntercessorClient.getConfiguration().get(JStellaMain.CONFIG_KEY_DEFAULT_SCREEN);
           if (JStellaMain.CONFIG_VALUE_DEFAULT_SCREEN_SNOW.equals(zDefaultScreen)) myConsole.setTelevisionMode(TELEVISION_MODE_SNOW);
           else myConsole.setTelevisionMode(TELEVISION_MODE_TEST_PATTERN);
        }//end : no cartridge loaded
       myConsole.updateVideoFrame();
        
       // myConsole.doFrame();
        updateTimerDelay();
    }
    
    public boolean isVirtualJoystickEnabled() {
        if ((myVirtualJoystickDialog==null)||(myVirtualJoystickDialog.isVisible()==false)) return false;
        else return true;
    }
    
    /**
     * The virtual joystick is a separate window with a graphical representation of a 
     * 2600 joystick that allows the user to use the mouse to emulate joystick movement.
     * @param aParent the parent window of which the virtual joystick window will be a child (sort-of)
     */
    public void enableVirtualJoystick(java.awt.Frame aParent) {
        
        if (myVirtualJoystickDialog==null) {
            myVirtualJoystickDialog=new VirtualJoystickDialog(aParent, myInputMaster);
        }//end : is null
        myVirtualJoystickDialog.setVisible(true);
        // this.setAutoPauseMode(false);
        
    }
    
    public void disableVirtualJoystick() {
        if (myVirtualJoystickDialog!=null) myVirtualJoystickDialog.setVisible(false);
       
    }
    
    public void toggleVirtualJoystick(java.awt.Frame aParent) {
        if (isVirtualJoystickEnabled()==false) enableVirtualJoystick(aParent);
        else disableVirtualJoystick();
    }
    
    
    
    /**
     * Starts the timer.  The timer is used to actually "run" the 2600 emulator.  
     * This method is often called from other Intercessor methods, so it may be 
     * unnecessary for the GUI class to call it.
     */
    public void startTimer() {
        
        myIntercessorClient.informUserOfPause(false);
       // if (USE_UTIL_TIMER==true) {
            if (myUtilTimer!=null) {
                myUtilTimer.cancel();
            }//end : resetting timer
            
            myUtilTimer=new java.util.Timer(true);
            myUtilTimer.scheduleAtFixedRate(new MainTimerTask(), myCurrentTimerDelay, myCurrentTimerDelay);
      //  }//end : use java.util.Timer
      /*  else {
            
            if (mySwingTimer==null) mySwingTimer=new javax.swing.Timer(myCurrentTimerDelay, new MainTimerTask());
            mySwingTimer.start();
            
        }//end : use javax.swing.timer
       */
    }
    
    /**
     * Stop the "running" of the 2600 emulator...is called during pauses.
     */
    public void stopTimer() {
     //   if (USE_UTIL_TIMER==true) {
            if (myUtilTimer!=null) {
                myUtilTimer.cancel();
                myUtilTimer=null;
                
            }
      //  }//end : use java.util.Timer
  /*      else {
            if (mySwingTimer!=null) {
                mySwingTimer.stop();
            }//end : not null
        }//end : use javax.swing.Timer
   */
        myIntercessorClient.informUserOfPause(true);
    }
    
    
    /**
     * Destroys the console, which is necessary to do so when the program is complete,
     * because the destroy method frees up any system audio resources that have been
     * reserved.
     */
    public void destroy() {
        myConsole.destroy();
    }
    
    
    
    /**
     * Used to load a saved game.  The GUI class is responsible for opening
     * an input stream that contains the saved game, and this method will handle
     * the rest.
     * @param aInputStream Stream containing a previously saved game
     * @throws java.io.IOException 
     * @throws java.lang.ClassNotFoundException 
     */
    public void loadStateFromStream(InputStream aInputStream) throws IOException, ClassNotFoundException {
        try{
            //setPausedByPlayer(true);
            stopTimer();
            ObjectInputStream zOIS=new ObjectInputStream(aInputStream);
            Object zObj=zOIS.readObject();
            zOIS.close();
            
            if (zObj instanceof JSConsole) {
                JSConsole zNewConsole=(JSConsole)zObj;
                
                initConsole(zNewConsole);
                
                //myConsole.setTelevisionMode(TELEVISION_MODE_GAME);
                myConsole.doFrame();
                updatePause();
                refocusKeyboard();
                
                //myIntercessorClient.informUserOfPause(true);
                
                
            }//end : is a console
        }//end : try
        catch (JSException e) {
            e.printStackTrace();
        }
    }
    
    /**
     * Save the current "game" to the stream.  The GUI class is responsible
     * for creating a writable stream, and this method does the rest.
     * @param aOutputStream Stream that the saved game will be written to.
     * @throws java.io.IOException 
     */
    public void saveStateToStream(OutputStream aOutputStream) throws IOException {
        boolean zIsPaused=isPausedByPlayer();
        setPausedByPlayer(true);
        ObjectOutputStream zOOS=new ObjectOutputStream(aOutputStream);
        zOOS.writeObject(myConsole);
        zOOS.close();
        setPausedByPlayer(zIsPaused);
        
    }
    
    /**
     * This can be called by a GUI class if it doesn't wish to implement any special
     * behavior.  This method mostly displays a dialog indicating that there was error.
     * @param e Exception to communicate to user
     * @param aDialogParent Parent in which an error-message dialog can open under
     */
    public void showDefaultExceptionResponse(JSException e, Component aDialogParent) {
        
        System.out.println("" + e.getMessage());
        if (e.getExceptionType()==JSException.ExceptionType.INSTRUCTION_NOT_RECOGNIZED) {
            JOptionPane.showMessageDialog(aDialogParent, "There was an error running the ROM.", "JSTELLA ERROR", JOptionPane.ERROR_MESSAGE);
            
        } else {
            JOptionPane.showMessageDialog(aDialogParent, e.getJStellaMessage(), "JSTELLA ERROR", JOptionPane.ERROR_MESSAGE);
        }//end : other
    }
    
    
    /**
     * To receive keyboard input, the canvas needs to have the keyboard focus.
     * If the focus is lost, this method can attempt to transfer the keyboard focus
     * back to the canvas (or whatever else is appropriate) object
     */
    public void refocusKeyboard() {
        if ((myCanvas!=null) && (myCanvas.isVisible()==true)) 
        {
            myCanvas.requestFocus();
            myCanvas.requestFocusInWindow();
            
        }//end : visible
    }
    
    
    private void runMainLoop() {
        if (myConsole!=null) {
            try{
                myConsole.doFrame();
            }//end : try
            catch (JSException e) {
                stopTimer();
                if (myIntercessorClient!=null) myIntercessorClient.respondToException(e);
            }
            
        }//end : my console == false
    }
    
    private void updatePause() {
        if ((myPausedByFocusLoss==false)&&(myPausedByPlayer==false)) {
            startTimer();
        }//end : unpaused
        else {
            
            stopTimer();
            myConsole.pauseAudio();
            myConsole.grayCurrentFrame();
        }//end : is paused
    }
    
    /**
     * Since the pressing of reset is a two event process (switch down for a brief time,
     * and then switch back up), this method is needed to emulate both events with a 
     * single action.
     */
    public void emulateResetPress() {
        new SwitchToButtonAdapter(ConsoleSwitch.SWITCH_RESET);
    }
    
    /**
     * Since the pressing of select is a two event process (switch down for a brief time,
     * and then switch back up), this method is needed to emulate both events with a 
     * single action.
     */
    public void emulateSelectPress() {
        new SwitchToButtonAdapter(ConsoleSwitch.SWITCH_SELECT);
    }
    
    /**
     * When auto-pause mode is enabled, the emulator will pause whenever the appropriate object
     * (probably the canvas object) loses keyboard focus.
     * @param aEnable true to enable auto-pause mode
     */
    public void setAutoPauseMode(boolean aEnable) {
        myAutoPauseMode=aEnable;
        updatePause();
    }
    
    public boolean getAutoPauseMode()
    {
        return myAutoPauseMode;
    }
    
    /**
     * This "manually" pauses the emulator, as opposed to an automatic pause such as that
     * associated with loss of keyboard focus (see auto-pause mode methods).
     * @param aPause true to manually pause the emulator, false to unpause
     */
    public void setPausedByPlayer(boolean aPause) {
        myPausedByPlayer=aPause;
        updatePause();
    }
    
    /**
     * See setPausedByPlayer.
     * @return Returns true if the emulator is manually paused.
     */
    public boolean isPausedByPlayer() {
        return myPausedByPlayer;
    }
    
    /**
     * If paddle mode is locked, then pressing the right mouse button (or whatever else)
     * will not automatically result in the toggling of paddle mode on/off.  Thus,
     * if paddle mode is locked when paddle mode is off, paddle mode will remain off.
     * If locked when paddle mode is on, paddle mode will remain on.  This is useful for
     * applets that don't want the user accidentally exiting paddle mode, and not being
     * able to figure out what happened.
     */
    public void lockPaddleMode()
    {
       myInputMaster.setPaddleModeLock(false);
       myInputMaster.setPaddleMode(true);
       myInputMaster.setPaddleModeLock(true);
    }
    
    
    public void setControls(java.util.Map<String, String> aConfigMap) {
        myInputMaster.setControls(aConfigMap);
    }
    
    /**
     * This loads the ROM from the given stream, and starts its execution by the 
     * emulator.
     * 
     * This method will auto-detect the cartridge type (RECOMMENDED)
     * @param aROMStream the previously opened stream containing the ROM to open
     */
    public void playROM(java.io.InputStream aROMStream)  {   playROM(aROMStream, null, -1);   }    
    /**
     * This loads the ROM from the given stream, and starts its execution by the 
     * emulator.
     * 
     * This method allows the GUI to manually specify the cartridge type.  If the 
     * cartridge type specified is null, then the emulator will auto-detect the type.
     * @param aROMStream the previously opened stream containing the ROM to open
     * @param aCartridgeType manually specified cartridge type.
     */
    public void playROM(java.io.InputStream aROMStream, String aCartridgeType) { playROM(aROMStream, aCartridgeType, -1); }
    
    /**
     * This loads the ROM from the given stream, and starts its execution by the 
     * emulator.
     * 
     * This method allows the GUI to manually specify the cartridge type.  If the 
     * cartridge type specified is null, then the emulator will auto-detect the type.
     * 
     * This method also allows the GUI class to manually specify the display height.
     * If the display height specified is -1, the display height will be auto-detected.
     * @param aROMStream the previously opened stream containing the ROM to open
     * @param aCartridgeType manually specified cartridge type.
     * @param aDisplayHeight the display height for the ROM.  If -1, emulator will auto-detect height.
     */
    public void playROM(java.io.InputStream aROMStream, String aCartridgeType, int aDisplayHeight) {
        
        try{
            stopTimer();
            Cartridge zCart=JSConsole.createCartridge(aROMStream, aCartridgeType);
          
            if (zCart!=null) {
                myConsole.insertCartridge(zCart, aDisplayHeight);
                updateTimerDelay();
                
                myCanvas.refreshCanvas();
                startTimer();
            }//end : not null
        }//end : try
        catch (JSException e) {
            stopTimer();
            if (myIntercessorClient!=null) myIntercessorClient.respondToException(e);
        }
        
    }
    
    /**
     * Enables/disables the audio in the core emulator classes.  GUI classes should call
     * this method whenever the user changes the configuration to enable/disable the 
     * sound.
     * @param aEnabled true to turn sound on.
     */
    public void setSoundEnabled(boolean aEnabled) {  myConsole.setSoundEnabled(aEnabled);   }
    /**
     * Returns whether the sound is enabled (in the core emulator classes, as opposed to
     * the GUI menu).
     * @return true if the sound is enabled (in the core emulator objects)
     */
    public boolean isSoundEnabled() { return myConsole.isSoundEnabled(); }
    
    /**
     * Phosphor mode is essentially an anti-flicker mode.  When enabled,
     * the new color put on the screen is averaged with the old color.  
     * See JSConsole/JSVideo for details.
     * @param aEnable true to turn phoshpor mode on
     */
    public void setPhosphorEnabled(boolean aEnable)  { myConsole.setPhosphorEnabled(aEnable); }
    /**
     * Returns true if phosphor mode is enabled in the core emulator classes.
     * @return true if the emulator is currently using phosphor mode
     */
    public boolean isPhosphorEnabled() { return myConsole.isPhosphorEnabled(); }
    public void setStereoSound(boolean aEnable) {myConsole.setStereoSound(aEnable); }
    public boolean isStereoSound()  { return myConsole.isStereoSound(); }
    
    /**
     * Corresponds to the TV Type switch on the 2600 console.  This reports the variable
     * from the core emulator's point of view, not any GUI implementation.
     */
    public boolean isTVTypeBW() { return myConsole.isSwitchOn(ConsoleSwitch.SWITCH_BW); }
    public void setTVTypeBW(boolean aBW) { myConsole.flipSwitch(ConsoleSwitch.SWITCH_BW, aBW); }
    
    public boolean isPlayer0Amateur() { return myConsole.isSwitchOn(ConsoleSwitch.SWITCH_DIFFICULTY_P0);  }
    public void setPlayer0Amateur(boolean aAmateur) { myConsole.flipSwitch(ConsoleSwitch.SWITCH_DIFFICULTY_P0, aAmateur); }
    
    
    public boolean isPlayer1Amateur() { return myConsole.isSwitchOn(ConsoleSwitch.SWITCH_DIFFICULTY_P1); }
    public void setPlayer1Amateur(boolean aAmateur) { myConsole.flipSwitch(ConsoleSwitch.SWITCH_DIFFICULTY_P1, aAmateur); }

    /**
     * This method is called to inform the Intercessor client class (i.e. the GUI) that a console
     * switch has been flipped, and that the GUI may want to update the positions 
     * of any graphical representation of the switches.
     */
    public void switchFlipped() {
        if (myIntercessorClient!=null) myIntercessorClient.updateSwitches();
    }

    public JSConsole getConsole() {
        return myConsole;
    }

    public IfcCanvas getCanvas() {
        return myCanvas;
    }
    
    
    /**
     * Does the same thing as getCanvas(), but the returned object is already declared
     * as a JStellaCanvas, instead of the more generic IfcCanvas...thus, no casting
     * is necessary to run JStellaCanvas specific methods.
     * @return the current canvas
     */
    public JStellaCanvas getJStellaCanvas()
    {
        return myCanvas;
    }
    
    /**
     * Letter box mode essentially locks the screen into a fixed dimension ratio,
     * i.e. if the emulator canvas doesn't have the same width/height ratio as the 
     * emulator intends, black bars are used to make up the difference.
     * @param aEnable true to enable letter-box mode
     */
    public void setLetterBoxMode(boolean aEnable) { if (myCanvas!=null) myCanvas.setLetterBoxMode(aEnable); }
    /**
     * 
     * @return 
     */
    public boolean getLetterBoxMode() {  return ((myCanvas!=null) ? myCanvas.getLetterBoxMode() : false); }
  
    
    
    //==============================================
    
    private class MainTimerTask extends java.util.TimerTask implements ActionListener {
        /**
         * This is called by a java.util.Timer
         */
        public void run() {
            runMainLoop();
        }
        
        
        /**
         * This is called by a javax.swing.Timer
         * @param e
         */
        public void actionPerformed(ActionEvent e) {
            runMainLoop();
        }
        
    }//END INNER CLASS
    
    //========================================================
    /**
     * This is the Intercessor client interface that GUI classes should implement
     * to allow the Intercessor to fully communicate with it.
     */
    public interface IfcIntercessorClient  {
        /**
         * The GUI class should have this method add the specified canvas to wherever it
         * intends the emulator screen to be in the GUI.
         * e.g. the class may add the canvas to the content pane in a border layout, setting the 
         * canvas to CENTER.
         * @param aCanvas the canvas that the GUI class should add and display
         */
        public void displayCanvas(JPanel aCanvas);
        /**
         * This method is called whenever the intercessor receives an exception.
         * If the GUI class doesn't want to deal with this, it can simply have this method
         * call the Intercessor's showDefaultExceptionResponse method.
         * @param e the exception to deal with
         * @return return variable does nothing as of yet
         */
        public boolean respondToException(JSException e);
        /**
         * This method is called whenever the intercessor pauses the emulator, and it
         * allows the GUI to implement some sort of pause-notification scheme, so that the 
         * user doesn't think the emulator is locked-up.
         * @param aIsPaused true if paused, false if no longer paused
         */
        public void informUserOfPause(boolean aIsPaused);
        /**
         * This method is called whenever one of the console switches has been changed,
         * and it allows the GUI class to make any graphics/menu changes to reflect this.
         */
        public void updateSwitches();
        /**
         * This method should return the current user configuration object.
         * @return the configuration map
         */
        public java.util.Map<String, String> getConfiguration();
        
      //  public boolean isOkayToAutoPause();
        
    }
    
    
    //===============================
    
    /**
     * This converts a single-action (e.g. a button click) into the two actions needed
     * for certain switches (reset and select).
     */
    private class SwitchToButtonAdapter implements ActionListener {
        
        ConsoleSwitch mySwitchType=null;
        javax.swing.Timer mySwitchTimer=new javax.swing.Timer(50, this);
        
        public SwitchToButtonAdapter(ConsoleSwitch aSwitchType) {
            mySwitchType=aSwitchType;
            myConsole.flipSwitch(aSwitchType, true);
            
            mySwitchTimer.setRepeats(false);
            mySwitchTimer.start();
        }
        
        public void actionPerformed(ActionEvent e) {
            myConsole.flipSwitch(mySwitchType, false);
            mySwitchTimer.stop();
            mySwitchTimer=null;
            //mySTBA=null;
        }
    }
    
    
    //================================================================================
    
    private class IntercessorKeyboardFocusListener implements java.awt.event.FocusListener {
        public void focusLost(FocusEvent e) {
           // System.out.println("Debug : focus lost, new owner is " + e.getOppositeComponent());
            if (myAutoPauseMode==true)
            {
            //if (e.getOppositeComponent()==null) myPausedByFocusLoss=true;
           // else if (e.isTemporary()==false) myPausedByFocusLoss=true;
                if (!(e.getOppositeComponent() instanceof JButton))  myPausedByFocusLoss=true;
            }//end : autopause is on
             updatePause();
            
        }
        
        public void focusGained(FocusEvent e) {
            
            myPausedByFocusLoss=false;
            updatePause();
            
        }
        
    }
    
    
    //=================================================
    
    private class IntercessorInputVerifier extends javax.swing.InputVerifier
    {
        public boolean verify(JComponent jComponent) {
            return false;
        }
        
    }
    
    
}
