/*
 * JSVideo.java
 *
 * Created on July 16, 2007, 10:22 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package jstella.core;


import java.awt.Font;
import java.awt.*;
import java.io.*;
import java.awt.image.*;
import java.awt.geom.*;
import java.awt.font.*;
import java.net.URL;
import javax.swing.*;

import static jstella.core.JSConstants.*;


/**
 * The class that takes care of drawing to the user's computer screen.
 * 
 * <p>
 *     It essentially works this way: <br>
 *     The myCurrentFrameBuffer array (and the myPreviousFrameBuffer one) represent
 *     the pixels of the TV display, with the top left being the first, and increasing
 *     to the right, eventually continuing on the next line down, on the left side of the 
 *     screen. The values of this array represent INDICES (of the palette array) of the 
 *     colors pertaining to that pixel.  It is the TIA's job to set the values of this
 *     array--it does so when JSConsole's doFrame() calls the TIA's processFrame() method.
 *     When the JSConsole calls the doVideo() method, this data should already be updated.
 *     So JSVideo first takes the values in the FrameBuffer array and uses them to 
 *     set the pixels on the back buffer image to the corresponding color.  It then has
 *     the back buffer painted onto the GUI's canvas.
 *     
 *     All of the GUI related scaling is done in the canvas class of the GUI.
 *     
 *     
 *     
 *     
 * </p>
 * @author Bradford W. Mott and the Stella team (original)
 * J.L. Allen (Java translation)
 */
public class JSVideo implements java.io.Serializable{
    private final static long serialVersionUID = 701607876730703063L;
    
    
    public final static int DEFAULT_WIDTH=160;
    public final static int DEFAULT_HEIGHT=200; //not sure if this is still used
    
    public final static int DEFAULT_PHOSPHOR_BLEND=77; 
    
    private static java.util.Random myRandomGenerator=new java.util.Random();
    
    
    
    private final static int[] PALETTE_GRAY_STANDARD;
    
    static {
        PALETTE_GRAY_STANDARD=new int[256];
        for (int i=0; i<256; i++) {
            PALETTE_GRAY_STANDARD[i]=new Color(i, i, i).getRGB();
        }//end : for i loop
        
    }//end : static
    
    private JSConsole myConsole=null;
    
    // TIA palettes for normal and phosphor modes
    private transient int[] myNormalPalette=new int[256];
    private transient int[][] myBlendedPalette=new int[256][256];
    private transient int[] myGrayPalette=new int[256];
    
    private transient int[] myCurrentFrameBuffer=null;
    private transient int[] myPreviousFrameBuffer=null;
    
    
    private transient int[] myResidualColorBuffer=null;
    
    private boolean myGrayPaletteMode=false;
    
    
    private boolean myRedrawTIAIndicator=true;   // Indicates if the TIA area should be redrawn
    private boolean myUsePhosphor=false;  // Use phosphor effect (aka no flicker on 30Hz screens)
    private int myPhosphorBlendPercent=DEFAULT_PHOSPHOR_BLEND;   // Amount to blend when using phosphor effect
    
    private transient ClipRectangle myClipRect=new ClipRectangle();
    private transient BufferedImage myBackBuffer=null;//(DEFAULT_WIDTH, DEFAULT_HEIGHT); //new BufferedImage(DEFAULT_WIDTH, DEFAULT_HEIGHT, BufferedImage.TYPE_INT_ARGB);
    private transient int[] myBackBufferData=null; //new byte[0];
    
    
    private transient ImageIcon myTestPattern=null;
    
    
    
    
    /**
     * Creates a new instance of JSVideo
     * @param aConsole the parent console
     */
    protected JSVideo(JSConsole aConsole) {
        myConsole=aConsole;
        myRedrawTIAIndicator=true;
        myUsePhosphor=false;
        myPhosphorBlendPercent=DEFAULT_PHOSPHOR_BLEND;
       
        // Allocate buffers for two frame buffers
        myCurrentFrameBuffer = new int[CLOCKS_PER_LINE_VISIBLE * FRAME_Y_MAX];
        myPreviousFrameBuffer = new int[CLOCKS_PER_LINE_VISIBLE * FRAME_Y_MAX];
        initBackBuffer(DEFAULT_WIDTH, DEFAULT_HEIGHT);
        initPalettes();
        loadImages();
        initialize();
    }
    
    /**
     * This method is a special method used by Java's serialization mechanism.  It is called
     * when a JSVideo object is deserialized (e.g. during the loading of a saved game), and
     * acts as an "alternate constructor", rebuilding any object that wasn't able to be
     * serialized/deserialized.
     * @param in the object input stream
     * @throws java.io.IOException input/output exception
     * @throws java.lang.ClassNotFoundException called if it can't find the class.
     */
    private void readObject(java.io.ObjectInputStream in)  throws IOException, ClassNotFoundException {
        in.defaultReadObject();
        if (myClipRect==null) myClipRect=new ClipRectangle();
      //  if (myBackBuffer==null) myBackBuffer=createBackBuffer(getWidth(), getHeight()); //new BufferedImage(getWidth(), getHeight(), BufferedImage.TYPE_INT_ARGB);
        if (myCurrentFrameBuffer==null)  myCurrentFrameBuffer = new int[CLOCKS_PER_LINE_VISIBLE * FRAME_Y_MAX];
        if (myPreviousFrameBuffer==null) myPreviousFrameBuffer = new int[CLOCKS_PER_LINE_VISIBLE * FRAME_Y_MAX];
        initBackBuffer(getWidth(), getHeight());
        initPalettes();
        loadImages();
        initialize();
        refresh();
        
    }
    
    
    /**
     * This is a special method that is called by Java's serialization mechanism.
     * See readObject(...)
     * @param out object output stream
     * @throws java.io.IOException thrown if an i/o exception occurs
     */
    private void writeObject(ObjectOutputStream out) throws IOException {
        out.defaultWriteObject();
    }
    
    /**
     * This method is called for the creation of the back buffer.  It is called when 
     * a new JSVideo object is created, as well as when a JSVideo object is
     * deserialized--that is, loaded from a stream (when a saved game state is loaded).
     * @param aWidth desired width of the back buffer
     * @param aHeight desired height of the back buffer
     * @return the new back buffer
     */
    private static BufferedImage createBackBuffer(int aWidth, int aHeight)
    {
       // BufferedImage zReturn=new BufferedImage(aWidth, aHeight, BufferedImage.TYPE_INT_ARGB);
        BufferedImage zReturn=GraphicsEnvironment.getLocalGraphicsEnvironment().getDefaultScreenDevice().getDefaultConfiguration().createCompatibleImage(aWidth, aHeight);
        
        
        //System.out.println("debug: image=" + zReturn);
        return zReturn;
        
    }
    
    private void initBackBuffer(int aWidth, int aHeight)
    {
        myBackBuffer=createBackBuffer(aWidth, aHeight);
        switch(myBackBuffer.getType())
        {
            case BufferedImage.TYPE_INT_ARGB :
            case BufferedImage.TYPE_INT_ARGB_PRE :
            case BufferedImage.TYPE_INT_RGB :
          
       
            myBackBufferData=((DataBufferInt)myBackBuffer.getRaster().getDataBuffer()).getData();
            break;
     
            
            default :
        {
            System.out.println("Backbuffer is not integer RGB type");
            myBackBufferData=null;
            break;
        }//end : not correct type for optimized buffer handling
    }
    }//::
    
    private void initPalettes()
    {
        myNormalPalette=new int[256];
        myBlendedPalette=new int[256][256];
        myGrayPalette=new int[256];
    }
    
    /**
     * Clears the buffers.
     */
    protected void clearBuffers() {
        for(int i = 0; i < myCurrentFrameBuffer.length; ++i) {
            myCurrentFrameBuffer[i] = 0;
            myPreviousFrameBuffer[i] = 0;
        }
        
    }
    
    /**
     * This is called once a frame to swap the previous and the current frame buffers.
     * The previous becomes the current, and the former current becomes the previous.
     */
    protected void swapFrameBuffers() {
        int[] tmp = myCurrentFrameBuffer;
        myCurrentFrameBuffer = myPreviousFrameBuffer;
        myPreviousFrameBuffer = tmp;
    }
    
    /**
     * Returns the current frame buffer.
     * <p>
     *    The current frame buffer represents all the pixels on the display screen (with
     *    each integer representing the index of a color in the current palette).
     * </p>
     * @return the current frame buffer
     */
    protected int[] getCurrentFrameBuffer() { return myCurrentFrameBuffer; }
    /**
     * Returns the previous frame buffer.
     * There are two frame buffers, a current and a previous, and they are switched every
     * frame.  This way, JSVideo can keep track of which pixels have changed, so that
     * it only has to redraw things that have changed.
     * @return previous frame buffer
     */
    protected int[] getPreviousFrameBuffer() { return myPreviousFrameBuffer; }
    
    /**
     * Calls the console's getWidth() method.
     * @return width of the display (as far as TIA is concerned)
     */
    protected int getWidth() { return myConsole.getDisplayWidth();}
    /**
     * Calls the console's getHeight() method.
     * @return height of the display (as far as TIA is concerned)
     */
    protected int getHeight() { return myConsole.getDisplayHeight(); }
    
    
    
    
    /**
     * Is used to paint the current frame in shades of gray...is used when paused.
     * This method first changes the palette to grayscale, then repaints the 
     * current frame with that palette, and then changes the palette back to what
     * it was previously.  Thus, this is only good for one frame, so this is best
     * used when the game is paused.
     */
    protected void grayCurrentFrame() {
        boolean zOldMode=myGrayPaletteMode;
        myGrayPaletteMode=true;
        updateVideoFrame();
        myGrayPaletteMode=zOldMode;
        
    }
    
    /**
     * Repaints the current frame.
     * This is similar to refresh(), but it also redraws the current frame, whereas refresh waits 
     * until it's time to redraw the frame again.
     */
    protected void updateVideoFrame() {
        
        refresh();
        switch (myConsole.getTelevisionMode())
        {
            case TELEVISION_MODE_GAME : doFrameVideo(); break;
            case TELEVISION_MODE_SNOW : doSnow(); break;
            case TELEVISION_MODE_TEST_PATTERN : doTestPattern(); break;
        }//end : switch
        
    }
    
    
    /**
     * Returns the back buffer object
     * @return the back buffer
     */
    protected BufferedImage getBackBuffer() {
        return myBackBuffer;
    }
    
    /**
     * Erases any images on the back buffer
     */
    protected  void clearBackBuffer() {
        Graphics2D z2D=myBackBuffer.createGraphics();
        z2D.setColor(Color.BLACK);
        z2D.fillRect(0,0, myBackBuffer.getWidth(), myBackBuffer.getHeight());
        z2D.dispose();
    }
    
    /**
     * Ensures the back buffer is big enough to contain the current display.
     * The back buffer is a buffered image object that the JSVideo draws to, and which is
     * subsequently drawn to the screen by the canvas object.  The back buffer should be the
     * size of the display, so when the display changes size, this method should be called.
     * @param aNewWidth new display width
     * @param aNewHeight new display height
     */
    protected void adjustBackBuffer(int aNewWidth, int aNewHeight) {
        if ((aNewWidth>myBackBuffer.getWidth())||(aNewHeight > myBackBuffer.getHeight())) {
         //   myBackBuffer=createBackBuffer(
             initBackBuffer(Math.max(myBackBuffer.getWidth(), aNewWidth), Math.max(myBackBuffer.getHeight(), aNewHeight));//new BufferedImage(Math.max(myBackBuffer.getWidth(), aNewWidth), Math.max(myBackBuffer.getHeight(), aNewHeight), BufferedImage.TYPE_INT_ARGB);
        }//end : needs to be larger
    }
    
    
    
    
    
    
    /**
     * Prepares the JSVideo for use.
     */
    protected void initialize() {
        setTIAPalette(PALETTE_NTSC);
    }
    
    /**
     * This method signals to the JSVideo that the whole display screen needs to be 
     * updated.  Normally, for performance reasons, the JSVideo object will only update
     * those regions of the display that, by its calculations, have changed.  This method 
     * tells JSVideo to ignore its calculations during the next frame, and redraw the whole
     * thing.
     * It does not cause the frame to be redrawn...it merely causes the ENTIRE frame to be
     * redrawn the next time a redraw occurs.
     */
    protected void refresh() {
        myRedrawTIAIndicator = true;
    }
    
    /**
     * This method loads any images needed by the JSVideo object (e.g. the television 
     * test pattern image).  It is called when JSVideo object is created.
     */
    private void loadImages() {
        
        URL zTestPatternURL=this.getClass().getResource(RESOURCE_IMAGE_TEST_PATTERN);
        if (zTestPatternURL!=null) myTestPattern=new ImageIcon(zTestPatternURL);
        
        
    }
    
    /**
     * Draws static ("snow") on the back buffer, and paints the back buffer to the canvas.
     */
    protected void doSnow() {
        if (myBackBuffer!=null) {
            snowBackBuffer();
            if (getCanvas()!=null) getCanvas().paintCanvas(myBackBuffer, myBackBuffer.getWidth(), myBackBuffer.getHeight());
        }//end : not null
    }
    
    /**
     * This sets out random grayscale pixels on the backbuffer to simulate television static
     */
    private void snowBackBuffer() {
        if (myBackBuffer!=null) {
            int zWidth=myBackBuffer.getWidth();
            int zHeight=myBackBuffer.getHeight();
            for (int iY=0; iY<zHeight; iY++) {
                for (int iX=0; iX<zWidth; iX++) {
                    int zRandomValue=Math.min((int)(myRandomGenerator.nextDouble() * 256.0),255);
                    
                    myBackBuffer.setRGB(iX, iY, PALETTE_GRAY_STANDARD[zRandomValue]);
                }//end : for iX
                
                
            }//end : for iY
        }//end : back buffer not null
    }
    
    /**
     * This method is a convenience method which fetches the Canvas object from the 
     * Console's client (i.e. the GUI).
     * @return the current canvas
     */
    private IfcCanvas getCanvas() {
        if (myConsole.getConsoleClient()!=null) return myConsole.getConsoleClient().getCanvas();
        else return null;
    }
    
    
    
    /**
     * This method is one of the most important of the program.  It draws the pixels
     * on the back buffer and then tells the component that display the back buffer to
     * repaint itself immediately.
     */
    protected void doFrameVideo() {
       
       // long zTimeA=System.nanoTime();
        prepareBackBuffer();
      //  long zTimeB=System.nanoTime();
     
        paintBackBufferToCanvas();
     //    long zTimeC=System.nanoTime();
   /*        if (JSConsole.DEBUG_MODE_ON==true)
        {
            //int zDeltaBA=(int)(zTimeB - zTimeA) / 1000;
           // System.out.println("debug JSVideo : prepareBackBuffer=" + zDeltaBA + " microseconds");
             int zDeltaCB=(int)(zTimeC - zTimeB) / 1000;
            System.out.println("debug JSVideo : paintBackBufferToCanvas=" + zDeltaCB + " microseconds");
        }//end : debug mode on
    */
       
        
    }
    
    /**
     * This method takes data from the TIA object and uses it to draw the back buffer.
     */
    private void prepareBackBuffer() {
      
            int[] zCurrentBuffer=getCurrentFrameBuffer();
            int[] zPrevBuffer=getPreviousFrameBuffer();
            if (myResidualColorBuffer==null) myResidualColorBuffer=new int[zCurrentBuffer.length]; //maybe a better way to set it
            int zWidth  = Math.min(getWidth(), myBackBuffer.getWidth());
            int zHeight = Math.min(getHeight(), myBackBuffer.getHeight());
            
            int zBufferIndexAtLineStart = 0;
            
            for(int y = 0; y < zHeight; y++) {         //for each line
                
                for(int x = 0; x < zWidth; x++) {    //for each pixel on a given line
                    int zBufferIndex = zBufferIndexAtLineStart + x;  //determing the buffer index at this given x and y
                    
                    int zNewColorIndex = zCurrentBuffer[zBufferIndex];
                    int zOldColorIndex = zPrevBuffer[zBufferIndex];
                    
                    //TODO : make the following code more "elegant", and self-explanatory
                    int zOldPaintedColor= myResidualColorBuffer[zBufferIndex];
                    int zNewPaintedColor= myUsePhosphor ? getBlendedColorInt(zOldColorIndex, zNewColorIndex) : getColorInt(zNewColorIndex);
                    
                    if((zNewPaintedColor != zOldPaintedColor) || (myRedrawTIAIndicator) ) {   // either the color has changed, or we have been ordered to draw it regardless
                        myClipRect.addPoint(x,y);                   // expands the clip rectangle, telling it there is another part of the screen in need of update
                        
                        myResidualColorBuffer[zBufferIndex]=zNewPaintedColor;
                        
                        
                        
                        if (myBackBufferData!=null) myBackBufferData[zBufferIndex]=zNewPaintedColor; //a quicker way if available
                        else myBackBuffer.setRGB(x, y, zNewPaintedColor);     // the actual act of drawing
                        
                    }//end : pixel has changed
                }//end : for x to width loop
                
                zBufferIndexAtLineStart += zWidth;  //moving to next line
            }//end : for y to height loop
            myRedrawTIAIndicator=false;
       
    }//::
    
    
    
    
    
    
    
    /**
     * This method paints the back buffer to the previously specified canvas
     */
    private void paintBackBufferToCanvas() {
        
        if (getCanvas()!=null) {
            //Tells the canvas to call the paint command...the coordinates are very important...drawing the screen is incredibly slow, so you must only
            //draw a portion of it at a time.  The portion that has changed is contained in myClipRect
            //The calculations are there to scale, converting double into ints by rounding the correct direction
            
            getCanvas().paintCanvas(myBackBuffer, getWidth(), getHeight(), myClipRect);
            
            
            myClipRect.resetRect();
        }//end : canvas not null
        
    }
    
    /**
     * The "test pattern" is the image of colored bars that may be display when 
     * no ROM is loaded.  This method draws it to the canvas.
     */
    protected void doTestPattern()
    {
         Graphics2D z2D=myBackBuffer.createGraphics();
            double zScaleX=(double)myBackBuffer.getWidth() / myTestPattern.getIconWidth();
            double zScaleY=(double)myBackBuffer.getHeight() / myTestPattern.getIconHeight();
            double zScale=Math.min(zScaleX, zScaleY);
            int zWidth  =(int)(myTestPattern.getIconWidth() * zScale);//Math.min(getWidth(), myBackBuffer.getWidth());
            int zHeight = (int)(myTestPattern.getIconHeight() * zScale); //Math.min(getHeight(), myBackBuffer.getHeight());
            z2D.drawImage(myTestPattern.getImage(), 0,0, zWidth, zHeight, 0,0, myTestPattern.getIconWidth(), myTestPattern.getIconHeight(), null);
            
            z2D.dispose();
            
            if (getCanvas()!=null) {
            //Tells the canvas to call the paint command...the coordinates are very important...drawing the screen is incredibly slow, so you must only
            //draw a portion of it at a time.  The portion that has changed is contained in myClipRect
            //The calculations are there to scale, converting double into ints by rounding the correct direction
            myClipRect.resetRect();
            getCanvas().paintCanvas(myBackBuffer, zWidth, zHeight, myClipRect);
            
            
          
        }//end : canvas not null
            
    }
    
    // ======================= COLOR STUFF ==================================
    
    
    
    
    
    /**
     * Returns true if phosphor mode is enabled
     * @return true if phosphor mode is enabled
     */
    protected boolean getPhosphorEnabled() {
        return myUsePhosphor;
    }
    
    
    
    /**
     * Phosphor mode helps emulate the television a little better, and can be used as
     * an "anti-flicker" mode.  When the "pixels" on a traditional TV screen are illuminated,
     * they don't immediately fade when the next frame (and thus pixel) is set...instead,
     * there is somewhat of a blend of the old and the new.  Think "phosphorescence".
     * <p>
     *    Phosphor mode emulates this blend, and is useful for combatting flicker.
     *    Imagine a white background, and for every other frame, a black square is shown
     *    on that background.  Now imagine that there are 60 frames per second.
     *    Without phosphor mode, you are likely to see a flickering of a black square.
     *    With phosphor mode, you will likely see a steady gray square--it is a blend
     *    of white and black.
     *    
     * </p>
     * @param aEnable true to turn on phosphor mode
     */
    protected void setPhosphorEnabled(boolean aEnable) {
        myUsePhosphor=aEnable;
    }
    
   
    /**
     * Turns phosphor mode on/off, but also specifies a blend percentage value...(e.g. 77)
     * See the other setPhosphorEnabled(...) documentation.
     * @param aEnable true to enable
     * @param aBlendPercent percentage that the two colors should be blended
     */
    protected void setPhosphorEnabled(boolean aEnable, int aBlendPercent) {
        setPhosphorEnabled(aEnable);
        myPhosphorBlendPercent=aBlendPercent;
        
    }
    
    
    /**
     * Returns the java color associated with the given array index
     * @param aIndex The color's index
     * @return the associated java color
     */
    //private Color getColor(int aIndex) {
    //     return new Color(myNormalPalette[aIndex]);
    // }
    
    /**
     * Retrieves the color integer from a certain array.
     * The integer stores color information in the ARGB format (I think)
     * @param aIndex The array index of the desired color
     * @return An integer representing the desired color
     */
    private int getColorInt(int aIndex) {
        if (myGrayPaletteMode==true) return myGrayPalette[aIndex & 0xFF];
        else return myNormalPalette[aIndex & 0xFF];
    }
    
    /**
     * This is used in phosphor (aka anti-flicker) mode to get a blend
     * of the colors represented by the two indices
     * @param aOldIndex the old color index
     * @param aNewIndex the new color index
     * @return the color that represents the blend between the two indices
     */
    private int getBlendedColorInt(int aOldIndex, int aNewIndex) {
        return myBlendedPalette[aOldIndex & 0xFF][aNewIndex & 0xFF];
    }
    
    
    
    
    /**
     * Sets the palette to be used.
     * The palettes are integer arrays of 256 values, in xRGB format, where x isn't used.
     * Thus, the lowest 8 bits of each value represent the blue value (0-255), the next 8 represent the green
     * value, and the next 8 represent the red value.
     * @param palette the palette to use
     */
    protected void setTIAPalette(int[] palette) {
        
        int i, j;
        
        // Set palette for normal fill
        for(i = 0; i < 256; ++i) {
            int r = (palette[i] >> 16) & 0xff;
            int g = (palette[i] >> 8) & 0xff;
            int b = palette[i] & 0xff;
            
            
            myNormalPalette[i] = calculateNormalColor(r, g, b);
            myGrayPalette[i] = calculateGrayColor(r, g, b);
        }
        
        // Set palette for phosphor effect
        for(i = 0; i < 256; ++i) {
            for(j = 0; j < 256; ++j) {
                int ri = (palette[i] >> 16) & 0xff;
                int gi = (palette[i] >> 8) & 0xff;
                int bi = palette[i] & 0xff;
                int rj = (palette[j] >> 16) & 0xff;
                int gj = (palette[j] >> 8) & 0xff;
                int bj = palette[j] & 0xff;
                
                int r = calculatePhosphorColor(ri, rj, myPhosphorBlendPercent);
                int g =  calculatePhosphorColor(gi, gj, myPhosphorBlendPercent);
                int b = calculatePhosphorColor(bi, bj, myPhosphorBlendPercent);
                
                myBlendedPalette[i][j] = calculateNormalColor(r, g, b);
            }
        }
        
        
        
        myRedrawTIAIndicator = true;
    }
    
    
   /*    protected  void setColorLossPalette(boolean loss) {
           //TODO : document what this color loss thing is (I don't have a clue - JLA)
        // Look at all the palettes, since we don't know which one is
        // currently active
        int[][] palette= {
            ourNTSCPalette,    ourPALPalette,
            ourNTSCPalette11,  ourPALPalette11,
            ourNTSCPaletteZ26, ourPALPaletteZ26,
            null, null //ourUserNTSCPalette, ourUserPALPalette
        };
       // if(myUserPaletteDefined) {
         //   palette[6] = ourUserNTSCPalette;
         //   palette[7] = ourUserPALPalette;
      // }
    
        for(int i = 0; i < 8; ++i) {
            if(palette[i] == null)
                continue;
    
            // If color-loss is enabled, fill the odd numbered palette entries
            // with gray values (calculated using the standard RGB . grayscale
            // conversion formula)
            for(int j = 0; j < 128; ++j) {
                int[] zPixelArray=palette[i];
                int pixel = zPixelArray[(j<<1)];
                if(loss) {
                    int r = (int)(pixel >> 16) & 0xff;
                    int g = (int)(pixel >> 8)  & 0xff;
                    int b = (int)(pixel >> 0)  & 0xff;
                    int sum = (int) (((float)r * 0.2989) +
                            ((float)g * 0.5870) +
                            ((float)b * 0.1140));
                    pixel = (sum << 16) + (sum << 8) + sum;
                }
                palette[i][(j<<1)+1] = pixel;
            }//end : for int j
        }//for int i (palette)
    }
    */
    
    
    
    
    
    
    
    
    
    /**
     * Returns an integer version of a color based on its components.
     * @param r red component
     * @param g green component
     * @param b blue component
     * @return an integer version of the specified color
     */
    private static int calculateNormalColor(int r, int g, int b) {
        assert((r>=0)&&(g>=0)&&(b>=0));
        return new Color(r,g,b).getRGB();
    }
    
    
    /**
     * Calculates a given phosphor blend based on the colors provided.  This is used 
     * when the palette is being set.
     * @param aColorComponentA the first component e.g. the red component (0-255) of the previous pixel
     * @param aColorComponentB the second component e.g. the red component (0-255) of the new pixel
     * @param aPhosphorBlend percentage to blend (e.g. 77)
     * @return new component value
     */
    private static int calculatePhosphorColor(int aColorComponentA, int aColorComponentB, int aPhosphorBlend) {
        
        int zDifference=Math.abs(aColorComponentA - aColorComponentB);
        double zBlendFactor=(double)aPhosphorBlend/100.0;
        
        int zPhosp=Math.min(aColorComponentA, aColorComponentB) + (int)(zBlendFactor * zDifference);
        
        assert((zPhosp>=0)&&(zPhosp<0x100)); //i.e. between 0 and 256 (a byte value)
        return zPhosp;
    }
    
    /**
     * Calculates a gray color based on the given components
     * @param aRed red component (0-255)
     * @param aGreen green component (0-255)
     * @param aBlue blue component (0-255)
     * @return the new gray color in integer RGB format
     */
    private static int calculateGrayColor(int aRed, int aGreen, int aBlue) {
        int zAverage=(aRed + aGreen + aBlue) / 3;
        return calculateNormalColor(zAverage, zAverage, zAverage);
    }
    
    
    
    
    
 
    
    
  
    
    
    
    
    
    
    
    
//=============================================================================
//========================== INNER CLASSES ====================================
//=============================================================================
    
    
    //
    
    
    //==================================================
    /**
     * A rectangle that remembers what part of the screen has been changed.
     * Everytime the drawMediaSource() method encounters a changed pixel,
     * it informs an object of this class, which resizes to encompass the new
     * point.
     */
    private class ClipRectangle extends java.awt.Rectangle {
        private boolean isClear=true;
        
        
        /**
         * Resets the area of the rectangle.  Should be called every frame.
         */
        public void resetRect() {
            isClear=true;
            this.x=0;
            this.y=0;
            this.width=0;
            this.height=0;
        }
        
        
        
        /**
         * Tells the rectangle to expand to encompass the given point.
         * @param aX X
         * @param aY Y
         */
        public void addPoint(int aX, int aY) {
            if (isClear==true) //first point
            {
                this.x=aX - 1;
                this.y=aY - 1;
                this.width=3;
                this.height=3;
                isClear=false;
            }//end : first point
            else {
                if (aX >= (x+width)) //to the right of rect
                {
                    width=(aX - x)+ 2;
                } else if (aX <= x) //to the left of x
                {
                    width += (x - aX) + 2; //expand width
                    x=aX - 1;
                }
                
                if (aY >= (y+height)) //below rect
                {
                    height=(aY - y) + 2;
                } else if (aY <= y) //above y
                {
                    height += (y - aY) + 2; //expand height
                    y=aY - 1;
                }
                
            }//end : additional points
            
            
        }//::
        
        
    }//INNER CLASS END
    
    
    
    
    
    
    
    
//*****************************************************************************
//************************** DEBUG SECTION ************************************
//*****************************************************************************
    
    
 /*
      public void debugDrawMediaSource() {
        doFrameVideo();
    }
  
    public void debugPaintImmediately() {
        IfcCanvas zCanvas=getCanvas();
       // if (zCanvas!=null) zCanvas.paintCanvas(0,0,zCanvas.getCanvasWidth(), zCanvas.getCanvasHeight());
    }
  */
    
    
    
}//CLASS END
